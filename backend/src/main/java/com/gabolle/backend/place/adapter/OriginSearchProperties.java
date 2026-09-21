package com.gabolle.backend.place.adapter;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 출발지 검색 설정. 필드 기본값만으로 기동해야 한다 — 카카오 키가 비어 있어도 기동이 실패하면 안 된다.
 */
@ConfigurationProperties(prefix = "gabolle.place.origin")
public class OriginSearchProperties {

	/** {@code "KAKAO"} 또는 {@code "NONE"}. {@code NONE} 이면 어댑터를 부르지 않고 대체 목록으로 내려간다. */
	private String provider = "KAKAO";

	/** 비어 있으면(기본값) 키가 없는 것으로 보고 대체 목록으로 내려간다. */
	private String kakaoRestApiKey = "";

	private String kakaoBaseUrl = "https://dapi.kakao.com";

	/**
	 * 값이 있을 때만 {@code KA} 헤더를 붙인다. 등록된 키가 실제로는 JavaScript 키라면 이 헤더 없이
	 * 401 {@code "KA Header is required"} 로 거부되는데, 그 우회를 기본으로 켜 두지 않는다.
	 */
	private String kakaoKaOrigin = "";

	private Duration connectTimeout = Duration.ofSeconds(3);

	private Duration readTimeout = Duration.ofSeconds(5);

	private int maxLimit = 15;

	/**
	 * 검색을 가둘 사각형. {@code 왼쪽경도,아래위도,오른쪽경도,위쪽위도} 순이고 기본값은 부산광역시를
	 * 덮는 상자다. 빈 값으로 두면 제한 없이 부른다 — 부산 밖을 다루게 되는 날 설정만 비우면 된다.
	 */
	private String searchRect = "128.75,34.88,129.32,35.39";

	public String getProvider() {
		return this.provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getKakaoRestApiKey() {
		return this.kakaoRestApiKey;
	}

	public void setKakaoRestApiKey(String kakaoRestApiKey) {
		this.kakaoRestApiKey = kakaoRestApiKey;
	}

	public String getSearchRect() {
		return this.searchRect;
	}

	public void setSearchRect(String searchRect) {
		this.searchRect = searchRect;
	}

	public String getKakaoBaseUrl() {
		return this.kakaoBaseUrl;
	}

	public void setKakaoBaseUrl(String kakaoBaseUrl) {
		this.kakaoBaseUrl = kakaoBaseUrl;
	}

	public String getKakaoKaOrigin() {
		return this.kakaoKaOrigin;
	}

	public void setKakaoKaOrigin(String kakaoKaOrigin) {
		this.kakaoKaOrigin = kakaoKaOrigin;
	}

	public Duration getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return this.readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public int getMaxLimit() {
		return this.maxLimit;
	}

	public void setMaxLimit(int maxLimit) {
		this.maxLimit = maxLimit;
	}
}
