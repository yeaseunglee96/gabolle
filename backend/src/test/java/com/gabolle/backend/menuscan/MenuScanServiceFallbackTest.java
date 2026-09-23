package com.gabolle.backend.menuscan;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.adapter.LocalMenuReader;
import com.gabolle.backend.menuscan.application.MenuScanRateLimiter;
import com.gabolle.backend.menuscan.application.MenuScanService;
import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 우리 모델 먼저, 실패하면 GMS 비전으로 대신 — 사용자가 정한 순서다 (2026-09-23, S15P21E201-1538).
 *
 * <p>이 순서가 깨지는 방식은 둘이다. 우리 모델이 잘 읽었는데도 GMS 를 또 부르면 값을 두 번 치르고,
 * 우리 모델이 실패했는데 GMS 로 안 넘어가면 사용자는 기능을 못 쓴다. 둘 다 화면에는 티가 안 난다.
 */
class MenuScanServiceFallbackTest {

	private final UUID userId = UUID.randomUUID();

	private GmsMenuReader gms;
	private LocalMenuReader local;
	private MenuScanRateLimiter limiter;
	private MenuScanService service;
	private byte[] jpeg;

	private static final GmsMenuReader.Result LOCAL_RESULT = new GmsMenuReader.Result(List.of(
			new MenuScanResponse.Line("밀면 8,000", "밀면", "8,000", "Wheat noodles", "Wheat noodles 8,000", List.of())), 0);

	private static final GmsMenuReader.Result GMS_RESULT = new GmsMenuReader.Result(List.of(
			new MenuScanResponse.Line("밀면 8,000원", "밀면", "8,000원", "Milmyeon", "Milmyeon 8,000 won", List.of())), 1);

	@BeforeEach
	void setUp() throws Exception {
		MenuScanProperties properties = new MenuScanProperties();
		this.gms = mock(GmsMenuReader.class);
		this.local = mock(LocalMenuReader.class);
		this.limiter = mock(MenuScanRateLimiter.class);
		this.service = new MenuScanService(this.gms, this.local, this.limiter, properties);
		when(this.gms.isConfigured()).thenReturn(true);
		when(this.local.isConfigured()).thenReturn(true);

		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		this.jpeg = out.toByteArray();
	}

	@Test
	@DisplayName("우리 모델이 읽으면 GMS 는 부르지 않는다 — 값을 두 번 치르지 않는다")
	void theLocalModelAnswersAlone() {
		when(this.local.read(any(), any())).thenReturn(LOCAL_RESULT);

		MenuScanResponse response = this.service.scan(this.userId, this.jpeg, "en");

		assertThat(response.lines().get(0).translatedName()).isEqualTo("Wheat noodles");
		verify(this.gms, never()).read(any(), any());
	}

	@Test
	@DisplayName("우리 모델이 실패하면 GMS 비전으로 대신 읽는다")
	void aLocalFailureFallsBackToGms() {
		when(this.local.read(any(), any())).thenThrow(new LocalMenuReader.LocalReadFailedException("떠 있지 않다", null));
		when(this.gms.read(any(), any())).thenReturn(GMS_RESULT);

		MenuScanResponse response = this.service.scan(this.userId, this.jpeg, "en");

		assertThat(response.lines().get(0).translatedName()).isEqualTo("Milmyeon");
		assertThat(response.unreadLineCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("대신 읽어도 한도는 한 번만 센다")
	void theFallbackDoesNotChargeTwice() {
		when(this.local.read(any(), any())).thenThrow(new LocalMenuReader.LocalReadFailedException("5초 넘김", null));
		when(this.gms.read(any(), any())).thenReturn(GMS_RESULT);

		this.service.scan(this.userId, this.jpeg, "en");

		verify(this.limiter, times(1)).takeOrThrow(this.userId);
	}

	@Test
	@DisplayName("우리 모델이 실패했는데 GMS 설정도 없으면 명확한 실패로 올린다 — 빈 결과를 주지 않는다")
	void noFallbackMeansAClearFailure() {
		when(this.gms.isConfigured()).thenReturn(false);
		when(this.local.read(any(), any())).thenThrow(new LocalMenuReader.LocalReadFailedException("떠 있지 않다", null));

		assertThatThrownBy(() -> this.service.scan(this.userId, this.jpeg, "en"))
				.isInstanceOf(GmsMenuReader.MenuReadFailedException.class);
	}

	@Test
	@DisplayName("우리 모델 주소가 비어 있으면 곧장 GMS 로 읽는다 — 끄는 손잡이")
	void aBlankLocalAddressGoesStraightToGms() {
		when(this.local.isConfigured()).thenReturn(false);
		when(this.gms.read(any(), any())).thenReturn(GMS_RESULT);

		this.service.scan(this.userId, this.jpeg, "en");

		verify(this.local, never()).read(any(), any());
	}
}
