package com.gabolle.backend.exchangerate.adapter;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
import org.springframework.web.util.UriComponentsBuilder;

import com.gabolle.backend.exchangerate.application.ExchangeRateVendorException;
import com.gabolle.backend.exchangerate.application.ExchangeRateVendorPort;
import com.gabolle.backend.exchangerate.config.ExchangeRateProperties;

/**
 * 한국수출입은행 환율 정보(AP01) API 호출. 인증키가 비어 있으면 호출을 시도하지 않고 즉시
 * 명확한 실패를 던진다.
 * 도메인은 {@code oapi.koreaexim.go.kr} 이다 — 옛 {@code www.koreaexim.go.kr} 은 없어졌다.
 */
@Component
@Profile({ "db", "dev" })
public class KoreaeximExchangeRateVendorAdapter implements ExchangeRateVendorPort {

	private static final Logger log = LoggerFactory.getLogger(KoreaeximExchangeRateVendorAdapter.class);

	static final String PROVIDER_NAME = "KOREAEXIM";

	private static final DateTimeFormatter SEARCH_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	private final RestClient restClient;

	private final ExchangeRateProperties properties;

	@Autowired
	public KoreaeximExchangeRateVendorAdapter(RestClient.Builder restClientBuilder, ExchangeRateProperties properties) {
		this(restClientBuilder, properties, timeoutFactory(properties));
	}

	/** 시간 제한 자리를 갈아 끼울 수 있게 열어 둔 생성자 — 테스트 전용. */
	KoreaeximExchangeRateVendorAdapter(RestClient.Builder restClientBuilder, ExchangeRateProperties properties,
			ClientHttpRequestFactory requestFactory) {
		this.properties = properties;
		if (requestFactory != null) {
			restClientBuilder.requestFactory(requestFactory);
		}
		this.restClient = restClientBuilder.build();
	}

	private static ClientHttpRequestFactory timeoutFactory(ExchangeRateProperties properties) {
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
	public String fetchRatesJson(LocalDate searchDate) {
		String authKey = this.properties.getAuthKey();
		if (authKey == null || authKey.isBlank()) {
			throw new ExchangeRateVendorException("EXCHANGE_RATE_VENDOR_NOT_CONFIGURED", "환율 조회 서비스가 설정되지 않았습니다.",
					HttpStatus.BAD_GATEWAY);
		}

		// 공공기관 인증키는 이미 URL 인코딩된 값으로 발급되는 경우가 있어, 키는 문자열로 그대로
		// 이어 붙인다.
		String query = UriComponentsBuilder.newInstance()
				.queryParam("searchdate", searchDate.format(SEARCH_DATE_FORMAT))
				.queryParam("data", "AP01")
				.build()
				.toUriString()
				.replaceFirst("^\\?", "&");

		URI uri = URI.create(this.properties.getBaseUrl() + "?authkey=" + authKey + query);

		try {
			return this.restClient.get().uri(uri).retrieve().body(String.class);
		}
		catch (RestClientException exception) {
			log.warn("환율 조회 호출 실패");
			throw new ExchangeRateVendorException("EXCHANGE_RATE_VENDOR_UNAVAILABLE", "환율 조회 호출에 실패했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}
	}
}
