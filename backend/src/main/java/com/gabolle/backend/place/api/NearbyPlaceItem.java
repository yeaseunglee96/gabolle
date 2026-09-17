package com.gabolle.backend.place.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.gabolle.backend.place.domain.Place;

/**
 * 근처 조회 결과 한 건. {@code distanceM} 은 정수로 반올림한 미터 값이다.
 *
 * @param hasPhoto 사진이 있는 장소인가 (S15P21E201-894). 화면이 "야경 갈래는 야경 사진이 있는
 *                 곳이 먼저" 같은 순서를 매기는 데 쓴다 — 정렬은 화면이 하고 서버는 사실만 준다.
 *                 🔴 사진 <b>주소</b>는 싣지 않는다. 이 목록에 사진을 실제로 그리려면 출처 표기가
 *                 함께 가야 하는데({@code place.photo_source} 가 그래서 있다) 그 표기를 어디에
 *                 어떻게 둘지가 아직 정해지지 않았다. 주소만 먼저 열면 표기 없이 그리는 화면이
 *                 생기고, 그것은 되돌리기 어려운 쪽으로 틀린다.
 */
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
		 * 사진이 있는가. 🔴 {@code photoUrl} 이 생긴 뒤에도 <b>남겨 둔다</b> — 지금 화면이
		 * 이 칸을 읽고 있고, 칸을 빼면 그 화면이 깨진다. 새 칸은 맨 뒤에 더한다.
		 */
		boolean hasPhoto,

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
		 * 🔴 S15P21E201-1194 — 맨 뒤에 더한 칸이다. 영문 주소. 값이 없으면 키 자체가 빠진다.
		 *
		 * <p>이 목록도 이름은 두 언어로 싣고 주소는 한글만 실었다. 왜 그것이 화면에서 문제가
		 * 되는지는 {@code PlaceSummaryResponse} 의 같은 칸 주석에 있다 — 두 목록이 같은 결함을
		 * 나눠 갖고 있었다.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL) String addressEn) {

	public static NearbyPlaceItem from(Place place, long distanceM) {
		return new NearbyPlaceItem(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), distanceM,
				place.getPhotoUrl() != null && !place.getPhotoUrl().isBlank(),
				place.getPhotoUrl(), place.getPhotoSource(), place.getAddressEn());
	}
}
