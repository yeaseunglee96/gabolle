package com.gabolle.backend.itinerary.infra;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code itinerary_run} 읽기 전용. 쓰기는 {@link JpaItineraryRunRepository} 의
 * {@code ON CONFLICT} 문장이 맡는다.
 */
public interface ItineraryRunJpaRepository extends JpaRepository<ItineraryRunJpaEntity, UUID> {
}
