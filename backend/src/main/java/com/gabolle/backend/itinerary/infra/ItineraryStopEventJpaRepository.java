package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code itinerary_stop_event} 읽고 쌓기. 고치는 메서드를 두지 않는다 — 이 표는 쌓기만 한다.
 */
public interface ItineraryStopEventJpaRepository extends JpaRepository<ItineraryStopEventJpaEntity, UUID> {

	/** 시간 순. 사건은 순서가 곧 뜻이다. */
	List<ItineraryStopEventJpaEntity> findByItineraryIdOrderByOccurredAtAsc(UUID itineraryId);
}
