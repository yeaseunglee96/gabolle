package com.gabolle.backend.event.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.OutboxPublishStatus;

/**
 * event 패키지 밖에서 직접 부르지 않는다. 다른 도메인은 {@code OutboxService} 를 통해서만
 * 이벤트를 적는다 — 그래야 envelope 규칙과 멱등 처리가 한 군데에만 있다.
 */
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

	List<EventOutbox> findByEventTypeOrderByOccurredAtAsc(String eventType);

	List<EventOutbox> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);

	long countByEventType(String eventType);

	/**
	 * 아직 안 보낸 것을 받은 순서대로 고른다.
	 *
	 * <p>판정 기준은 {@code publishStatus} 가 아니라 {@code publishedAt} 이다. FAILED 도
	 * "안 보냈다" 에 포함된다 — 상태로 고르면 한 번 실패한 이벤트가 영영 안 나간다.
	 *
	 * <p>정렬은 {@code seq} 여야 한다. {@code occurredAt} 은 같은 시각에 적힌 두 건의 순서를
	 * 확정하지 못하고, {@code eventId} 를 동점 기준으로 더해도 삽입 순서를 되살리지는 못한다.
	 */
	List<EventOutbox> findByPublishedAtIsNullOrderBySeqAsc(Pageable pageable);

	// ── 지표 조회 ───────────────────────────────────────────────────────

	/**
	 * 시간대별 종류별 건수. {@code occurredAt} 기준이다 — 서버가 늦게 받은 것과 실제로 늦게
	 * 일어난 것은 다른 질문이다.
	 */
	@Query("SELECT e.eventType AS eventType, COUNT(e) AS count FROM EventOutbox e "
			+ "WHERE e.occurredAt >= :from AND e.occurredAt < :to GROUP BY e.eventType")
	List<EventTypeCount> countByEventTypeBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	/** 지금 이 순간 아직 안 보낸 건수 — 기간과 무관한 실시간 값이다. */
	long countByPublishedAtIsNull();

	/** 지금까지 재시도해도 계속 실패 중인 건수. */
	long countByPublishStatus(OutboxPublishStatus publishStatus);

	/** 그 기간에 실제로 나간 건수. */
	long countByPublishedAtBetween(OffsetDateTime from, OffsetDateTime to);

	/** {@code e.eventType} · {@code COUNT(e)} 그룹 결과를 받는 프로젝션. */
	interface EventTypeCount {
		String getEventType();

		long getCount();
	}
}
