package com.gabolle.backend.event.infra;

import com.gabolle.backend.event.domain.EventOutboxRepository;
import com.gabolle.backend.event.domain.OutboxEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 메모리 Outbox — M1 용. PostgreSQL 없이 적재·릴레이 동작을 재현한다.
 *
 * <p>지금 {@code application.properties} 가 DataSource·JPA·Flyway 를 꺼 두었고,
 * {@code resources/db} 는 다른 사람이 점유 중이라 DDL 을 못 만든다.
 * 그래도 규칙은 돌아야 하므로 먼저 이것으로 세운다.
 *
 * <p>🔴 서버를 끄면 사라진다. 시연·테스트 전용이다.
 */
@Repository
public class InMemoryEventOutboxRepository implements EventOutboxRepository {

    private final Map<String, OutboxEvent> events = new ConcurrentHashMap<>();

    @Override
    public OutboxEvent appendIfAbsent(OutboxEvent event) {
        // 🔴 putIfAbsent 는 "없을 때만 넣는다" 를 원자적으로 한다.
        //    DB 의 INSERT ... ON CONFLICT (event_id) DO NOTHING 과 같은 보장이다.
        //    두 요청이 동시에 같은 eventId 를 보내도 하나만 들어간다.
        OutboxEvent existing = events.putIfAbsent(event.eventId(), event);
        return existing != null ? existing : event;
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
        // DB 구현에서는 WHERE published_at IS NULL ORDER BY received_at LIMIT n 이고
        // 부분 인덱스 (received_at) WHERE published_at IS NULL 를 탄다.
        return events.values().stream()
                .filter(OutboxEvent::isPending)
                .sorted(Comparator.comparing(OutboxEvent::receivedAt))
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
                events.put(e.eventId(), e.anonymized());
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
