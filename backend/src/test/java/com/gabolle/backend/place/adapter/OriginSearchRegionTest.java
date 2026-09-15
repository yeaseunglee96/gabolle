package com.gabolle.backend.place.adapter;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출발지 검색이 부산만 보는가 — S15P21E201-979.
 *
 * <p>이 제한이 없던 동안 카카오에 전국을 물어봤다. "서면" 을 치면 부산 서면이 아니라 전남
 * 순천시 서면의 장소만 여덟 개가 나왔고 부산 결과는 한 건도 없었다. 지명에 "부산" 이
 * 들어가야만 제대로 나오는 검색이었다.
 *
 * <p>🔴 같은 꾸러미에 둔다 — {@code uriFor} 는 이 검사를 위해 연 자리이고, 바깥에 열어 둘
 * 이유가 없다.
 */
class OriginSearchRegionTest {

	private KakaoLocalOriginSearchAdapter adapterWith(String rect) {
		OriginSearchProperties properties = new OriginSearchProperties();
		properties.setKakaoBaseUrl("https://dapi.kakao.com");
		properties.setSearchRect(rect);
		return new KakaoLocalOriginSearchAdapter(RestClient.builder(), new ObjectMapper(), properties);
	}

	@Test
	@DisplayName("기본 설정이면 부산 사각형을 실어 부른다")
	void busanRectIsSentByDefault() {
		OriginSearchProperties defaults = new OriginSearchProperties();

		URI uri = adapterWith(defaults.getSearchRect()).uriFor("서면", 8);

		assertThat(uri.toString()).contains("rect=" + defaults.getSearchRect());
		assertThat(defaults.getSearchRect()).isEqualTo("128.75,34.88,129.32,35.39");
	}

	/** 부산 밖을 다루게 되는 날 설정만 비우면 되게 해 뒀다 — 코드에 지역을 안 박는 이유다. */
	@Test
	@DisplayName("사각형을 비우면 제한 없이 부른다")
	void blankRectMeansNoRestriction() {
		URI uri = adapterWith("").uriFor("서면", 8);

		assertThat(uri.toString()).doesNotContain("rect=");
	}

	@Test
	@DisplayName("검색어와 개수는 그대로 실린다")
	void queryAndSizeAreKept() {
		URI uri = adapterWith(null).uriFor("부산역", 5);

		assertThat(uri.toString()).contains("size=5");
		assertThat(uri.getRawQuery()).contains("query=");
	}
}
