package com.gabolle.backend.place.api;

import java.util.UUID;

import com.gabolle.backend.place.domain.Place;

/**
 * 목록 조회 한 줄 — 이름 검색(-462)과 갈래 필터 목록(-473)이 같이 쓴다.
 *
 * @param matchedField 이름 검색에서 어느 이름 칸이 걸렸는가. 갈래 필터 목록에는 "어느 이름이
 *     걸렸는가" 라는 개념 자체가 없어 {@code null} 이다.
 */
public record PlaceSummaryResponse(
		UUID placeId,
		String nameKo,
		String nameEn,
		String category,
		String address,
		Double lat,
		Double lng,
		MatchedField matchedField) {

	/** 이름 검색 결과 한 줄. 어느 이름 칸이 걸렸는지 서비스 계층의 순위 계산이 정해 준다. */
	public static PlaceSummaryResponse of(Place place, MatchedField matchedField) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), matchedField);
	}

	/** 갈래 필터 목록 결과 한 줄. 이름 매칭이 아니라 표식으로 골랐으므로 matchedField 가 없다. */
	public static PlaceSummaryResponse ofFacetMatch(Place place) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), null);
	}

	/**
	 * 이름 검색에서 어느 이름 칸이 걸렸는가. 🔴 이 값 목록은 자바가 정본이다 — {@code place_feature}
	 * 의 {@code featureType} 과 달리 사용자 입력이나 마이그레이션으로 늘어나는 값이 아니라
	 * "한국어냐 영어냐" 라는 이 서비스만의 고정된 두 갈래라서, 여기서는 enum 이 맞다.
	 */
	public enum MatchedField {
		NAME_KO,
		NAME_EN
	}
}
