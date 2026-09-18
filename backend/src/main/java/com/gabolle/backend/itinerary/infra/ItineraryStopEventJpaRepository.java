package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code itinerary_stop_event} 읽고 쌓기 — S15P21E201-1325.
 *
 * <p>고치는 메서드를 두지 않는다. 이 표는 <b>쌓기만 한다</b> — 기록을 고치면 「그때 무슨
 * 일이 있었나」가 조용히 달라진다.
 */
public interface ItineraryStopEventJpaRepository extends JpaRepository<ItineraryStopEventJpaEntity, UUID> {

	/** 시간 순이다. 「무슨 일이 있었나」는 순서가 곧 뜻이다. */
	List<ItineraryStopEventJpaEntity> findByItineraryIdOrderByOccurredAtAsc(UUID itineraryId);
}
