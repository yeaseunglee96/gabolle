package com.gabolle.backend.event.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 아무것도 내보내지 않는 발행자. {@link #isAvailable()} 이 {@code false} 라 릴레이가 조용히
 * 물러나고, 그동안 이벤트는 {@code published_at = null} 로 계속 쌓인다. 나중에 진짜 어댑터를
 * 꽂으면 그때까지 쌓인 것도 전부 나간다.
 *
 * <p>🔴 S15P21E201-561 — 조건을 {@link KafkaEventPublisher} 와 <b>정반대로 하나씩</b> 걸었다.
 * 둘 다 {@code @Component} 이므로 조건이 없으면 같은 {@link EventPublisherPort} 빈이 둘이 되어
 * 기동이 막힌다({@code NoUniqueBeanDefinitionException}). {@code @ConditionalOnMissingBean} 으로
 * 미루지 않은 이유는 그것이 자동설정에서만 순서가 보장되고, 지금처럼 컴포넌트 스캔으로 잡히는
 * 빈 사이에서는 <b>누가 먼저 등록되느냐에 따라 결과가 달라지기</b> 때문이다.
 *
 * <p>{@code matchIfMissing = true} — 설정 줄이 아예 없으면 지금까지처럼 꺼진 쪽이다.
 * 의존성을 추가한 것만으로 운영 동작이 바뀌면 안 된다.
 */
@Component
@ConditionalOnProperty(prefix = "gabolle.event.kafka", name = "enabled", havingValue = "false",
		matchIfMissing = true)
public class NoOpEventPublisher implements EventPublisherPort {

    @Override
    public void publish(EventOutbox event) {
        throw new UnsupportedOperationException(
                "발행 대상이 꺼져 있다. isAvailable() 이 false 이므로 여기까지 오지 않는다");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
