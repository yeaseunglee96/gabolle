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
 * 🔴 이 인터페이스는 event 패키지 밖에서 직접 부르지 않는다. 다른 도메인은
 * {@code OutboxService} 를 통해서만 이벤트를 적는다 — 그래야 envelope 규칙과
 * 멱등 처리가 한 군데에만 있다.
 */
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

	List<EventOutbox> findByEventTypeOrderByOccurredAtAsc(String eventType);

	List<EventOutbox> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);

	long countByEventType(String eventType);

	/**
	 * 아직 안 보낸 것을 <b>받은 순서대로</b> 고른다 — 릴레이(S15P21E201-354)가 쓴다.
	 *
	 * <p>🔴 <b>{@code publishStatus} 가 아니라 {@code publishedAt} 이 판정 기준이다.</b>
	 * FAILED 는 "안 보냈다" 에 포함된다 — 실패한 것은 다시 보내야 하기 때문이다.
	 * 상태로 고르면 한 번 실패한 이벤트가 영영 안 나간다.
	 *
	 * <p>🔴 2026-09-03 — {@code ...OrderByOccurredAtAscEventIdAsc} 에서 이 메서드로 바꿨다.
	 * {@code occurredAt} 만으로는 같은 시각에 적힌 두 건의 순서가 확정되지 않는다(메모리
	 * 구현에서 고정 Clock 으로 재현 — {@code [evt_2, evt_3, evt_1]}). {@code eventId} 를
	 * 동점 기준으로 더해도 <b>재현 가능은 해지지만 삽입 순서를 되살리지는 못한다.</b>
	 *
	 * <p>{@code seq}(고지혁 님이 S15P21E201-352-event-outbox-join-axes 에서 붙인
	 * {@code BIGSERIAL})가 진짜 답이다 — 행이 적힌 순서 그 자체라 동점이 생길 수 없다.
	 * 색인({@code ix_event_outbox_pending ON event_outbox (seq) WHERE published_at IS NULL})도
	 * 이미 그 마이그레이션에 있다. <b>컬럼·색인만으로는 안 고쳐진다</b> — 이 조회가
	 * 실제로 {@code seq} 를 봐야 한다. 그게 이 메서드다.
	 */
	List<EventOutbox> findByPublishedAtIsNullOrderBySeqAsc(Pageable pageable);

	// ── 지표 조회 (S15P21E201-160) ──────────────────────────────────────

	/**
	 * 시간대별 종류별 건수 — {@code GET /api/v1/admin/analytics/kpis} 의 근거.
	 *
	 * <p>🔴 {@code occurredAt} 기준이다({@code receivedAt} 이 아니다). "언제 일어난 일인가" 를
	 * 센다 — 서버가 늦게 받은 것과 실제로 늦게 일어난 것은 다른 질문이다.
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
