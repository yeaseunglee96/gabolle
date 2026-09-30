package com.gabolle.backend.place.api;

import java.util.Map;
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
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoSubject photoSubject,

		/**
		 * 사진의 라이선스 — 이름·주소·원본 파일 페이지(S15P21E201-1606). 위키미디어 사진(CC BY 등)은
		 * 출처 문구와 함께 이것을 보여야 쓸 수 있다. 이 칸으로 받은 것이 없으면 키가 빠진다 —
		 * 공공누리 사진은 지금 전부 그렇고, 그 표기는 {@code photoSource} 가 진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoLicense photoLicense,
		/**
		 * 장소 이름의 일본어·중국어(간체·번체) — 관광공사가 번역해 둔 곳만 있다(V20260930130000, S15P21E201-1859).
		 * 키는 앱의 언어 코드({@code ja} · {@code zh-Hans} · {@code zh-Hant}), 없는 언어는 빠지고 다 없으면 칸째 빠진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localNames,
		/**
		 * 주소의 일본어·중국어(간체·번체) — 관광공사가 번역해 둔 곳만 있다(V20260930180000, S15P21E201-1876).
		 * 키는 {@code localNames} 와 같고, 없는 언어는 빠지고 다 없으면 칸째 빠진다. 화면은 없으면 영문 → 한국어 주소로 물러선다.
		 */
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localAddresses) {

	/** 이름 검색 결과 한 줄. 어느 이름 칸이 걸렸는지 서비스 계층의 순위 계산이 정해 준다. */
	public static PlaceSummaryResponse of(Place place, MatchedField matchedField) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), matchedField,
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn(), place.getPhotoSubject(),
				place.getPhotoLicense(), place.localNames(), place.localAddresses());
	}

	/** 갈래 필터 목록 결과 한 줄. 이름 매칭이 아니라 표식으로 골랐으므로 matchedField 가 없다. */
	public static PlaceSummaryResponse ofFacetMatch(Place place) {
		return new PlaceSummaryResponse(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), null,
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn(), place.getPhotoSubject(),
				place.getPhotoLicense(), place.localNames(), place.localAddresses());
	}

	/**
	 * 이름 검색에서 어느 이름 칸이 걸렸는가. 이 값 목록은 자바가 정본이다 — 마이그레이션으로
	 * 늘어나는 값이 아니라 이 서비스만의 고정된 갈래다. {@code NAME_LOCAL} 은 관광공사 일본어·중국어
	 * 공식 이름(간체·번체) 중 하나가 걸렸다는 뜻이다(S15P21E201-1875).
	 */
	public enum MatchedField {
		NAME_KO,
		NAME_EN,
		NAME_LOCAL
	}
}
