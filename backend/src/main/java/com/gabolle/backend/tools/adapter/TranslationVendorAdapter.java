package com.gabolle.backend.tools.adapter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gabolle.backend.tools.application.TranslationVendorException;
import com.gabolle.backend.tools.application.TranslationVendorPort;
import com.gabolle.backend.tools.config.TranslateProperties;
import com.gabolle.backend.tools.domain.TranslationDirection;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

/**
 * 실제 번역 업체 호출 — S15P21E201-343.
 *
 * <p>이 저장소에 아직 실제 번역 업체 계약이 없다. 그래서 엔드포인트·키를 설정
 * ({@code gabolle.tools.translate.*})으로 받는 얇은 어댑터 하나만 둔다 — 업체가 정해지면
 * URL·요청/응답 모양만 이 클래스 안에서 바꾸면 된다({@link TranslationVendorPort} 를 통해
 * 부르는 {@code TranslationService} 는 손대지 않는다).
 *
 * <p>🔴 키나 엔드포인트가 비어 있으면 <b>호출을 시도하지 않고 즉시 명확한 실패</b>를 던진다.
 * 미리 정해 둔 문장을 돌려주며 성공한 척하는 것은 이 티켓이 명시적으로 금지한 바로 그
 * 버그다 — {@code KakaoMobilityRouteAdapter} 처럼 조용히 빈 값으로 넘어가지 않는다.
 *
 * <p>🔴 <b>원문을 로그에 남기지 않는다.</b> 실패 로그는 방향만 남긴다 — 좌표를 로그에 남기지
 * 않는 {@code KakaoMobilityRouteAdapter} 와 같은 이유다.
 */
@Component
@Profile({ "db", "dev" })
public class TranslationVendorAdapter implements TranslationVendorPort {

	private static final Logger log = LoggerFactory.getLogger(TranslationVendorAdapter.class);

	static final String PROVIDER_NAME = "GENERIC_TRANSLATE_VENDOR";

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final TranslateProperties properties;

	/** 생성자가 둘이라 어느 것으로 DI 할지 명시한다 — {@code KakaoMobilityRouteAdapter} 의 실측 참고. */
	@Autowired
	public TranslationVendorAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			TranslateProperties properties) {
		this(restClientBuilder, objectMapper, properties, timeoutFactory(properties));
	}

	/** 시간 제한 자리를 갈아 끼울 수 있게 열어 둔 생성자 — 테스트 전용. */
	TranslationVendorAdapter(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			TranslateProperties properties, ClientHttpRequestFactory requestFactory) {
		this.properties = properties;
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		this.restClient = restClientBuilder.build();
		this.objectMapper = objectMapper;
	}

	private static ClientHttpRequestFactory timeoutFactory(TranslateProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		return requestFactory;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public String translate(String sourceText, TranslationDirection direction) {
		String apiKey = this.properties.getVendorApiKey();
		String baseUrl = this.properties.getVendorBaseUrl();
		if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
			throw new TranslationVendorException("TRANSLATE_VENDOR_NOT_CONFIGURED",
					"번역 업체가 설정되지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}

		String body;
		try {
			body = this.restClient.post()
					.uri(URI.create(baseUrl))
					.header("Authorization", "Bearer " + apiKey)
					.body(new VendorRequest(sourceText, direction.name()))
					.retrieve()
					.body(String.class);
		}
		catch (RestClientException exception) {
			// 🔴 원문을 찍지 않는다 — 방향만 남긴다.
			log.warn("번역 업체 호출 실패 direction={}", direction);
			throw new TranslationVendorException("TRANSLATE_VENDOR_UNAVAILABLE",
					"번역 업체 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY, exception);
		}

		JsonNode root;
		try {
			root = this.objectMapper.readTree(body);
		}
		catch (JacksonException exception) {
			log.warn("번역 업체 응답 파싱 실패 direction={}", direction);
			throw new TranslationVendorException("TRANSLATE_VENDOR_UNAVAILABLE",
					"번역 업체 응답을 해석하지 못했습니다.", HttpStatus.BAD_GATEWAY, exception);
		}

		String translated = root.path("translatedText").asText(null);
		if (translated == null || translated.isBlank()) {
			log.warn("번역 업체가 결과를 주지 않음 direction={}", direction);
			throw new TranslationVendorException("TRANSLATE_VENDOR_UNAVAILABLE",
					"번역 업체가 결과를 주지 않았습니다.", HttpStatus.BAD_GATEWAY);
		}
		return translated;
	}

	private record VendorRequest(String text, String direction) {
	}
}
