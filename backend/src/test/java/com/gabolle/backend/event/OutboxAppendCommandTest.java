package com.gabolle.backend.event;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.domain.Producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OutboxAppendCommand} 의 입력 검사. 이 커맨드가 공용 입구이므로 검사도 여기 있어야
 * 한다 — 호출자 한 곳에만 두면 커맨드를 직접 만드는 다른 호출자가 보호받지 못한다.
 */
class OutboxAppendCommandTest {


    @Test
    @DisplayName("producer 가 없으면 거부한다 (DR-13)")
    void producerIsMandatory() {
        assertThatThrownBy(() -> new OutboxAppendCommand(
                UUID.randomUUID(), "place_like", 1, "trip", UUID.randomUUID(), "user",
                Map.of(), OffsetDateTime.now(), UUID.randomUUID(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("producer");
    }

    @Test
    @DisplayName("🔴 추천 이벤트(aggregateType=recommendation)에 requestId 를 채우면 거부한다")
    void recommendationEventRejectsExplicitRequestId() {
        assertThatThrownBy(() -> new OutboxAppendCommand(
                UUID.randomUUID(), "recommendation_impression", 1, "recommendation", UUID.randomUUID(), "user",
                Map.of(), OffsetDateTime.now(), UUID.randomUUID(), null, null, Producer.CLIENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recommendation");
    }

    @Test
    @DisplayName("추천 이벤트에 requestId 가 null 이면 통과한다 — 요청 축은 aggregateId 다")
    void recommendationEventAllowsNullRequestId() {
        OutboxAppendCommand command = new OutboxAppendCommand(
                UUID.randomUUID(), "recommendation_impression", 1, "recommendation", UUID.randomUUID(), "user",
                Map.of(), OffsetDateTime.now(), null, null, null, Producer.CLIENT);

        assertThat(command.requestId()).isNull();
    }

    @Test
    @DisplayName("추천이 아닌 이벤트는 requestId 를 채울 수 있다")
    void nonRecommendationEventAllowsRequestId() {
        UUID requestId = UUID.randomUUID();
        OutboxAppendCommand command = new OutboxAppendCommand(
                UUID.randomUUID(), "place_like", 1, "trip", UUID.randomUUID(), "user",
                Map.of(), OffsetDateTime.now(), requestId, null, null, Producer.SERVER);

        assertThat(command.requestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("🔴 하위 호환 생성자(8개 인자)는 producer=SERVER · requestId=null 로 채운다")
    void legacyEightArgConstructorDefaultsToServer() {
        OutboxAppendCommand command = new OutboxAppendCommand(
                UUID.randomUUID(), "recommendation_requested", 1, "recommendation", UUID.randomUUID(), "user",
                Map.of(), OffsetDateTime.now());

        assertThat(command.producer()).isEqualTo(Producer.SERVER);
        assertThat(command.requestId()).isNull();
        assertThat(command.userId()).isNull();
        assertThat(command.tripId()).isNull();
    }
}
