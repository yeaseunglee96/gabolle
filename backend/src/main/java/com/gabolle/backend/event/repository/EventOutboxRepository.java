package com.gabolle.backend.event.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 🔴 이 인터페이스는 event 패키지 밖에서 직접 부르지 않는다. 다른 도메인은
 * {@code OutboxService} 를 통해서만 이벤트를 적는다 — 그래야 envelope 규칙과
 * 멱등 처리가 한 군데에만 있다.
 */
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

	List<EventOutbox> findByEventTypeOrderByOccurredAtAsc(String eventType);

	List<EventOutbox> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);

	long countByEventType(String eventType);
}
