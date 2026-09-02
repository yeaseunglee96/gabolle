package com.gabolle.backend.event.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 아직 보내지 않은 이벤트 한 건 — Outbox 패턴의 실체 (DR-01 · DR-05).
 *
 * <h2>Outbox 가 왜 필요한가</h2>
 * 사용자가 좋아요를 누르면 두 가지가 일어나야 한다 — ① DB 에 좋아요 저장
 * ② 분석 시스템(Kafka)에 이벤트 전송. <b>①과 ②는 서로 다른 시스템이라
 * 동시에 성공을 보장할 수 없다.</b>
 *
 * <p>① 성공 · ② 실패 → 좋아요는 저장됐는데 분석에는 안 잡힌다. 추천이 학습을 못 한다.<br>
 * ② 성공 · ① 실패 → 분석에는 있는데 DB 엔 없다. 유령 데이터.
 *
 * <p>그래서 <b>"보낼 것" 을 같은 DB 에 먼저 적는다.</b> 같은 DB 안이면 하나의
 * 트랜잭션으로 묶을 수 있다. 전송은 나중에 별도 릴레이가 한다.
 *
 * <h2>왜 발생 시각과 수신 시각을 둘 다 두는가</h2>
 * S15P21E201-542 3장이 {@code occurred_at} 과 {@code received_at} 을 모두 요구한다.
 * 지하철에서 끊긴 앱이 30분 뒤에 이벤트를 몰아 보내면 두 시각이 크게 벌어진다.
 * 하나만 두면 <b>"언제 일어난 일인가" 와 "언제 받았나" 를 구분할 수 없다.</b>
 */
public class OutboxEvent {

    /** 🔴 멱등 키. 재전송해도 중복이 안 들어온다. PK 로 쓴다. */
    private final String eventId;

    private final EventType eventType;
    private final int eventVersion;
    private final Producer producer;

    /**
     * 🔴 외래키를 걸지 않는다.
     *
     * <p>탈퇴 정책이 "계정은 삭제, 이벤트는 익명화" 다. FK 가 있으면 CASCADE 로
     * 이벤트가 같이 지워지거나 RESTRICT 로 계정 삭제가 막힌다. 둘 다 정책 위반이다.
     * 익명화는 {@code UPDATE ... SET user_id = NULL} 한 줄이어야 한다.
     */
    private final String userId;

    private final String tripId;

    /** 🔴 노출↔행동을 잇는 축. 없으면 분석용으로 승인되지 않는다 (API-07). */
    private final String requestId;

    /** 실제로 일어난 시각. 클라이언트가 알려준다. */
    private final Instant occurredAt;

    /** 서버가 받은 시각. 서버가 찍는다. */
    private final Instant receivedAt;

    /** 종류마다 다른 고유 필드. 공통 7개는 위 칸이고 나머지는 여기. */
    private final Map<String, Object> payload;

    // ── 발행 상태 ────────────────────────────────────────────────
    /** {@code null} 이면 아직 안 보냈다. M1·M2 동안은 계속 null 로 쌓인다. */
    private Instant publishedAt;
    private int attempts;
    private String lastError;

    public OutboxEvent(String eventId, EventType eventType, int eventVersion, Producer producer,
                       String userId, String tripId, String requestId,
                       Instant occurredAt, Instant receivedAt, Map<String, Object> payload) {

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 는 멱등 키다. 비울 수 없다");
        }
        if (requestId == null || requestId.isBlank()) {
            // API-07 — requestId 로 연결되지 않으면 분석용 정상 데이터로 승인하지 않는다.
            throw new IllegalArgumentException(
                    "requestId 가 없으면 노출과 행동을 이을 수 없다 (API-07): " + eventType);
        }
        if (!eventType.allowsProducer(producer)) {
            // DR-13 — 생산 책임이 뒤바뀌면 신뢰할 수 없는 값이 들어온다.
            throw new IllegalArgumentException(
                    eventType + " 은 " + eventType.expectedProducer() + " 가 만들어야 한다 (받은 값: " + producer + ")");
        }
        if (occurredAt == null || receivedAt == null) {
            throw new IllegalArgumentException("occurredAt 과 receivedAt 은 둘 다 필요하다");
        }
        if (occurredAt.isAfter(receivedAt)) {
            // 발생이 수신보다 뒤일 수는 없다. 기기 시계가 틀렸거나 조작된 값이다.
            throw new IllegalArgumentException(
                    "occurredAt(" + occurredAt + ") 이 receivedAt(" + receivedAt + ") 보다 뒤다");
        }

        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.producer = producer;
        this.userId = userId;
        this.tripId = tripId;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.attempts = 0;
    }

    /** 아직 안 보냈는가. 릴레이가 이걸로 고른다. */
    public boolean isPending() {
        return publishedAt == null;
    }

    /**
     * 전송 성공.
     *
     * <p>🔴 이미 보낸 것을 다시 성공 처리하지 않는다. 릴레이가 두 번 돌아도
     * 발행 시각이 덮어써지면 "언제 보냈나" 가 흐려진다.
     */
    public void markPublished(Instant at) {
        if (publishedAt != null) {
            return;
        }
        this.publishedAt = at;
        this.lastError = null;
    }

    /**
     * 전송 실패.
     *
     * <p>🔴 실패한 것을 표에서 지우지 않는다. 다음 차례에 다시 시도한다 —
     * 행동 기록은 나중에 다시 물어볼 수 없는 종류의 데이터다.
     */
    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error;
    }

    /** 🔴 탈퇴 시 익명화. 삭제가 아니다 — 지우면 과거 평가를 재현할 수 없다 (NFR-08). */
    public OutboxEvent anonymized() {
        OutboxEvent copy = new OutboxEvent(eventId, eventType, eventVersion, producer,
                null, tripId, requestId, occurredAt, receivedAt, payload);
        copy.publishedAt = this.publishedAt;
        copy.attempts = this.attempts;
        copy.lastError = this.lastError;
        return copy;
    }

    public String eventId()          { return eventId; }
    public EventType eventType()     { return eventType; }
    public int eventVersion()        { return eventVersion; }
    public Producer producer()       { return producer; }
    public String userId()           { return userId; }
    public String tripId()           { return tripId; }
    public String requestId()        { return requestId; }
    public Instant occurredAt()      { return occurredAt; }
    public Instant receivedAt()      { return receivedAt; }
    public Map<String, Object> payload() { return payload; }
    public Instant publishedAt()     { return publishedAt; }
    public int attempts()            { return attempts; }
    public String lastError()        { return lastError; }
}
