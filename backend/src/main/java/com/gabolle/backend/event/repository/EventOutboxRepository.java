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

	/**
	 * 위와 같되, <b>재시도 상한을 넘긴 행은 뺀다</b> (S15P21E201-561).
	 *
	 * <p>🔴 이 조건이 없으면 <b>독약 메시지</b>(어떤 이유로든 영원히 실패하는 한 건)가 그 뒤의
	 * 모든 이벤트를 영구히 막는다. 릴레이는 순서를 지키려고 실패한 자리에서 멈추기 때문에,
	 * 맨 앞 한 건이 계속 실패하면 <b>뒤엣것은 한 건도 못 나간다.</b> 상한을 넘긴 행이 조회에서
	 * 빠지면 그 다음 건이 맨 앞이 되어 줄이 다시 흐른다.
	 *
	 * <p>빠진 행을 <b>지우지 않는다.</b> {@code publish_status = FAILED} 로 남고 {@code last_error}
	 * 에 이유가 적혀 있다. 사람이 원인을 고친 뒤 {@code publish_attempts} 를 0 으로 되돌리면
	 * 다시 줄에 선다 — 되살릴 수 없게 지우는 것과 다르다.
	 *
	 * @param publishAttempts 이 값 <b>미만</b>인 행만 고른다 ({@code max-attempts} 를 그대로 넘긴다)
	 */
	List<EventOutbox> findByPublishedAtIsNullAndPublishAttemptsLessThanOrderBySeqAsc(int publishAttempts,
			Pageable pageable);

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

	/**
	 * 재시도 상한을 넘겨 <b>줄에서 빠진</b> 건수 (S15P21E201-561).
	 *
	 * <p>{@link #countByPublishedAtIsNull()} 과 따로 세는 이유. 그 값은 "아직 안 나간 것" 전부라
	 * 곧 나갈 것과 <b>사람이 손대기 전엔 영영 안 나갈 것</b>을 구별하지 못한다. 섞어 두면 둘 다
	 * 그냥 "밀려 있다" 로 보이고, 고쳐야 할 것이 밀린 것 뒤에 숨는다.
	 *
	 * @param publishAttempts 이 값 <b>이상</b>인 행을 센다 ({@code max-attempts} 를 그대로 넘긴다)
	 */
	long countByPublishedAtIsNullAndPublishAttemptsGreaterThanEqual(int publishAttempts);

	/** 그 기간에 실제로 나간 건수. */
	long countByPublishedAtBetween(OffsetDateTime from, OffsetDateTime to);

	/** {@code e.eventType} · {@code COUNT(e)} 그룹 결과를 받는 프로젝션. */
	interface EventTypeCount {
		String getEventType();

		long getCount();
	}
}
