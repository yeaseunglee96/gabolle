package com.gabolle.backend.menuscan;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.adapter.LocalMenuReader;
import com.gabolle.backend.menuscan.application.MenuScanRateLimiter;
import com.gabolle.backend.menuscan.domain.MenuScanUsage;
import com.gabolle.backend.menuscan.repository.MenuScanUsageRepository;
import com.gabolle.backend.menuscan.application.MenuScanService;
import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.presentation.MenuScanController;
import com.gabolle.backend.menuscan.presentation.MenuScanExceptionHandler;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메뉴판 읽기. 이 검사들이 지키는 것은 기능이 아니라 안전이다 — 모델이 못 읽은 것을 화면이
 * «없음»으로 그리면 사람이 다친다.
 */
class MenuScanControllerTest {

	private GmsMenuReader reader;
	private MenuScanProperties properties;
	private MockMvc mockMvc;

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.reader = mock(GmsMenuReader.class);
		this.properties = new MenuScanProperties();
		this.properties.setApiKey("test-key");

		// 한도 집계 표를 메모리로 흉내 내 «같은 사람이 창 안에서 몇 번 불렀나»만 센다 —
		// 이 시험이 보는 것은 한도가 걸렸을 때 429 가 나가는가다.
		MenuScanUsageRepository usage = inMemoryUsage();
		MenuScanRateLimiter limiter = new MenuScanRateLimiter(this.properties, usage,
				Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));
		// 우리 모델(menu-ocr)은 «설정 없음»으로 둔다 — 이 시험들은 GMS 경로의 안전을 본다. 우리 모델
		// 경로와 대체는 MenuScanServiceFallbackTest 가 본다
		LocalMenuReader local = mock(LocalMenuReader.class);
		MenuScanService service = new MenuScanService(this.reader, local, limiter, this.properties);

		this.mockMvc = MockMvcBuilders.standaloneSetup(new MenuScanController(service))
				.setControllerAdvice(new MenuScanExceptionHandler())
				.build();

		when(this.reader.isConfigured()).thenReturn(true);
	}

	/** 표 대신 목록 하나에 적는 가짜. 세는 규칙만 진짜와 같게 둔다. */
	private static MenuScanUsageRepository inMemoryUsage() {
		List<MenuScanUsage> rows = new ArrayList<>();
		MenuScanUsageRepository usage = mock(MenuScanUsageRepository.class);

		when(usage.save(any(MenuScanUsage.class))).thenAnswer((call) -> {
			MenuScanUsage row = call.getArgument(0);
			rows.add(row);
			return row;
		});
		when(usage.countByUserIdAndScannedAtAfter(any(), any())).thenAnswer((call) -> {
			UUID userId = call.getArgument(0);
			OffsetDateTime since = call.getArgument(1);
			return rows.stream()
					.filter((row) -> row.getUserId().equals(userId) && row.getScannedAt().isAfter(since))
					.count();
		});
		when(usage.deleteExpiredFor(any(), any())).thenReturn(0);
		return usage;
	}

	private static Authentication principal(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	/** 진짜 JPEG 를 만든다 — 가짜 바이트로는 위치정보 제거가 도는지 잴 수 없다. */
	private static byte[] jpeg() throws Exception {
		BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
		var g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 40, 20);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	private MockMultipartFile part(byte[] bytes) {
		return new MockMultipartFile("image", "menu.jpg", "image/jpeg", bytes);
	}

	// ── 줄을 이름과 가격으로 나눈다 ────────────────────────────────────────

	@Test
	@DisplayName("음식 줄은 이름과 가격이 따로 나간다")
	void foodLineCarriesNameAndPrice() throws Exception {
		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(
				List.of(new MenuScanResponse.Line("돼지국밥 9,000원", "돼지국밥", "9,000원", "Pork and rice soup",
						"Pork and rice soup 9,000 won", List.of("돼지고기"))), 0));

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(jpeg()))
						.principal(principal(this.userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.lines[0].name").value("돼지국밥"))
				.andExpect(jsonPath("$.data.lines[0].price").value("9,000원"))
				// 번역된 이름이 따로 나간다. 없으면 화면이 가격을 세 번 그린다 —
				// 원문 줄에 한 번, 번역된 줄에 한 번, 가격 칸에 한 번.
				.andExpect(jsonPath("$.data.lines[0].translatedName").value("Pork and rice soup"))
				.andExpect(jsonPath("$.data.lines[0].translatedText").value("Pork and rice soup 9,000 won"))
				// 원문 줄은 그대로 남는다 — 옛 앱 빌드가 이것만 읽는다
				.andExpect(jsonPath("$.data.lines[0].text").value("돼지국밥 9,000원"));
	}

	/**
	 * 가격을 숫자로 바꾸면 화면이 통화를 자기가 붙여야 하고, 그 순간 우리가 바꾼 값이 맞다고
	 * 주장하는 것이 된다. 적힌 그대로 넘기면 틀릴 자리가 없다.
	 */
	@Test
	@DisplayName("🔴 가격은 적힌 그대로 나간다 — 숫자로 바꾸지 않는다")
	void priceIsNotNormalised() throws Exception {
		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(
				List.of(new MenuScanResponse.Line("밀면 8,500원", "밀면", "8,500원", "Wheat noodles",
						"밀면 8,500원", List.of())), 0));

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(jpeg()))
						.principal(principal(this.userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.lines[0].price").value("8,500원"));
	}

	/**
	 * 가게 이름과 안내문은 음식이 아니다. 여기서 {@code name} 을 채우면 화면이 그것을 메뉴로 그리고
	 * 그림까지 만든다.
	 */
	@Test
	@DisplayName("🔴 음식이 아닌 줄은 이름과 가격이 빈 문자열이다 — null 이 아니다")
	void nonFoodLineHasEmptyNameAndPrice() throws Exception {
		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(
				List.of(new MenuScanResponse.Line("※ 모든 메뉴에 공깃밥이 포함됩니다", "", "", "",
						"※ All menus include a bowl of rice", List.of())), 0));

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(jpeg()))
						.principal(principal(this.userId)))
				.andExpect(status().isOk())
				// 칸이 사라지면 화면은 «아직 안 왔나»와 «음식이 아니다»를 못 가른다
				.andExpect(jsonPath("$.data.lines[0].name").value(""))
				.andExpect(jsonPath("$.data.lines[0].price").value(""));
	}

	// ── 「없다」를 말할 수 없다 ──────────────────────────────────────────────

	/** 응답 모양에 «안전하다»를 담을 칸이 있으면 언젠가 누군가 그린다. */
	@Test
	@DisplayName("🔴 응답에 「안전·없음」을 담을 칸이 아예 없다")
	void responseHasNoSafetyClaimField() {
		List<String> names = Arrays.stream(MenuScanResponse.class.getRecordComponents())
				.map(RecordComponent::getName).toList();
		List<String> lineNames = Arrays.stream(MenuScanResponse.Line.class.getRecordComponents())
				.map(RecordComponent::getName).toList();

		assertThat(names).doesNotContain("safe", "hasAllergen", "allergenFree", "isSafe");
		assertThat(lineNames).doesNotContain("safe", "hasAllergen", "allergenFree", "isSafe");
		// 링크를 만들 칸도 없다 — 주입이 링크로 새는 길을 모양에서 막는다.
		assertThat(lineNames).doesNotContain("link", "url", "href");
	}

	@Test
	@DisplayName("🔴 사진에서 읽은 값은 언제나 ESTIMATED 다 — VERIFIED 를 붙이지 않는다")
	void alwaysEstimated() throws Exception {
		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(
				List.of(new MenuScanResponse.Line("새우튀김 12,000원", "새우튀김", "12,000원", "Fried shrimp",
						"새우튀김 12,000원", List.of("새우"))), 0));

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(jpeg()))
						.principal(principal(this.userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.evidenceStatus").value("ESTIMATED"))
				.andExpect(jsonPath("$.data.lines[0].allergenWords[0]").value("새우"));
	}

	/** 못 읽은 줄이 0 이어도 «전부 안전»이 아니다. 서버는 읽은 것만 주고 주장하지 않는다. */
	@Test
	@DisplayName("🔴 알레르기 낱말을 못 찾아도 「없음」이라고 답하지 않는다")
	void nothingFoundIsNotAClaimOfSafety() throws Exception {
		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(
				List.of(new MenuScanResponse.Line("김밥", "김밥", "", "Gimbap", "김밥", List.of())), 0));

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(jpeg()))
						.principal(principal(this.userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.lines[0].allergenWords").isEmpty())
				.andExpect(jsonPath("$.data.evidenceStatus").value("ESTIMATED"))
				.andExpect(jsonPath("$.data.safe").doesNotExist())
				.andExpect(jsonPath("$.data.hasAllergen").doesNotExist());
	}

	// ── 실패를 빈 결과로 바꾸지 않는다 ──────────────────────────────────────

	@Test
	@DisplayName("🔴 설정이 없으면 빈 목록이 아니라 503 이다")
	void unconfiguredIsFailureNotEmpty() throws Exception {
		when(this.reader.isConfigured()).thenReturn(false);

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(jpeg()))
						.principal(principal(this.userId)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("MENU_SCAN_UNAVAILABLE"));

		verify(this.reader, never()).read(any(), any());
	}

	@Test
	@DisplayName("🔴 한도를 넘기면 조용히 빈 결과가 아니라 429 다")
	void rateLimitedIsFailureNotEmpty() throws Exception {
		this.properties.setPerMinuteLimit(1);
		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(List.of(), 0));

		byte[] image = jpeg();
		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(image))
				.principal(principal(this.userId))).andExpect(status().isOk());

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(image))
						.principal(principal(this.userId)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("MENU_SCAN_RATE_LIMITED"));
	}

	@Test
	@DisplayName("이미지가 아니면 400 — 원본을 그대로 바깥으로 보내지 않는다")
	void nonImageIsRejected() throws Exception {
		this.mockMvc.perform(multipart("/api/v1/menu-scans")
						.file(part("이건 그냥 글자다".getBytes()))
						.principal(principal(this.userId)))
				.andExpect(status().isBadRequest());

		verify(this.reader, never()).read(any(), any());
	}

	// ── 위치 정보를 지우고 보낸다 ──────────────────────────────────────────

	/** 촬영 정보가 들어가는 칸(EXIF)을 흉내 내 원본에 심는다. 눈에 띄는 표식을 넣어 뒤에서 찾는다. */
	private static final String GPS_MARKER = "GPS-SECRET-DO-NOT-LEAK";

	private static byte[] withFakeExif(byte[] jpeg) throws Exception {
		byte[] segment = ("Exif\0\0" + GPS_MARKER).getBytes(StandardCharsets.ISO_8859_1);
		int length = segment.length + 2; // 길이 칸은 자기 자신 2바이트를 포함한다

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);                       // 파일 시작 표시 FF D8
		out.write(0xFF);
		out.write(0xE1);                             // APP1 — EXIF 가 들어가는 칸
		out.write((length >> 8) & 0xFF);
		out.write(length & 0xFF);
		out.write(segment);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	private static boolean contains(byte[] bytes, String marker) {
		return new String(bytes, StandardCharsets.ISO_8859_1).contains(marker);
	}

	/**
	 * 개인정보 처리방침에 «보내기 전에 촬영 위치 정보를 지운다»가 적혀 있다.
	 *
	 * <p>«바이트가 달라졌다»로 재지 않는다 — 흰 사진은 다시 써도 바이트가 같아질 수 있어 그 검사는
	 * 통과해도 아무것도 증명하지 못한다. 대신 원본에 표식을 심고 그 표식이 사라졌는지를 본다.
	 */
	@Test
	@DisplayName("🔴 사진에 딸려 온 촬영 정보는 모델에게 가지 않는다")
	void metadataIsStrippedBeforeLeaving() throws Exception {
		byte[] original = withFakeExif(jpeg());
		// 표식이 실제로 심겼는지부터 본다 — 안 심겼으면 아래 검사는 공짜로 통과한다.
		assertThat(contains(original, GPS_MARKER)).isTrue();

		when(this.reader.read(any(), any())).thenReturn(new GmsMenuReader.Result(List.of(), 0));

		this.mockMvc.perform(multipart("/api/v1/menu-scans").file(part(original))
				.principal(principal(this.userId))).andExpect(status().isOk());

		ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
		verify(this.reader).read(sent.capture(), any());

		assertThat(sent.getValue()).isNotEmpty();
		assertThat(contains(sent.getValue(), GPS_MARKER))
				.as("촬영 정보가 그대로 바깥 모델로 나갔다 — 처리방침이 거짓이 된다")
				.isFalse();
	}
}
