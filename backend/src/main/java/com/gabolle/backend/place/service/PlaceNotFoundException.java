package com.gabolle.backend.place.service;

import java.util.UUID;

/**
 * 그 아이디의 장소가 없다 — 404 로 나간다.
 *
 * <p>장소는 공개 카탈로그라 "없다" 와 "권한이 없다" 를 구분해 숨길 것이 없다. 여행처럼 존재
 * 자체를 감춰야 하는 자원과 다르다.
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
