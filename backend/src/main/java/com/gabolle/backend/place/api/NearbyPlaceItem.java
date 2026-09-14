package com.gabolle.backend.place.api;

import java.util.UUID;

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
		boolean hasPhoto) {

	public static NearbyPlaceItem from(Place place, long distanceM) {
		return new NearbyPlaceItem(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), distanceM,
				place.getPhotoUrl() != null && !place.getPhotoUrl().isBlank());
	}
}
