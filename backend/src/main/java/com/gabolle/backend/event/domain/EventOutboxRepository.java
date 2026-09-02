package com.gabolle.backend.event.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 저장소 — 인터페이스만 도메인에 둔다. 구현(JPA)은 {@code infra} 에 있다.
 */
public interface EventOutboxRepository {

    /**
     * 이벤트를 적는다.
     *
     * <p>🔴 <b>같은 {@code eventId} 가 이미 있으면 조용히 무시하고 기존 것을 돌려준다.</b>
     * 예외를 던지지 않는다 — 재전송은 오류가 아니라 <b>정상 동작</b>이다.
     * 지하철에서 끊긴 앱은 반드시 다시 보내고, 그때 400 을 받으면 앱이 오류를 띄운다.
     *
     * <p>DB 구현에서는 {@code INSERT ... ON CONFLICT (event_id) DO NOTHING} 이 된다.
     *
     * @return 새로 적혔으면 그 이벤트, 이미 있었으면 기존 이벤트
     */
    OutboxEvent appendIfAbsent(OutboxEvent event);

    /** 이미 받은 이벤트인가. */
    boolean exists(String eventId);

    Optional<OutboxEvent> findById(String eventId);

    /**
     * 아직 안 보낸 것을 오래된 것부터 고른다.
     *
     * <p>DB 구현에서는 {@code WHERE published_at IS NULL ORDER BY received_at LIMIT n} 이고,
     * 🔴 부분 인덱스 {@code (received_at) WHERE published_at IS NULL} 를 탄다.
     * 보낸 행은 인덱스에 안 들어가므로 <b>시간이 지나도 인덱스가 안 커진다.</b>
     */
    List<OutboxEvent> findPending(int limit);

    void save(OutboxEvent event);

    /** 🔴 탈퇴 시 익명화. 삭제가 아니다. */
    int anonymizeByUser(String userId);
}
