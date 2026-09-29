package com.gabolle.backend.place.api;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.place.domain.Place;

/** 근처 조회 결과 한 건. {@code distanceM} 은 정수로 반올림한 미터 값이다. */
public record NearbyPlaceItem(
		UUID placeId,
		String nameKo,
		String nameEn,
		String category,
		String address,
		double lat,
		double lng,
		long distanceM,
		/**
		 * 사진이 있는가. 화면이 "사진 있는 곳이 먼저" 같은 순서를 매기는 데 쓴다 — 정렬은 화면이
		 * 하고 서버는 사실만 준다. {@code photoUrl} 이 생긴 뒤에도 남겨 둔다 — 지금 화면이 이 칸을
		 * 읽고 있어 빼면 깨진다.
		 */
		boolean hasPhoto,

		/** 대표 사진 주소. 값이 없으면 키 자체가 빠진다. */
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoUrl,
		/**
		 * {@code photoUrl} 과 반드시 짝이다 — 관광공사 공공누리 사진이라 출처 표기 없이 내보내면
		 * 라이선스 문제가 된다. 값이 없으면 키가 빠진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String photoSource,

		/** 영문 주소. 값이 없으면 키 자체가 빠진다. */
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn,

		/**
		 * 그 사진이 무엇을 찍은 것인가 ({@code SELF} = 이 장소, {@code VENUE} = 이 장소가 들어 있는
		 * 곳). 이 칸이 없으면 화면은 주변 시설 사진을 이 장소 사진처럼 그린다. 값이 없으면 키가 빠진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoSubject photoSubject,

		/** 사진의 라이선스(S15P21E201-1606). {@link PlaceSummaryResponse#photoLicense()} 와 같다. 없으면 키가 빠진다. */
		@JsonInclude(JsonInclude.Include.NON_NULL) Place.PhotoLicense photoLicense,
		/**
		 * 장소 이름의 일본어·중국어(간체·번체) — 관광공사가 번역해 둔 곳만 있다(V20260930130000, S15P21E201-1859).
		 * 키는 앱의 언어 코드({@code ja} · {@code zh-Hans} · {@code zh-Hant}), 없는 언어는 빠지고 다 없으면 칸째 빠진다.
		 */
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> localNames) {

	public static NearbyPlaceItem from(Place place, long distanceM) {
		return new NearbyPlaceItem(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), distanceM,
				place.getPhotoUrl() != null && !place.getPhotoUrl().isBlank(),
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn(), place.getPhotoSubject(),
				place.getPhotoLicense(), place.localNames());
	}
}
