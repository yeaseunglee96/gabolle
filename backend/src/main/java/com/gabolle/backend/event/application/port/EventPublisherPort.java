package com.gabolle.backend.event.application.port;

import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 이벤트를 밖으로 내보내는 구멍. 요청 경로가 브로커에 의존하면 안 되므로 인터페이스로 뚫어
 * 두고 지금은 아무것도 안 하는 구현을 꽂는다 — 어댑터를 갈아 끼우면 나머지는 안 고친다.
 */
public interface EventPublisherPort {

    /**
     * 한 건 보낸다.
     *
     * <p>🔴 <b>정정 (S15P21E201-561).</b> 여기에 <i>"받는 쪽이 중복을 걸러낼 수 있게
     * {@code eventId} 를 메시지 키로 쓴다"</i> 고 적혀 있었다. <b>키로 쓰면 안 된다.</b>
     * 메시지 키는 브로커가 <b>어느 파티션에 넣을지</b>를 정하고, 순서는 파티션 안에서만
     * 지켜진다. {@code eventId} 는 건마다 달라서 키로 쓰면 한 여행·한 사용자의 이벤트가
     * 파티션에 흩어지고, 릴레이가 {@code ORDER BY seq} 와 "실패하면 멈춘다" 로 지켜 온 순서가
     * 소비자 쪽에서 그대로 무너진다.
     *
     * <p>둘은 다른 일이고 칸도 따로 있다.
     * <ul>
     * <li><b>순서</b> — 메시지 키는 {@code partitionKey}. 그 칼럼의 뜻이
     * {@code OutboxAppendCommand} 에 적혀 있다: <i>"브로커로 보낼 때 순서를 지켜야 하는 단위"</i></li>
     * <li><b>멱등</b> — {@code eventId} 는 헤더({@code event_id})로 싣는다. 릴레이는 "정확히
     * 한 번" 이 아니라 "적어도 한 번" 을 보장하므로(보낸 뒤 적기 전에 죽는 구간이 있다) 받는
     * 쪽이 그 값으로 같은 것을 두 번 처리하지 않게 걸러야 한다</li>
     * </ul>
     *
     * <p>한 칸에 둘을 태우면 둘 다 잃는다. 옛 문장을 지우지 않고 남겨 둔 이유는, 다음 사람이
     * 같은 판단을 다시 하지 않게 하기 위해서다.
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
