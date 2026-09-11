package com.gabolle.backend.weather.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청 단기예보 조회 설정 — S15P21E201-366.
 *
 * <p>🔴 <b>필드 기본값만으로 기동해야 한다.</b> 서비스 키가 비어 있으면 호출 자체가 명확한
 * 실패({@code WeatherVendorException})로 올라갈 뿐, 기동이 실패하면 안 된다 —
 * {@code TranslateProperties}·{@code RouteProperties} 가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.weather")
public class WeatherProperties {

	/** 공공데이터포털이 발급하는 기상청 단기예보 조회서비스 인증키. 비어 있으면 호출이 즉시 명확한 실패로 끝난다. */
	private String kmaServiceKey = "";

	private String kmaBaseUrl = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	/**
	 * 같은 격자·같은 발표 회차의 응답을 다시 받지 않고 재사용하는 시간. 발표 회차가 열쇠에
	 * 이미 들어 있어 그 열쇠의 값은 절대 안 바뀐다 — 이 값은 그저 DB 에 얼마나 오래 들고
	 * 있을지를 정할 뿐이다.
	 */
	private Duration cacheTtl = Duration.ofDays(1);

	public String getKmaServiceKey() { return this.kmaServiceKey; }
	public void setKmaServiceKey(String kmaServiceKey) { this.kmaServiceKey = kmaServiceKey; }
	public String getKmaBaseUrl() { return this.kmaBaseUrl; }
	public void setKmaBaseUrl(String kmaBaseUrl) { this.kmaBaseUrl = kmaBaseUrl; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
	public Duration getCacheTtl() { return this.cacheTtl; }
	public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
}
