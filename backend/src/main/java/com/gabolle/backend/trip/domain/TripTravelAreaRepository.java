package com.gabolle.backend.trip.domain;

import java.util.List;

/** 여행 범위 저장소. 구현은 DB 만 둔다 — 쓰는 쪽과 읽는 쪽이 둘 다 DB 프로필에서만 뜬다. */
public interface TripTravelAreaRepository {

    void saveAll(String tripId, List<TravelArea> areas);

    /** 고른 순서대로. 범위를 안 고른 여행이면 비어 있다. */
    List<TravelArea> findByTripId(String tripId);
}
