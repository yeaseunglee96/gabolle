package com.gabolle.backend.place.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.place.domain.Place;

/**
 * 목록 조회 한 줄 — 이름 검색과 갈래 필터 목록이 같이 쓴다.
 *
 * <p>{@code matchedField} 는 갈래 필터 목록에서 {@code null} 이다 — 그쪽에는 "어느 이름이
 * 걸렸는가" 라는 개념이 없다. {@code null} 인 칸은 키 자체가 빠진다.
 */
public record PlaceSummaryResponse(
		UUID placeId,
		String nameKo,
		String nameEn,
		String category,
		String address,
		Double lat,
		Double lng,
		MatchedField matchedField,

		@JsonInclude(JsonInclude.Include.NON_NULL) String photoUrl,
		/**
		 * {@code photoUrl} 과 반드시 짝이다. 관광공사 공공누리 사진이라 출처 표기 없이 내보내면
		 * 라이선스 문제가 된다 — 사진만 주고 이 칸을 빼면 화면이 표기할 방법이 없다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoSource,

		/**
		 * 영문 주소. 이름을 {@code nameKo}·{@code nameEn} 두 언어로 싣는데 주소가 하나뿐이면
		 * 영어 화면이 영어 이름 밑에 한글 주소를 그리게 된다. 언어 선택은 칸을 더하는 것이지
		 * 기존 칸을 바꾸는 것이 아니다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn,

		/**
		 * 그 사진이 무엇을 찍은 것인가. {@code SELF} 는 이 장소를 직접 찍은 것, {@code VENUE} 는
		 * 이 장소가 들어 있는 곳을 찍은 것이다. 부산 축제 사진 35건 중 축제 자체를 찍은 것은
		 * 1건뿐이라, 이 칸이 없으면 화면이 주변 시설 사진을 이 장소 사진처럼 그린다.
		 * 화면은 {@code VENUE} 일 때만 뱃지를 띄운다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoSubject photoSubject) {

	/** 이름 검색 결과 한 줄. 어느 이름 칸이 걸렸는지 서비스 계층의 순위 계산이 정해 준다. */
	public static PlaceSummaryResponse of(Place place, MatchedField matchedField) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), matchedField,
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn(), place.getPhotoSubject());
	}

	/** 갈래 필터 목록 결과 한 줄. 이름 매칭이 아니라 표식으로 골랐으므로 matchedField 가 없다. */
	public static PlaceSummaryResponse ofFacetMatch(Place place) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), null,
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn(), place.getPhotoSubject());
	}

	/**
	 * 이름 검색에서 어느 이름 칸이 걸렸는가. 이 값 목록은 자바가 정본이다 — 마이그레이션으로
	 * 늘어나는 값이 아니라 이 서비스만의 고정된 두 갈래다.
	 */
	public enum MatchedField {
		NAME_KO,
		NAME_EN
	}
}
