package com.gabolle.backend.dish.adapter;

import java.util.Base64;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.gabolle.backend.dish.config.DishProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 음식 묘사 한 줄을 받아 <b>그림을 그려 온다</b> — S15P21E201-1272.
 *
 * <h2>🔴 이 호출은 요청 밖에서만 돈다</h2>
 *
 * 2026-09-18 에 같은 중계로 잰 값이다.
 *
 * <pre>
 *   low     / 1024x1024   10.90초
 *   medium  / 1024x1024   15.55초
 *   (기본)  / 1024x1024   45.77초
 * </pre>
 *
 * 앱은 12초에 끊는다. 가장 빠른 {@code low} 도 10.9초라 <b>요청 안에서는 못 준다.</b>
 * 그래서 이 클래스를 부르는 것은 언제나 다른 스레드이고, 시간 제한도 거기에 맞춰
 * 넉넉히(90초) 잡는다 — 짧게 잡으면 정상 호출을 우리가 먼저 끊고 값은 이미 치른 뒤가 된다.
 *
 * <h2>🔴 받은 그림을 그대로 보관하지 않는다</h2>
 *
 * 모델이 주는 것은 1MB 가 넘는 1024 짜리 PNG 인데, 화면에는 손바닥만 하게 뜬다.
 * 그대로 들고 있으면 표가 음식 수의 1MB 배로 늘고, 앱은 그 1MB 를 매번 내려받는다.
 * 그래서 {@link DishImageShrinker} 가 줄여서 JPEG 로 바꾼 것을 보관한다.
 *
 * <p>모델에게 받을 형식을 {@code png} 로 두는 이유가 여기 있다 — 자바 기본
 * {@code ImageIO} 는 <b>webp 를 못 읽는다.</b> webp 가 조금 작지만(1,099KB 대 1,358KB)
 * 어차피 줄이면서 버리는 크기다.
 *
 * <h2>{@code dall-e-3} 는 이 중계에 없다</h2>
 *
 * {@code Model dall-e-3 is not available} 이 돌아온다(2026-09-18 실측). 설정에서 이름만
 * 바꿔 넣으면 그 자리에서 죽는다.
 */
@Component
public class GmsDishPainter {

	/**
	 * 묘사 앞에 늘 붙이는 말.
	 *
	 * <p>🔴 <b>글자를 안 그리게 한다.</b> 모델이 그림 안에 메뉴판이나 간판 글자를 그려
	 * 넣으면, 그 글자는 <b>우리가 읽은 적 없는 글자</b>다. 사용자는 그것을 메뉴판에서 온
	 * 것으로 읽는다 — 우리가 만든 오해가 된다.
	 */
	private static final String PROMPT_PREFIX =
			"A simple, appetizing food photograph of Korean food, plain background, no text, "
					+ "no letters, no watermark, no people, no logo. ";

	private final DishProperties properties;

	private final ObjectMapper objectMapper;

	private final RestClient restClient;

	public GmsDishPainter(DishProperties properties, ObjectMapper objectMapper,
			RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = restClientBuilder.requestFactory(timeoutRequestFactory(properties)).build();
	}

	private static ClientHttpRequestFactory timeoutRequestFactory(DishProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getImageReadTimeout());
		return requestFactory;
	}

	public boolean isConfigured() {
		return !this.properties.getApiKey().isBlank() && !this.properties.getBaseUrl().isBlank();
	}

	/**
	 * @param imagePrompt {@link GmsDishDescriber} 가 만든 영어 묘사
	 * @return 모델이 준 그림의 원본 바이트. 줄이는 것은 부르는 쪽의 몫이다
	 */
	public byte[] paint(String imagePrompt) {
		Map<String, Object> body = Map.of(
				"model", this.properties.getImageModel(),
				"prompt", PROMPT_PREFIX + imagePrompt,
				"n", 1,
				"size", this.properties.getImageSize(),
				"quality", this.properties.getImageQuality(),
				"output_format", this.properties.getImageFormat());

		String raw;
		try {
			raw = this.restClient
					.post()
					.uri(this.properties.getBaseUrl() + "/images/generations")
					.header("Authorization", "Bearer " + this.properties.getApiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
		}
		catch (RestClientResponseException exception) {
			throw new DishPaintFailedException(
					"그림 모델이 요청을 거절했다 (HTTP " + exception.getStatusCode().value() + ")", exception);
		}
		catch (RestClientException exception) {
			throw new DishPaintFailedException("그림 모델에 닿지 못했다", exception);
		}

		try {
			JsonNode root = this.objectMapper.readTree(raw);
			String encoded = root.path("data").path(0).path("b64_json").asString("");
			if (encoded.isBlank()) {
				// 🔴 빈 그림을 «성공» 으로 올리지 않는다. 올리면 READY 인데 바이트가 없는
				//    행이 생기고, 화면은 깨진 그림을 그린다.
				throw new DishPaintFailedException("그림 모델이 빈 답을 줬다", null);
			}
			return Base64.getDecoder().decode(encoded);
		}
		catch (DishPaintFailedException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new DishPaintFailedException("그림을 알아볼 수 없다", exception);
		}
	}

	/** 그림을 못 만들었다. */
	public static class DishPaintFailedException extends RuntimeException {

		public DishPaintFailedException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
