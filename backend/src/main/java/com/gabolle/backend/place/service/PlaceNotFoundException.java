package com.gabolle.backend.place.service;

import java.util.UUID;

/**
 * 그 아이디의 장소가 없다 — 404 로 나간다. 장소는 공개 카탈로그라 여행과 달리 존재 자체를
 * 감추지 않는다.
 */
public class PlaceNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final UUID placeId;

	public PlaceNotFoundException(UUID placeId) {
		super("장소를 찾을 수 없습니다: " + placeId);
		this.placeId = placeId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}
}
