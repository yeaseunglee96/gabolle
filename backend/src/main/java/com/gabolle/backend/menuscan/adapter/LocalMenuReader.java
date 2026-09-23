package com.gabolle.backend.menuscan.adapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.presentation.dto.MenuScanResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 서버 안의 메뉴판 OCR 컨테이너({@code backend/menu-ocr})로 사진을 읽는다 (S15P21E201-1538).
 *
 * <p>그 컨테이너가 하는 일: 글자 위치 찾기(PP-OCRv5, NNCF 보정 INT8) → 한 줄 읽기(한국어 PP-OCRv5,
 * FP32) → 닮은 한 글자 사전 보정 → 이름·가격 짝짓기 → 한식진흥원 800선 사전 번역. 전부 OpenVINO 로
 * 운영 서버 CPU 에서 돈다 — 실사진 한 장 1.9초(2026-09-23 실측). 바깥으로 나가는 것은 사전에 없는 이름의
 * 글자 번역({@link GmsNameTranslator}) 하나뿐이다.
 *
 * <p>실패하면 {@link LocalReadFailedException} 을 던지고, 부르는 쪽이 GMS 비전으로 대신 읽는다(사용자가
 * 정했다, 2026-09-23). 음식 줄을 하나도 못 짝지은 것도 실패로 본다 — 빈 목록을 주면 화면은 «이 메뉴판에
 * 음식이 없다»고 말하게 된다.
 *
 * <p>알레르기 칸은 비워 둔다. 이 기능에서 빠질 예정이고, 빈 칸의 뜻은 «못 찾았다»다({@link
 * MenuScanResponse.Line}).
 */
@Component
public class LocalMenuReader {

	private static final Logger log = LoggerFactory.getLogger(LocalMenuReader.class);

	/** 컨테이너에 넘겨도 되는 언어 값. 이 밖의 값은 한국어로 떨어뜨린다 — 사용자 값을 주소에 그대로 싣지 않는다. */
	private static final Set<String> LANGUAGES = Set.of("ko", "en", "ja", "zh-Hans", "zh-Hant");

	private final MenuScanProperties properties;

	private final ObjectMapper objectMapper;

	private final RestClient restClient;

	private final GmsNameTranslator translator;

	public LocalMenuReader(MenuScanProperties properties, ObjectMapper objectMapper,
			RestClient.Builder restClientBuilder, GmsNameTranslator translator) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.translator = translator;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getLocalConnectTimeout());
		requestFactory.setReadTimeout(properties.getLocalReadTimeout());
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
	}

	/** 주소가 비어 있으면 부르지 않는다 — 그 컨테이너가 없는 곳에서 매번 연결 실패를 겪지 않게. */
	public boolean isConfigured() {
		return !this.properties.getLocalBaseUrl().isBlank();
	}

	public GmsMenuReader.Result read(byte[] jpeg, String language) {
		String lang = (language != null && LANGUAGES.contains(language)) ? language : "ko";
		long started = System.nanoTime();
		byte[] raw;
		try {
			// 바이트로 받아 UTF-8 로 읽는다 — 응답 머리글의 문자 인코딩에 기대면, 머리글이 빠진 날 한글
			// 음식 이름이 깨진 채로 화면에 나간다(시험의 가짜 서버에서 실제로 그랬다)
			raw = this.restClient.post()
					.uri(this.properties.getLocalBaseUrl() + "/v1/read?lang={lang}", lang)
					.contentType(MediaType.IMAGE_JPEG)
					.body(jpeg)
					.retrieve()
					.body(byte[].class);
		}
		catch (RestClientException exception) {
			throw new LocalReadFailedException("우리 모델에 닿지 못했거나 거절당했다 — " + exception.getMessage(), exception);
		}

		JsonNode root;
		try {
			root = this.objectMapper.readTree(new String(raw == null ? new byte[0] : raw, StandardCharsets.UTF_8));
		}
		catch (RuntimeException exception) {
			throw new LocalReadFailedException("우리 모델의 답을 알아볼 수 없다", exception);
		}

		List<Draft> drafts = new ArrayList<>();
		for (JsonNode line : root.path("lines")) {
			if (drafts.size() >= this.properties.getMaxLines()) {
				break;
			}
			String text = clamp(line.path("text").asString(""));
			if (text.isBlank()) {
				continue;
			}
			drafts.add(new Draft(text, clamp(line.path("name").asString("")), clamp(line.path("price").asString("")),
					clamp(line.path("translatedName").asString(""))));
		}
		long dishes = drafts.stream().filter(d -> !d.name().isBlank()).count();
		if (dishes == 0) {
			throw new LocalReadFailedException("음식 줄을 하나도 짝짓지 못했다 (읽은 줄 " + drafts.size() + ")", null);
		}

		// 사전에 없는 이름만 모아 한 번에 옮긴다
		Set<String> missing = new LinkedHashSet<>();
		if (!"ko".equals(lang)) {
			for (Draft d : drafts) {
				if (!d.name().isBlank() && d.translatedName().isBlank()) {
					missing.add(d.name());
				}
			}
		}
		Map<String, String> translated = missing.isEmpty() ? Map.of()
				: this.translator.translate(List.copyOf(missing), lang);

		List<MenuScanResponse.Line> lines = new ArrayList<>();
		for (Draft d : drafts) {
			String name = d.name();
			String translatedName = d.translatedName().isBlank() ? translated.getOrDefault(name, "") : d.translatedName();
			// 음식 줄이 아니면(안내문·가게 이름) 원문 그대로 — GMS 비전은 이것도 옮겼지만 여기서는 안 옮긴다
			String translatedText = translatedName.isBlank() ? d.text()
					: clamp(d.price().isBlank() ? translatedName : translatedName + " " + d.price());
			lines.add(new MenuScanResponse.Line(d.text(), name, d.price(), translatedName, translatedText, List.of()));
		}
		int unread = Math.max(root.path("unreadLineCount").asInt(0), 0);

		log.info("메뉴판을 우리 모델로 읽었다 — 언어 {}, 음식 {}줄, 못 읽은 줄 {}, 모델 {}ms, 전체 {}ms, "
				+ "사전에 없어 GMS 로 옮긴 이름 {}/{} ({})",
				lang, dishes, unread, root.path("timingsMs").path("total").asInt(-1),
				(System.nanoTime() - started) / 1_000_000, translated.size(), missing.size(),
				root.path("model").path("det").asString(""));
		return new GmsMenuReader.Result(List.copyOf(lines), unread);
	}

	private String clamp(String value) {
		String trimmed = (value == null) ? "" : value.trim();
		int max = this.properties.getMaxLineLength();
		return (trimmed.length() <= max) ? trimmed : trimmed.substring(0, max);
	}

	private record Draft(String text, String name, String price, String translatedName) {
	}

	/** 우리 모델이 못 읽었다. 부르는 쪽이 GMS 비전으로 대신 읽는다 — 사용자에게 바로 나가는 예외가 아니다. */
	public static class LocalReadFailedException extends RuntimeException {

		public LocalReadFailedException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
