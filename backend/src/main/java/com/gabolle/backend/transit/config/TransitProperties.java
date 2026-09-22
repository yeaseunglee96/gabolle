package com.gabolle.backend.transit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 국토교통부 TAGO 버스 정류소·도착정보 조회 설정. 서비스 키가 비어 있어도 기동은 성공해야 한다 —
 * 호출 시점에 {@code TransitVendorException} 으로 실패한다.
 */
@ConfigurationProperties(prefix = "gabolle.transit")
public class TransitProperties {

	/** 공공데이터포털이 발급하는 TAGO 인증키. */
	private String serviceKey = "";

	private String baseUrl = "https://apis.data.go.kr/1613000";

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	/**
	 * 근처 정류소를 몇 곳까지 볼 것인가 — 정류소 하나당 도착정보 호출이 하나씩 더 나가므로,
	 * 너무 크게 잡으면 응답 하나에 벤더 호출이 여러 번 몰린다.
	 */
	private int maxStops = 3;

	public String getServiceKey() { return this.serviceKey; }
	public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
	public String getBaseUrl() { return this.baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public int getMaxStops() { return this.maxStops; }
	public void setMaxStops(int maxStops) { this.maxStops = maxStops; }
}
