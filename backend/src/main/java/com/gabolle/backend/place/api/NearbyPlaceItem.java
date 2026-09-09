package com.gabolle.backend.place.api;

import java.util.UUID;

import com.gabolle.backend.place.domain.Place;

/**
 * 근처 조회 결과 한 건. {@code distanceM} 은 정수로 반올림한 미터 값이다.
 */
public record NearbyPlaceItem(
		UUID placeId,
		String nameKo,
		String nameEn,
		String category,
		String address,
		double lat,
		double lng,
		long distanceM) {

	public static NearbyPlaceItem from(Place place, long distanceM) {
		return new NearbyPlaceItem(place.getPlaceId(), place.getNameKo(), place.getNameEn(),
				place.getCategory(), place.getAddress(), place.getLat(), place.getLng(), distanceM);
	}
}
