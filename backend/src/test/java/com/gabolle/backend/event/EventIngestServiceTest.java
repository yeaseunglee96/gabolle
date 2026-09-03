package com.gabolle.backend.event;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 수집 서비스가 <b>OutboxService 를 통해</b> 적는지, envelope 축을 제대로 채우는지 본다.
 *
 * <p>🔴 이 테스트가 있는 이유는 2026-09-03 의 고장이다 — 같은 패키지에 이름이 같은
 * 리포지토리 인터페이스가 둘 있어서 수집 API 가 <b>DB 가 아니라 메모리에</b> 적고 있었다.
 * 표는 멀쩡해 보이는데 안이 비는 종류라 아무도 못 봤다.
 * 그래서 여기서 <b>"OutboxService 로 갔는가"</b> 를 직접 확인한다.
 */
class EventIngestServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    private OutboxService outboxService;
    private EventIngestService service;

    @BeforeEach
    void setUp() {
        this.outboxService = mock(OutboxService.class);
        this.service = new EventIngestService(this.outboxService, Clock.fixed(NOW, ZoneOffset.UTC));
        givenAppendReturns(true);
    }

    private void givenAppendReturns(boolean created) {
        given(this.outboxService.appendReportingDuplicate(any()))
                .willReturn(new OutboxService.AppendResult(mock(EventOutbox.class), created));
    }

    private OutboxAppendCommand captureCommand() {
        ArgumentCaptor<OutboxAppendCommand> captor = ArgumentCaptor.forClass(OutboxAppendCommand.class);
        verify(this.outboxService).appendReportingDuplicate(captor.capture());
        return captor.getValue();
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    // ── 적히는 경로 ──────────────────────────────────────────────

    @Test
    @DisplayName("🔴 클라이언트 이벤트는 OutboxService 로 간다 — 메모리가 아니다")
    void clientEventGoesThroughOutboxService() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        boolean created = this.service.ingestFromClient(eventId, EventType.RECOMMENDATION_IMPRESSION, 1,
                UUID.randomUUID(), UUID.randomUUID(), requestId, at("2026-09-03T11:59:00Z"), Map.of("rank", 3));

        assertThat(created).isTrue();
        OutboxAppendCommand command = captureCommand();
        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.eventType()).isEqualTo("recommendation_impression");
    }

    @Test
    @DisplayName("이미 받은 이벤트는 false 를 돌려준다 — 오류가 아니다")
    void duplicateIsReportedNotRejected() {
        givenAppendReturns(false);

        boolean created = this.service.ingestFromClient(UUID.randomUUID(), EventType.RECOMMENDATION_IMPRESSION, 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), at("2026-09-03T11:59:00Z"), Map.of());

        assertThat(created).isFalse();
    }

    // ── envelope 축 ─────────────────────────────────────────────

    @Test
    @DisplayName("추천 이벤트의 aggregate 는 recommendation / request_id 다")
    void recommendationEventsAggregateOnRequestId() {
        UUID requestId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.RECOMMENDATION_IMPRESSION, 1,
                UUID.randomUUID(), UUID.randomUUID(), requestId, at("2026-09-03T11:59:00Z"), Map.of());

        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateType()).isEqualTo("recommendation");
        assertThat(command.aggregateId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("여행 이벤트의 aggregate 는 trip / trip_id 다")
    void tripEventsAggregateOnTripId() {
        UUID tripId = UUID.randomUUID();

        this.service.recordFromServer(UUID.randomUUID(), EventType.TRIP_CREATED, 1,
                UUID.randomUUID(), tripId, UUID.randomUUID(), Map.of());

        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateType()).isEqualTo("trip");
        assertThat(command.aggregateId()).isEqualTo(tripId);
    }

    @Test
    @DisplayName("🔴 여행 이벤트인데 tripId 가 없으면 거부한다 — 다른 값을 대신 넣지 않는다")
    void tripEventWithoutTripIdIsRejected() {
        assertThatThrownBy(() -> this.service.recordFromServer(UUID.randomUUID(), EventType.TRIP_CREATED, 1,
                UUID.randomUUID(), null, UUID.randomUUID(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tripId");
    }

    @Test
    @DisplayName("🔴 aggregate 축이 안 정해진 종류는 적히지 않는다")
    void undecidedAggregateAxisIsRejected() {
        assertThat(EventType.EDITORIAL_PICK_PUBLISHED.hasAggregateAxis()).isFalse();

        assertThatThrownBy(() -> this.service.recordFromServer(UUID.randomUUID(),
                EventType.EDITORIAL_PICK_PUBLISHED, 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aggregate");
    }

    @Test
    @DisplayName("partitionKey 는 사용자다. 🔴 비로그인이면 aggregate 로 흩는다")
    void partitionKeyFallsBackToAggregateWhenAnonymous() {
        UUID requestId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.RECOMMENDATION_IMPRESSION, 1,
                null, null, requestId, at("2026-09-03T11:59:00Z"), Map.of());

        assertThat(captureCommand().partitionKey()).isEqualTo(requestId.toString());
    }

    // ── 2026-09-03 — request_id·user_id·trip_id·producer 는 이제 실컬럼이다 ─────
    //    (고지혁 님이 S15P21E201-352-event-outbox-join-axes 에서 컬럼을 추가했다.
    //    payload 에 욱여넣던 옛 방식은 지웠다 — 아래는 그 실컬럼 배선을 검증한다.)

    @Test
    @DisplayName("user_id · trip_id · producer 는 커맨드의 실컬럼으로 간다 — payload 에 안 실린다")
    void userIdTripIdProducerGoToRealColumnsNotPayload() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.RECOMMENDATION_IMPRESSION, 1,
                userId, tripId, requestId, at("2026-09-03T11:59:00Z"), Map.of("rank", 3));

        OutboxAppendCommand command = captureCommand();
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.tripId()).isEqualTo(tripId);
        assertThat(command.producer()).isEqualTo(Producer.CLIENT);
        assertThat(command.payload()).containsEntry("rank", 3)
                .doesNotContainKeys("request_id", "user_id", "trip_id", "producer");
    }

    @Test
    @DisplayName("🔴 추천 이벤트는 request_id 컬럼이 null 이다 — 요청 축은 이미 aggregate_id 다")
    void recommendationEventLeavesRequestIdColumnNull() {
        UUID requestId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.RECOMMENDATION_IMPRESSION, 1,
                UUID.randomUUID(), UUID.randomUUID(), requestId, at("2026-09-03T11:59:00Z"), Map.of());

        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateId()).isEqualTo(requestId);
        assertThat(command.requestId()).isNull();
    }

    @Test
    @DisplayName("🔴 여행 축 이벤트는 request_id 컬럼을 채운다 — aggregate_id 와 다른 값이다")
    void tripAxisEventFillsRequestIdColumn() {
        UUID tripId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        this.service.recordFromServer(UUID.randomUUID(), EventType.TRIP_CREATED, 1,
                UUID.randomUUID(), tripId, requestId, Map.of());

        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateId()).isEqualTo(tripId);
        assertThat(command.requestId()).isEqualTo(requestId);
    }

    // ── 입구에서 막는 것 ─────────────────────────────────────────

    @Test
    @DisplayName("🔴 requestId 가 없으면 거부한다 (API-07)")
    void requestIdIsMandatory() {
        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(),
                EventType.RECOMMENDATION_IMPRESSION, 1, null, null, null, at("2026-09-03T11:59:00Z"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API-07");
    }

    @Test
    @DisplayName("🔴 클라이언트가 서버 전용 이벤트를 보내면 거부한다 (DR-13)")
    void clientCannotForgeServerEvents() {
        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(),
                EventType.TRIP_CREATED, 1, null, UUID.randomUUID(), UUID.randomUUID(),
                at("2026-09-03T11:59:00Z"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SERVER");
    }

    @Test
    @DisplayName("🔴 발생 시각이 수신 시각보다 뒤면 거부한다 — 기기 시계가 틀렸거나 조작된 값이다")
    void occurredAtCannotBeInTheFuture() {
        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(),
                EventType.RECOMMENDATION_IMPRESSION, 1, null, null, UUID.randomUUID(),
                at("2026-09-03T12:00:01Z"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("뒤다");
    }
}
