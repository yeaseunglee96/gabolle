package com.gabolle.backend.place.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

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
		MatchedField matchedField,

		/**
		 * 🔴 S15P21E201-1120 — 맨 뒤에 더한 칸이다. 대표 사진 주소.
		 *
		 * <p>홈 「부산 둘러보기」 카드와 탐색 목록이 사진을 못 그리던 이유가 이 칸이 없어서다.
		 * 사진이 없어서가 아니다 — 운영에 사진이 붙은 장소가 72곳 있는데(2026-09-16 실측,
		 * 문화·사찰 42 · 도심 16 · 자연 9 · 바다 1) 목록 응답이 그 주소를 안 실었다.
		 *
		 * <p>값이 없으면 키 자체가 빠진다. 앱은 모르는 키를 무시하므로 맨 뒤에 더하는 것은
		 * 지금 화면을 깨지 않는다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoUrl,
		/**
		 * 🔴 {@code photoUrl} 과 <b>반드시 짝이다.</b> {@code Place.photoUrl} 의 주석이
		 * "photoUrl 이 있으면 이것도 있어야 한다" 고 못박아 뒀다 — 관광공사 공공누리 사진이라
		 * <b>출처 표기 없이 내보내면 라이선스 문제</b>가 된다. 사진만 주고 이 칸을 빼면 화면이
		 * 표기할 방법이 없어진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoSource,

		/**
		 * 🔴 S15P21E201-1194 — 맨 뒤에 더한 칸이다. 영문 주소.
		 *
		 * <p>이 목록은 이름을 {@code nameKo}·{@code nameEn} <b>두 언어로</b> 싣는데 주소는 한글
		 * 하나만 실었다. 그래서 영어 화면이 <b>영어 이름 바로 밑에 한글 주소</b>를 그렸다 —
		 * 탐색 목록과 숙소 검색이 그 자리다. 숙소 검색은 주소가 <b>없을 때의 안내문만 영어로
		 * 번역</b>돼 있어서 <b>없으면 영어, 있으면 한글</b>이었다. 어느 쪽 설계로도 설명이 안 된다.
		 *
		 * <p>새 결정이 아니다. {@code PlaceDetailResponse} 가 이미 정해 둔 것을 목록이 안 지키고
		 * 있던 것이다 — <i>「언어 선택은 칸을 더하는 것이지 기존 칸을 바꾸는 것이 아니다」</i>.
		 *
		 * <p>값이 없으면 키 자체가 빠진다({@code photoUrl}·{@code photoSource} 와 같은 규칙).
		 * 새 조회는 없다 — 아래 팩토리가 이미 {@code Place} 를 통째로 받는다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn) {

	/** 이름 검색 결과 한 줄. 어느 이름 칸이 걸렸는지 서비스 계층의 순위 계산이 정해 준다. */
	public static PlaceSummaryResponse of(Place place, MatchedField matchedField) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), matchedField,
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn());
	}

	/** 갈래 필터 목록 결과 한 줄. 이름 매칭이 아니라 표식으로 골랐으므로 matchedField 가 없다. */
	public static PlaceSummaryResponse ofFacetMatch(Place place) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), null,
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn());
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
