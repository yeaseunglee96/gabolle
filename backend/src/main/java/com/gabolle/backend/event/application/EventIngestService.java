package com.gabolle.backend.event.application;

import com.gabolle.backend.event.domain.EventOutboxRepository;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.OutboxEvent;
import com.gabolle.backend.event.domain.Producer;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 적재 — 응용 계층 (S15P21E201-352).
 *
 * <p>🔴 이 서비스의 완료 기준은 <b>"중계 서버가 꺼져 있어도 정상 응답한다"</b> 다.
 * 그러려면 여기서 Kafka 를 부르지 않아야 한다. 적기만 하고 끝낸다.
 */
@Service
public class EventIngestService {

    private final EventOutboxRepository repository;
    private final Clock clock;

    public EventIngestService(EventOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 클라이언트가 보낸 이벤트를 적는다.
     *
     * <p>🔴 이미 받은 {@code eventId} 면 <b>조용히 성공으로 응답한다.</b>
     * 재전송은 오류가 아니다 — 앱이 400 을 받으면 사용자에게 오류를 띄운다.
     *
     * @return 새로 적혔으면 true, 이미 있었으면 false (둘 다 성공 응답)
     */
    @Transactional
    public boolean ingestFromClient(String eventId, EventType type, int eventVersion,
                                    String userId, String tripId, String requestId,
                                    Instant occurredAt, Map<String, Object> payload) {

        if (repository.exists(eventId)) {
            return false;
        }

        OutboxEvent event = new OutboxEvent(
                eventId, type, eventVersion, Producer.CLIENT,
                userId, tripId, requestId,
                occurredAt, clock.instant(), payload);

        OutboxEvent stored = repository.appendIfAbsent(event);
        return stored == event;
    }

    /**
     * 서버 비즈니스 로직이 이벤트를 적는다 — <b>Outbox 패턴의 핵심</b> (DR-05).
     *
     * <p>🔴 {@code @Transactional(propagation = REQUIRED)} 가 기본값이므로,
     * 부르는 쪽(예: 좋아요 저장)이 이미 트랜잭션 안이면 <b>그 트랜잭션에 합류한다.</b>
     * 그래서 "좋아요는 저장됐는데 이벤트는 없는" 상태가 생기지 않는다.
     *
     * <p>🔴 <b>여기서 새 트랜잭션을 열면(REQUIRES_NEW) Outbox 패턴이 깨진다.</b>
     * 비즈니스 저장이 롤백돼도 이벤트만 남는다.
     */
    @Transactional
    public void recordFromServer(String eventId, EventType type, int eventVersion,
                                 String userId, String tripId, String requestId,
                                 Map<String, Object> payload) {

        Instant now = clock.instant();
        repository.appendIfAbsent(new OutboxEvent(
                eventId, type, eventVersion, Producer.SERVER,
                userId, tripId, requestId,
                now, now, payload));
    }

    /** 🔴 탈퇴 처리 — 삭제가 아니라 익명화 (NFR-08 재현성). */
    @Transactional
    public int anonymizeUser(String userId) {
        return repository.anonymizeByUser(userId);
    }
}
