package com.gabolle.backend.trip.domain;

/**
 * 취향·제약이 계정 기본값인가, 이번 여행 전용인가. 구분이 없으면 이번 여행에서만 고친 값이
 * 계정 기본값을 덮어쓴다.
 *
 * <p>여행 생성이 만드는 스냅샷·제약은 항상 {@link #TRIP} 이다. {@link #USER} 는 계정 취향
 * 설정 흐름에서만 만든다.
 */
public enum PersonalizationScope {
    /** 계정 기본값. trip_id 가 없다. */
    USER,
    /** 이번 여행 전용. trip_id 가 있다. */
    TRIP
}
