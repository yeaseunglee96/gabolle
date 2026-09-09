package com.gabolle.backend.route.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 경로 조회 설정 — S15P21E201-184 · -189 · -196.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 카카오 키가 비어 있으면 실제 경로 대신
 * 추정으로 답할 뿐, 기동이 실패하면 안 된다 — {@code OriginSearchProperties} 가 같은 이유로
 * 같은 규칙을 지킨다.
 *
 * <h2>🔴 추정에 쓰는 속도는 잰 값이 아니다</h2>
 *
 * 아래 {@code carSpeedKmh}·{@code walkSpeedKmh}·{@code transitSpeedKmh}·{@code detourFactor}
 * 는 <b>아무도 재지 않은 값</b>이다. 그래서 코드에 박지 않고 설정으로 뺐다 — 나중에 실제
 * 이동 기록(S15P21E201-293 이 모으고 있다)으로 맞출 수 있게 하려는 것이다.
 *
 * <p>이 값들로 만든 답에는 반드시 "추정" 표시가 붙는다. 표시 없이 내보내면 그건 추정이
 * 아니라 창작이고, 화면은 그것을 실제 소요시간으로 그린다.
 */
@ConfigurationProperties(prefix = "gabolle.route")
public class RouteProperties {

	/**
	 * 카카오 REST API 키. 비어 있으면 자차 경로도 추정으로 답한다.
	 *
	 * <p>🔴 카카오는 앱 하나에 REST API 키를 <b>하나만</b> 준다. 로그인에 쓰는 값
	 * ({@code gabolle.oauth.kakao.client-id})과 길찾기에 쓰는 값이 같은 키다 — 우연이 아니라
	 * 카카오가 그렇게 설계했다. 그래서 배포 설정에서 그 값을 그대로 물려받게 해 두었고,
	 * 나중에 앱을 나누면 이 키만 따로 주면 된다.
	 */
	private String kakaoRestApiKey = "";

	private String kakaoMobilityBaseUrl = "https://apis-navi.kakaomobility.com";

	private Duration connectTimeout = Duration.ofSeconds(2);

	/**
	 * 🔴 읽기 시간 제한을 3초로 짧게 잡는다. 경로는 화면이 기다리는 값이고, 늦게 오는 정확한
	 * 답보다 <b>바로 오는 추정</b>이 낫다 — 추정이라는 사실이 응답에 실려 나가기 때문이다.
	 */
	private Duration readTimeout = Duration.ofSeconds(3);

	/** 도심 자차 평균 속도. 잰 값이 아니다(클래스 javadoc 참고). */
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

	/** 같은 경로를 다시 물었을 때 답을 재사용하는 시간 — S15P21E201-196. */
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
