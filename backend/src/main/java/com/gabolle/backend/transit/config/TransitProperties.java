package com.gabolle.backend.transit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 국토교통부 TAGO 버스 정류소·도착정보 조회 설정 — S15P21E201-988.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 서비스 키가 비어 있으면 호출 자체가 명확한
 * 실패({@code TransitVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code WeatherProperties}·{@code TranslateProperties}가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.transit")
public class TransitProperties {

	/** 공공데이터포털이 발급하는 TAGO 인증키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
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
