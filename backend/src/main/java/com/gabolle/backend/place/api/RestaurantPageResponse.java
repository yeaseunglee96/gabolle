package com.gabolle.backend.place.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 구·동으로 거른 맛집·카페 목록 (S15P21E201-2051, 카페는 S15P21E201-2059).
 *
 * <p>커서를 받지 않는다. 한 동의 맛집은 많아야 수십 곳이라 한 번에 다 주고, {@code hasNext} 가 참이면 {@code limit} 에 잘린 것이다.
 * 순서는 인증한 목록이 많은 곳이 먼저이고(둘 이상이 인증한 집이 먼저 보이게), 같으면 이름 가나다 순이다.
 */
public record RestaurantPageResponse(List<RestaurantItem> items, int limit, boolean hasNext) {

	/**
	 * 한 곳. 장소의 기본 칸({@link PlaceSummaryResponse})은 <b>펼쳐서</b> 싣는다 — 기존 목록 카드가 쓰는 칸 이름과 모양이 그대로라
	 * 앱이 같은 카드 부품을 쓴다. 기본 칸을 여기 따로 베끼지 않은 것은 {@code PlaceSummaryResponse} 에 칸이 생길 때 이 응답이 조용히
	 * 뒤처지지 않게 하려는 것이다.
	 *
	 * @param gu 구·군. 표식이 없으면 {@code null}
	 * @param dong 법정동. 표식이 없으면 {@code null}
	 * @param certifiedSources 소개·인증한 목록 — 공공기관 {@code BUSAN_FOOD_INFO} · {@code MODEL_RESTAURANT} · {@code TAXI_DRIVER_PICK},
	 *     카페 {@code KTO_CAFE}(한국관광공사) · {@code REDTABLE_FEATURED}(레드테이블), 현지인 설문 {@code LOCAL_SURVEY}.
	 *     낱말은 고정이고 이름표는 화면이 언어별로 단다. 선언 순서로 정렬해 늘 같은 순서다
	 * @param broadcastOrAward 레드테이블이 방송·수상 이력도 적어 둔 곳이면 {@code true}({@code REDTABLE_FEATURED} 표식의 값에서
	 *     읽는다). 아니면 칸째 없다 — 「아니다」와 「모른다」를 한 값으로 합치지 않으려고 거짓을 내보내지 않는다
	 */
	public record RestaurantItem(
			@JsonUnwrapped PlaceSummaryResponse place,
			String gu,
			String dong,
			List<String> certifiedSources,
			@JsonInclude(JsonInclude.Include.NON_NULL) Boolean broadcastOrAward) {
	}
}
