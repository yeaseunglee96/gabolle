package com.gabolle.backend.exchangerate.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국수출입은행 환율 조회 설정 — S15P21E201-1079.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 인증키가 비어 있으면 호출 자체가 명확한
 * 실패({@code ExchangeRateVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code WeatherProperties}·{@code TransitProperties}가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.exchange-rate")
public class ExchangeRateProperties {

	/** 한국수출입은행이 발급하는 인증키(authkey). 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String authKey = "";

	private String baseUrl = "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON";

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	public String getAuthKey() { return this.authKey; }
	public void setAuthKey(String authKey) { this.authKey = authKey; }
	public String getBaseUrl() { return this.baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
