package com.gabolle.backend.itinerary.infra;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code itinerary_run} 읽기 — S15P21E201-1325.
 *
 * <p>쓰기는 {@link JpaItineraryRunRepository} 의 {@code ON CONFLICT} 문장이 맡는다.
 * 여기는 「지금 상태가 무엇인가」를 한 줄 읽는 데만 쓴다.
 */
public interface ItineraryRunJpaRepository extends JpaRepository<ItineraryRunJpaEntity, UUID> {
}
