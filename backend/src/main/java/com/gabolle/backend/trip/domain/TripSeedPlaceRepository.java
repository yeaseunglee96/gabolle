package com.gabolle.backend.trip.domain;

import java.util.List;

/** 복제 씨앗 저장소. 구현은 DB 만 둔다 — 복제 서비스와 추천 엔진이 둘 다 DB 프로필에서만 뜬다. */
public interface TripSeedPlaceRepository {

    void saveAll(List<TripSeedPlace> seeds);

    /** 원본 순서(sequence ASC)대로. 복제가 아닌 보통 여행이면 비어 있다. */
    List<TripSeedPlace> findByTripId(String tripId);
}
