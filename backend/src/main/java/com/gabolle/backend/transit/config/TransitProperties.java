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
	 * 몇 곳을 돌려줄 것인가 — 가장 가까운 이만큼은 늘 보고, 그중 하나라도 조회에 실패하면 요청 전체가 실패한다.
	 * 앱은 받은 정류소를 모두 카드로 그리므로 이 값이 곧 화면의 카드 수다. 정류소 하나당 도착정보 호출이 하나씩 더 나간다.
	 */
	private int maxStops = 3;

	/**
	 * 몇 곳까지 볼 것인가 (S15P21E201-1755). 가까운 {@link #maxStops} 곳 가운데 「오는 버스 없음」이 있으면, 그다음
	 * 정류소를 이만큼까지 「덤」으로 더 보고 도착이 있는 곳을 앞으로 올린다. 덤은 실패하거나 늦으면 버린다.
	 */
	private int candidateStops = 5;

	/**
	 * 덤 호출의 읽기 제한 — {@link #readTimeout}(5초)보다 짧다. 차례로 부르므로 덤이 늦으면 버려도 기다린 시간은 사용자에게
	 * 그대로 더해진다. 그 상한이다. 2026-09-26 실측 도착 호출 90% 가 1.2초였다.
	 */
	private Duration extraReadTimeout = Duration.ofMillis(1500);

	/** 근처 정류소 목록을 다시 부르지 않는 시간 (S15P21E201-1956). 정류소는 움직이지 않는다. */
	private Duration stopsCacheTtl = Duration.ofMinutes(10);

	/** 도착 목록을 다시 부르지 않는 시간. 도착 시간은 이 정도면 거의 그대로다. */
	private Duration arrivalsFreshTtl = Duration.ofSeconds(20);

	/** 도착 호출이 실패했을 때 대신 쓸 수 있는 값의 나이 상한. 넘으면 실패를 그대로 알린다. */
	private Duration arrivalsStaleTtl = Duration.ofSeconds(90);

	public Duration getStopsCacheTtl() { return this.stopsCacheTtl; }
	public void setStopsCacheTtl(Duration stopsCacheTtl) { this.stopsCacheTtl = stopsCacheTtl; }
	public Duration getArrivalsFreshTtl() { return this.arrivalsFreshTtl; }
	public void setArrivalsFreshTtl(Duration arrivalsFreshTtl) { this.arrivalsFreshTtl = arrivalsFreshTtl; }
	public Duration getArrivalsStaleTtl() { return this.arrivalsStaleTtl; }
	public void setArrivalsStaleTtl(Duration arrivalsStaleTtl) { this.arrivalsStaleTtl = arrivalsStaleTtl; }
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
	public int getCandidateStops() { return this.candidateStops; }
	public void setCandidateStops(int candidateStops) { this.candidateStops = candidateStops; }
	public Duration getExtraReadTimeout() { return this.extraReadTimeout; }
	public void setExtraReadTimeout(Duration extraReadTimeout) { this.extraReadTimeout = extraReadTimeout; }
}
