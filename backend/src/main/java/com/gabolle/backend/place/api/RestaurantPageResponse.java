package com.gabolle.backend.place.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 구·동으로 거른 맛집 목록 (S15P21E201-2051).
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
	 * @param certifiedSources 인증한 목록 — {@code BUSAN_FOOD_INFO} · {@code MODEL_RESTAURANT} · {@code TAXI_DRIVER_PICK}.
	 *     낱말은 고정이고 이름표는 화면이 언어별로 단다. 선언 순서로 정렬해 늘 같은 순서다
	 */
	public record RestaurantItem(
			@JsonUnwrapped PlaceSummaryResponse place,
			String gu,
			String dong,
			List<String> certifiedSources) {
	}
}
