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
 * S15P21E201-352 회귀 수정 — 고지혁 님 실측.
 *
 * <p>Outbox 통합 전에는 {@code OutboxEvent} 생성자가 requestId 없는 이벤트를
 * 거부했다. 통합 뒤 그 검사가 <b>진짜 공용 입구인 이 커맨드에는</b> 없었다 —
 * {@code EventIngestService} 라는 한 호출자에만 있었고, {@code RecommendationRecorder}
 * 처럼 이 커맨드를 직접 만드는 다른 호출자는 보호받지 못했다. 여기서 그 자리를 채운다.
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
