package com.gabolle.backend.event.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

	/**
	 * 아직 안 보낸 것을 오래된 순으로 고른다 — 릴레이(S15P21E201-354)가 쓴다.
	 *
	 * <p>🔴 <b>{@code publishStatus} 가 아니라 {@code publishedAt} 이 판정 기준이다.</b>
	 * FAILED 는 "안 보냈다" 에 포함된다 — 실패한 것은 다시 보내야 하기 때문이다.
	 * 상태로 고르면 한 번 실패한 이벤트가 영영 안 나간다.
	 *
	 * <p>🔴 <b>{@code occurredAt} 만으로는 순서가 확정되지 않는다.</b> 같은 시각에 적힌
	 * 두 건의 앞뒤가 실행할 때마다 달라진다(메모리 구현에서 고정 Clock 으로 재현했다 —
	 * {@code [evt_2, evt_3, evt_1]}). 그래서 {@code eventId} 를 동점 처리 기준으로 함께 둔다.
	 * 이건 <b>재현 가능하게 만드는 것이지 삽입 순서를 되살리는 것은 아니다.</b>
	 *
	 * <p>진짜 답은 표에 삽입 순서 컬럼을 두는 것이다 — {@code seq BIGSERIAL} 을 붙이고
	 * {@code ORDER BY seq}, 색인은 {@code WHERE published_at IS NULL} 부분 색인.
	 * 그 {@code ALTER TABLE} 은 고지혁 님(S15P21E201-554) 자리라 여기서 하지 않는다.
	 * <b>컬럼이 생기면 이 메서드를 {@code ...OrderBySeqAsc} 로 바꾼다.</b>
	 */
	List<EventOutbox> findByPublishedAtIsNullOrderByOccurredAtAscEventIdAsc(Pageable pageable);
}
