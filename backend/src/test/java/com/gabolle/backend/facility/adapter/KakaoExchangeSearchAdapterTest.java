package com.gabolle.backend.facility.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.facility.service.ExchangeSearch;

import tools.jackson.databind.ObjectMapper;

/** 카카오 응답에서 환전소만 고르는가 (S15P21E201-2066). 「환전」으로 찾으면 금은방이 섞여 나왔다(2026-10-20 남포동 실측). */
class KakaoExchangeSearchAdapterTest {

	@Test
	void 분류가_외환_환율인_곳만_남긴다() throws Exception {
		String body = """
				{"meta":{"is_end":true},"documents":[
				 {"id":"1","place_name":"머니플러스","category_name":"금융,보험 > 외환,환율 > 환전","x":"129.03","y":"35.09",
				  "road_address_name":"부산 중구 광복로 1","address_name":"부산 중구 창선동","phone":"051-000-0000","place_url":"http://place.map.kakao.com/1","distance":"403"},
				 {"id":"2","place_name":"골드앤와이즈 부산점","category_name":"가정,생활 > 패션 > 보석,귀금속","x":"129.03","y":"35.09","distance":"547"},
				 {"id":"3","place_name":"좌표없음환전","category_name":"금융,보험 > 외환,환율 > 환전","x":"","y":"","distance":"600"},
				 {"id":"4","place_name":"부산환전","category_name":"금융,보험 > 외환,환율 > 환전","x":"129.04","y":"35.10",
				  "road_address_name":"","address_name":"부산 중구 남포동","phone":"","distance":"532"}
				]}""";
		List<ExchangeSearch.Spot> spots = KakaoExchangeSearchAdapter.spotsOf(new ObjectMapper().readTree(body));

		assertThat(spots).extracting(ExchangeSearch.Spot::name).containsExactly("머니플러스", "부산환전");
		assertThat(spots.get(0).address()).isEqualTo("부산 중구 광복로 1");
		assertThat(spots.get(0).tel()).isEqualTo("051-000-0000");
		// 도로명이 비면 지번, 빈 전화는 null
		assertThat(spots.get(1).address()).isEqualTo("부산 중구 남포동");
		assertThat(spots.get(1).tel()).isNull();
	}
}
