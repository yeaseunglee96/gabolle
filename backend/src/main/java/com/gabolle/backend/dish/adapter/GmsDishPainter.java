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
 * 음식 묘사 한 줄을 받아 그림을 그려 온다.
 *
 * <p>이 호출은 요청 밖에서만 돈다. 가장 빠른 설정으로도 10초가 넘어 앱의 12초 제한 안에 못 들어간다.
 * 시간 제한을 90초로 넉넉히 잡는 것도 그래서다 — 짧게 잡으면 값은 치르고 답만 버린다.
 *
 * <p>받은 그림은 1MB 가 넘는 1024 짜리라 그대로 보관하지 않는다. {@link DishImageShrinker} 가
 * 줄여서 JPEG 로 바꾼 것을 보관한다. 받을 형식을 {@code png} 로 두는 이유는 자바 기본
 * {@code ImageIO} 가 webp 를 못 읽기 때문이다.
 *
 * <p>{@code dall-e-3} 는 이 중계에 없다. 설정에서 이름만 바꿔 넣으면 그 자리에서 죽는다.
 */
@Component
public class GmsDishPainter {

	/**
	 * 묘사 앞에 늘 붙이는 말. 글자를 안 그리게 한다 — 그림 안의 글자는 우리가 읽은 적 없는 글자인데
	 * 사용자는 메뉴판에서 온 것으로 읽는다.
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
				// 빈 그림을 성공으로 올리면 READY 인데 바이트가 없는 행이 생긴다.
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
