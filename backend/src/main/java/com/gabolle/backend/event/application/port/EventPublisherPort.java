package com.gabolle.backend.event.application.port;

import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 이벤트를 밖으로 내보내는 구멍. 요청 경로가 브로커에 의존하면 안 되므로 인터페이스로 뚫어
 * 두고 지금은 아무것도 안 하는 구현을 꽂는다 — 어댑터를 갈아 끼우면 나머지는 안 고친다.
 */
public interface EventPublisherPort {

    /**
     * 한 건 보낸다. 받는 쪽이 중복을 걸러낼 수 있게 {@code eventId} 를 메시지 키로 쓴다 —
     * 릴레이가 보낸 뒤 적기 전에 죽으면 같은 것을 다시 보낸다.
     *
     * @throws EventPublishException 전송 실패 — 릴레이가 잡아서 재시도 대상으로 남긴다
     */
    void publish(EventOutbox event);

    /** 지금 내보낼 수 있는 상태인가. false 면 릴레이가 조용히 건너뛴다. */
    boolean isAvailable();

    class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
