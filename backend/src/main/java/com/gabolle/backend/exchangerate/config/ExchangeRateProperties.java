package com.gabolle.backend.exchangerate.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국수출입은행 환율 조회 설정. 인증키가 비어 있어도 기동은 성공해야 한다 — 호출 시점에
 * {@code ExchangeRateVendorException} 으로 실패한다.
 */
@ConfigurationProperties(prefix = "gabolle.exchange-rate")
public class ExchangeRateProperties {

	/** 한국수출입은행이 발급하는 인증키(authkey). */
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
