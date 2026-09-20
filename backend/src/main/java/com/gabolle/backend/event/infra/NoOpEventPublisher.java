package com.gabolle.backend.event.infra;

import org.springframework.stereotype.Component;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 아무것도 내보내지 않는 발행자. {@link #isAvailable()} 이 {@code false} 라 릴레이가 조용히
 * 물러나고, 그동안 이벤트는 {@code published_at = null} 로 계속 쌓인다. 나중에 진짜 어댑터를
 * 꽂으면 그때까지 쌓인 것도 전부 나간다.
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
