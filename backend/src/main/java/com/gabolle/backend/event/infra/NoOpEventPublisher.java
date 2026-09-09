package com.gabolle.backend.event.infra;

import org.springframework.stereotype.Component;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 아무것도 내보내지 않는 발행자 — M1·M2 용.
 *
 * <p>🔴 개발계획서 4.4 가 못 박았다 — "M1·M2 에서 Kafka·Spark 기동"은 하지 않는 일이고,
 * "그 전에는 PostgreSQL Outbox 테이블에 <b>쌓아만 둔다</b>".
 *
 * <p>{@link #isAvailable()} 이 {@code false} 라서 릴레이가 조용히 물러난다.
 * 그동안 이벤트는 {@code published_at = null} 로 계속 쌓인다.
 * M3 에 Kafka 어댑터를 꽂으면 <b>그때까지 쌓인 것도 전부 나간다.</b>
 *
 * <p>이것이 4계층으로 나눈 값이다 — 이 클래스를 갈아 끼우는 것 말고는 아무것도 안 고친다.
 */
@Component
public class NoOpEventPublisher implements EventPublisherPort {

    @Override
    public void publish(EventOutbox event) {
        throw new UnsupportedOperationException(
                "M1·M2 에는 발행 대상이 없다. isAvailable() 이 false 이므로 여기까지 오지 않는다");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
