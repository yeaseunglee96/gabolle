package com.gabolle.backend.route.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 경로 조회 설정. 필드 기본값만으로 기동해야 한다 — 카카오 키가 비면 추정으로 답할 뿐
 * 기동이 실패하면 안 된다.
 *
 * carSpeedKmh · walkSpeedKmh · transitSpeedKmh · detourFactor 는 실측이 아니다. 나중에
 * 실제 이동 기록으로 맞출 수 있도록 코드에 박지 않고 설정으로 뺐다.
 * 이 값들로 만든 답에는 반드시 추정 표시가 붙는다 — 표시 없이 나가면 화면이 실제
 * 소요시간으로 그린다.
 */
@ConfigurationProperties(prefix = "gabolle.route")
public class RouteProperties {

	/**
	 * 카카오 REST API 키. 비어 있으면 자차 경로도 추정으로 답한다.
	 * 카카오는 앱 하나에 REST API 키를 하나만 주므로 로그인용 gabolle.oauth.kakao.client-id 와
	 * 같은 값이고, 배포 설정에서 그대로 물려받는다.
	 */
	private String kakaoRestApiKey = "";

	private String kakaoMobilityBaseUrl = "https://apis-navi.kakaomobility.com";

	private Duration connectTimeout = Duration.ofSeconds(2);

	/**
	 * 읽기 시간 제한을 3초로 짧게 잡는다. 경로는 화면이 기다리는 값이라 늦게 오는 정확한
	 * 답보다 바로 오는 추정이 낫다 — 추정이라는 사실이 응답에 실려 나간다.
	 */
	private Duration readTimeout = Duration.ofSeconds(3);

	/** 도심 자차 평균 속도. 잰 값이 아니다. */
	private double carSpeedKmh = 25;

	/** 걷는 속도. 잰 값이 아니다. */
	private double walkSpeedKmh = 4;

	/** 대중교통 평균 속도 — 기다리는 시간과 환승을 뭉뚱그린 값이다. 잰 값이 아니다. */
	private double transitSpeedKmh = 18;

	/**
	 * 직선거리를 실제 이동 거리로 볼 때 곱하는 비율. 길은 직선으로 나 있지 않다.
	 * 잰 값이 아니다.
	 */
	private double detourFactor = 1.3;

	/** 같은 경로를 다시 물었을 때 답을 재사용하는 시간. */
	private Duration cacheTtl = Duration.ofHours(6);

	/** 캐시에 담아 두는 최대 경로 수. 넘으면 가장 오래 안 쓴 것부터 버린다. */
	private int cacheMaxEntries = 5_000;

	public String getKakaoRestApiKey() { return this.kakaoRestApiKey; }
	public void setKakaoRestApiKey(String kakaoRestApiKey) { this.kakaoRestApiKey = kakaoRestApiKey; }
	public String getKakaoMobilityBaseUrl() { return this.kakaoMobilityBaseUrl; }
	public void setKakaoMobilityBaseUrl(String kakaoMobilityBaseUrl) { this.kakaoMobilityBaseUrl = kakaoMobilityBaseUrl; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public double getCarSpeedKmh() { return this.carSpeedKmh; }
	public void setCarSpeedKmh(double carSpeedKmh) { this.carSpeedKmh = carSpeedKmh; }
	public double getWalkSpeedKmh() { return this.walkSpeedKmh; }
	public void setWalkSpeedKmh(double walkSpeedKmh) { this.walkSpeedKmh = walkSpeedKmh; }
	public double getTransitSpeedKmh() { return this.transitSpeedKmh; }
	public void setTransitSpeedKmh(double transitSpeedKmh) { this.transitSpeedKmh = transitSpeedKmh; }
	public double getDetourFactor() { return this.detourFactor; }
	public void setDetourFactor(double detourFactor) { this.detourFactor = detourFactor; }
	public Duration getCacheTtl() { return this.cacheTtl; }
	public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
	public int getCacheMaxEntries() { return this.cacheMaxEntries; }
	public void setCacheMaxEntries(int cacheMaxEntries) { this.cacheMaxEntries = cacheMaxEntries; }
}
