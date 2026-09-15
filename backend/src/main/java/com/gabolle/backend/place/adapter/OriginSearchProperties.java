package com.gabolle.backend.place.adapter;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 출발지 검색 설정 (S15P21E201-434).
 *
 * <p>🔴 {@code place/config/PlaceProperties} 와 별도 클래스다 — 그건 다른 작업이 같은 시점에
 * 만들고 있어서 여기서 손대지 않는다.
 *
 * <p>🔴 필드 기본값만으로 기동해야 한다. {@code application*.properties} 를 하나도 고치지 않았고,
 * 고칠 계획도 없다 — 카카오 키가 비어 있는 채로 뜨는 것이 이 티켓의 완료 기준이다. 설정이 없다고
 * 기동이 실패하면 안 된다.
 */
@ConfigurationProperties(prefix = "gabolle.place.origin")
public class OriginSearchProperties {

	/** {@code "KAKAO"} 또는 {@code "NONE"}. {@code NONE} 이면 어댑터를 부르지 않고 대체 목록으로 내려간다. */
	private String provider = "KAKAO";

	/** 비어 있으면(기본값) 키가 없는 것으로 보고 대체 목록으로 내려간다. */
	private String kakaoRestApiKey = "";

	private String kakaoBaseUrl = "https://dapi.kakao.com";

	/**
	 * 🔴 값이 있을 때만 {@code KA} 헤더를 붙인다. {@code ref/local-route/server/src/services/kakao.ts}
	 * 가 실측했듯, 등록된 키가 실제로는 JavaScript 키라면 이 헤더 없이는 401
	 * {@code "KA Header is required"} 로 거부된다. 그렇다고 이 우회를 기본으로 켜 두지 않는다 —
	 * 진짜 REST API 키를 받는 것이 맞고, 이 필드는 그 실측을 재현해야 할 때만 쓰는 탈출구다.
	 */
	private String kakaoKaOrigin = "";

	private Duration connectTimeout = Duration.ofSeconds(3);

	private Duration readTimeout = Duration.ofSeconds(5);

	private int maxLimit = 15;

	/**
	 * 검색을 가둘 사각형 — S15P21E201-979. {@code 왼쪽경도,아래위도,오른쪽경도,위쪽위도} 다.
	 *
	 * <p>기본값은 부산광역시를 덮는 상자다. 이 값이 없던 동안 카카오에 전국을 물어봐서,
	 * "서면" 을 치면 부산 서면이 아니라 전남 순천시 서면의 장소만 여덟 개가 나왔다. 부산
	 * 결과는 한 건도 없었다. 이 앱은 부산 여행만 다루므로 다른 지역을 보여 줄 이유가 없다.
	 *
	 * <p>빈 값으로 두면 제한 없이 부른다 — 부산 밖을 다루게 되는 날 설정만 비우면 된다.
	 * 코드에 지역을 박지 않는 이유가 그것이다.
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
