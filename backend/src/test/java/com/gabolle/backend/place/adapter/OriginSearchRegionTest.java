package com.gabolle.backend.place.adapter;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출발지 검색이 부산만 보는가. 이 검사가 어댑터와 같은 꾸러미에 있는 것은 {@code uriFor} 가
 * 이 검사를 위해 연 자리이기 때문이다 — 바깥에 열어 둘 이유가 없다.
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
