package com.gabolle.backend.place.photo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 장소 사진 대리 조회 설정(S15P21E201-1832). 필드 기본값만으로 기동해야 한다 — 키가 비면
 * 사진 요청이 404 로 끝날 뿐 기동이 실패하면 안 된다.
 */
@ConfigurationProperties(prefix = "gabolle.google-places")
public class GooglePlacePhotoProperties {

	/** Places API (New) 키. Google Cloud 쪽에서 서버 IP 와 Places API (New) 하나로 묶어 두었다. */
	private String apiKey = "";

	private String baseUrl = "https://places.googleapis.com/v1";

	/** 카드·상세 화면에서 가장 넓게 그리는 폭에 맞춘다. 더 크게 받으면 전송만 늘고 값은 같다. */
	private int maxWidthPx = 800;

	/**
	 * 받아 온 사진 주소를 메모리에 들고 있는 시간. Google 은 사진을 저장하지 말라고 하므로 DB 에
	 * 쓰지 않고, 같은 장소를 잇달아 여는 사람마다 두 번씩 과금되지 않을 만큼만 짧게 들고 있는다.
	 */
	private Duration cacheTtl = Duration.ofMinutes(30);

	/** 메모리 상한. 넘으면 비우고 다시 채운다 — 사진 주소 하나가 수백 바이트라 넉넉하다. */
	private int cacheMaxEntries = 20_000;

	private Duration connectTimeout = Duration.ofSeconds(2);

	private Duration readTimeout = Duration.ofSeconds(5);

	public String getApiKey() { return this.apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getBaseUrl() { return this.baseUrl; }
	public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
	public int getMaxWidthPx() { return this.maxWidthPx; }
	public void setMaxWidthPx(int maxWidthPx) { this.maxWidthPx = maxWidthPx; }
	public Duration getCacheTtl() { return this.cacheTtl; }
	public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
	public int getCacheMaxEntries() { return this.cacheMaxEntries; }
	public void setCacheMaxEntries(int cacheMaxEntries) { this.cacheMaxEntries = cacheMaxEntries; }
	public Duration getConnectTimeout() { return this.connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return this.readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
