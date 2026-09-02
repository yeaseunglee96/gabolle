package com.gabolle.backend.event.infra;

import com.gabolle.backend.event.domain.EventOutboxRepository;
import com.gabolle.backend.event.domain.OutboxEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * 메모리 Outbox — M1 용. PostgreSQL 없이 적재·릴레이 동작을 재현한다.
 *
 * <p>지금 {@code application.properties} 가 DataSource·JPA·Flyway 를 꺼 두었고,
 * {@code resources/db/migration} 은 다른 사람이 점유 중이라 DDL 을 못 만든다.
 * 그래도 규칙은 돌아야 하므로 먼저 이것으로 세운다.
 *
 * <p>🔴 서버를 끄면 사라진다. 시연·테스트 전용이다.
 */
@Repository
public class InMemoryEventOutboxRepository implements EventOutboxRepository {

    private final Map<String, OutboxEvent> events = new ConcurrentHashMap<>();

    /**
     * 들어온 순서 — 🔴 <b>정렬의 보조 키다. 이것이 없으면 순서가 무너진다.</b>
     *
     * <p>테스트가 잡아 준 결함이다. {@code receivedAt} 으로만 정렬했더니
     * 같은 밀리초에 들어온 이벤트 셋의 순서가 {@code [evt_2, evt_3, evt_1]} 로
     * 뒤바뀌었다. 시계를 고정한 테스트에서 드러났지만 <b>실제 DB 에서도 같다</b> —
     * {@code timestamptz} 는 마이크로초 단위라 부하가 걸리면 같은 값이 나온다.
     *
     * <p>Outbox 순서가 뒤바뀌면 Kafka 로 나가는 순서도 뒤바뀐다. 같은 일정에 대한
     * 편집 이벤트가 거꾸로 나가면 분석 쪽이 최종 상태를 잘못 재구성한다.
     *
     * <p>🔴 DB 구현에서는 {@code bigserial} 컬럼이 이 자리를 대신하고,
     * 정렬은 {@code ORDER BY seq} 가 된다. {@code received_at} 만으로 정렬하지 않는다.
     */
    private final Map<String, Long> sequences = new ConcurrentHashMap<>();
    private final AtomicLong nextSequence = new AtomicLong(1);

    @Override
    public OutboxEvent appendIfAbsent(OutboxEvent event) {
        // 🔴 putIfAbsent 는 "없을 때만 넣는다" 를 원자적으로 한다.
        //    DB 의 INSERT ... ON CONFLICT (event_id) DO NOTHING 과 같은 보장이다.
        //    두 요청이 동시에 같은 eventId 를 보내도 하나만 들어간다.
        OutboxEvent existing = events.putIfAbsent(event.eventId(), event);
        if (existing != null) {
            return existing;
        }
        sequences.put(event.eventId(), nextSequence.getAndIncrement());
        return event;
    }

    @Override
    public boolean exists(String eventId) {
        return events.containsKey(eventId);
    }

    @Override
    public Optional<OutboxEvent> findById(String eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        // DB 구현에서는 WHERE published_at IS NULL ORDER BY seq LIMIT n 이고
        // 부분 인덱스 (seq) WHERE published_at IS NULL 를 탄다.
        // 보낸 행은 인덱스에 안 들어가므로 시간이 지나도 인덱스가 안 커진다.
        return events.values().stream()
                .filter(OutboxEvent::isPending)
                .sorted(Comparator.comparingLong(e -> sequences.getOrDefault(e.eventId(), Long.MAX_VALUE)))
                .limit(limit)
                .toList();
    }

    @Override
    public void save(OutboxEvent event) {
        events.put(event.eventId(), event);
    }

    @Override
    public int anonymizeByUser(String userId) {
        int count = 0;
        for (OutboxEvent e : events.values()) {
            if (userId.equals(e.userId())) {
                events.put(e.eventId(), e.anonymized());   // 순번은 그대로 유지된다
                count++;
            }
        }
        return count;
    }

    /** 시연·테스트용 — 지금 몇 건 쌓였나. */
    public long pendingCount() {
        return events.values().stream().filter(OutboxEvent::isPending).count();
    }
}
