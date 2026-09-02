package com.gabolle.backend.event.application.port;

import com.gabolle.backend.event.domain.OutboxEvent;

/**
 * 이벤트를 밖으로 내보내는 구멍.
 *
 * <p>🔴 M1·M2 에는 Kafka 가 없다. 개발계획서 4.4 가 명시한다 —
 * "M1·M2 에서 Kafka·Spark 기동. <b>요청 경로가 이것들에 의존하면 안 된다.</b>"
 *
 * <p>그래서 인터페이스로 뚫어 두고 M1 은 아무것도 안 하는 구현을 꽂는다.
 * M3 에 Kafka 어댑터로 갈아 끼우면 {@code domain}·{@code application} 은 안 고친다.
 */
public interface EventPublisherPort {

    /**
     * 한 건 보낸다.
     *
     * <p>🔴 받는 쪽이 중복을 걸러낼 수 있게 {@code eventId} 를 메시지 키로 쓴다.
     * 릴레이가 "보냈는데 보냈다고 적기 전에" 죽으면 다시 보내게 되는데,
     * 그때 중복이 쌓이지 않아야 한다.
     *
     * @throws EventPublishException 전송 실패 — 릴레이가 잡아서 재시도 대상으로 남긴다
     */
    void publish(OutboxEvent event);

    /** 지금 내보낼 수 있는 상태인가. false 면 릴레이가 조용히 건너뛴다. */
    boolean isAvailable();

    class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
