package com.gabolle.backend.event;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 수집 서비스가 OutboxService 를 통해 적는지, envelope 축을 제대로 채우는지 본다.
 *
 * <p>이름이 같은 리포지토리 인터페이스가 둘 있어 수집 API 가 DB 가 아니라 메모리에 적던
 * 적이 있다. 표는 멀쩡해 보이고 안만 비는 종류라, 여기서 OutboxService 로 갔는지를 직접
 * 확인한다.
 */
class EventIngestServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    private OutboxService outboxService;
    private AppUserRepository users;
    private EventIngestService service;

    @BeforeEach
    void setUp() {
        this.outboxService = mock(OutboxService.class);
        this.users = mock(AppUserRepository.class);
        this.service = new EventIngestService(this.outboxService, this.users, Clock.fixed(NOW, ZoneOffset.UTC));
        givenAppendReturns(true);
        givenBehaviorPersonalization(PersonalizationMode.BEHAVIOR_ENABLED);
    }

    private void givenAppendReturns(boolean created) {
        given(this.outboxService.appendReportingDuplicate(any()))
                .willReturn(new OutboxService.AppendResult(mock(EventOutbox.class), created));
    }

    /**
     * 기본을 켜짐으로 두는 이유 — 이 파일의 나머지 검사는 개인화가 아니라 적재 경로를
     * 잰다. 기본이 꺼짐이면 그 검사들이 전부 「안 적힘」으로 통과해 적재가 깨져도 초록이다.
     */
    private void givenBehaviorPersonalization(PersonalizationMode mode) {
        given(this.users.findPersonalizationMode(any())).willReturn(Optional.ofNullable(mode));
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

        EventIngestService.Outcome outcome = this.service.ingestFromClient(eventId,
                EventType.RECOMMENDATION_IMPRESSION, 1,
                UUID.randomUUID(), UUID.randomUUID(), requestId, at("2026-09-03T11:59:00Z"), Map.of("rank", 3));

        assertThat(outcome).isEqualTo(EventIngestService.Outcome.STORED);
        OutboxAppendCommand command = captureCommand();
        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.eventType()).isEqualTo("recommendation_impression");
    }

    @Test
    @DisplayName("이미 받은 이벤트는 DUPLICATE 를 돌려준다 — 오류가 아니다")
    void duplicateIsReportedNotRejected() {
        givenAppendReturns(false);

        EventIngestService.Outcome outcome = this.service.ingestFromClient(UUID.randomUUID(),
                EventType.RECOMMENDATION_IMPRESSION, 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), at("2026-09-03T11:59:00Z"), Map.of());

        assertThat(outcome).isEqualTo(EventIngestService.Outcome.DUPLICATE);
    }

    // ── 행동 기반 개인화를 끈 사람 ───────────────────

    @Test
    @DisplayName("🔴 개인화를 끈 사람의 행동 이벤트는 적히지 않는다 — OutboxService 까지 가지도 않는다")
    void behaviorEventOfOptedOutUserIsNotStored() {
        givenBehaviorPersonalization(PersonalizationMode.EXPLICIT_ONLY);

        EventIngestService.Outcome outcome = this.service.ingestFromClient(UUID.randomUUID(),
                EventType.PLACE_VIEW, 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), at("2026-09-03T11:59:00Z"), Map.of());

        assertThat(outcome).isEqualTo(EventIngestService.Outcome.NOT_COLLECTED);
        verify(this.outboxService, never()).appendReportingDuplicate(any());
    }

    @Test
    @DisplayName("🔴 계정을 못 찾으면 안 적는다 — 동의를 확인할 수 없으면 모으지 않는다")
    void behaviorEventIsNotStoredWhenTheAccountIsUnknown() {
        givenBehaviorPersonalization(null);

        EventIngestService.Outcome outcome = this.service.ingestFromClient(UUID.randomUUID(),
                EventType.PLACE_VIEW, 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), at("2026-09-03T11:59:00Z"), Map.of());

        assertThat(outcome).isEqualTo(EventIngestService.Outcome.NOT_COLLECTED);
        verify(this.outboxService, never()).appendReportingDuplicate(any());
    }

    @Test
    @DisplayName("🔴 껐어도 행동 관찰이 아닌 이벤트는 그대로 적힌다 — 껐다는 것이 '내가 고른 것도 잊으라' 는 뜻은 아니다")
    void nonBehaviorEventIsStoredEvenWhenPersonalizationIsOff() {
        givenBehaviorPersonalization(PersonalizationMode.EXPLICIT_ONLY);

        this.service.recordFromServer(UUID.randomUUID(), EventType.TRIP_CREATED, 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Map.of());

        assertThat(captureCommand().eventType()).isEqualTo("trip_created");
    }

    @Test
    @DisplayName("🔴 형식이 틀린 이벤트는 껐든 켰든 400 이다 — 동의를 먼저 보면 앱의 계측 버그가 '그 사람만 안 쌓인다' 로 보인다")
    void malformedBehaviorEventStillFailsWhenPersonalizationIsOff() {
        givenBehaviorPersonalization(PersonalizationMode.EXPLICIT_ONLY);

        Map<String, Object> payloadWithEnvelopeKey = new HashMap<>();
        payloadWithEnvelopeKey.put("user_id", UUID.randomUUID().toString());

        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(), EventType.PLACE_VIEW, 1,
                UUID.randomUUID(), null, null, at("2026-09-03T11:59:00Z"), payloadWithEnvelopeKey))
                .isInstanceOf(IllegalArgumentException.class);
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

    // ── request_id·user_id·trip_id·producer 는 payload 가 아니라 실컬럼이다 ─────

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
    @DisplayName("🔴 payload 가 envelope 키를 덮어쓰려 하면 거부한다")
    void payloadCannotOverrideEnvelopeKeys() {
        Map<String, Object> hostile = new HashMap<>();
        hostile.put("request_id", "남의 요청");

        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(),
                EventType.RECOMMENDATION_IMPRESSION, 1, null, null, UUID.randomUUID(),
                at("2026-09-03T11:59:00Z"), hostile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request_id");
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
    @DisplayName("🔴 추천 축 이벤트는 requestId 가 없으면 거부한다 (API-07)")
    void requestIdIsMandatoryOnTheRecommendationAxis() {
        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(),
                EventType.RECOMMENDATION_IMPRESSION, 1, null, null, null, at("2026-09-03T11:59:00Z"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API-07");
    }

    // ── 축이 USER 인 이벤트와 requestId 범위 ──────

    @Test
    @DisplayName("🔴 여행 밖 저장은 requestId 없이도 적힌다 — 홈·장소 상세에는 줄 값이 없다")
    void placeEventsAreAcceptedWithoutARequestId() {
        UUID userId = UUID.randomUUID();

        EventIngestService.Outcome outcome = this.service.ingestFromClient(UUID.randomUUID(),
                EventType.PLACE_LIKE, 1,
                userId, null, null, at("2026-09-03T11:59:00Z"), Map.of("place_id", "seomyeon-1"));

        assertThat(outcome).isEqualTo(EventIngestService.Outcome.STORED);
        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateType()).isEqualTo("user");
        assertThat(command.aggregateId()).isEqualTo(userId);
        assertThat(command.requestId()).isNull();
        assertThat(command.tripId()).isNull();
    }

    @Test
    @DisplayName("추천 화면에서 온 저장은 requestId 를 그대로 이어 붙인다 — 축은 여전히 사용자다")
    void placeEventsKeepTheRequestIdWhenTheAppKnowsIt() {
        UUID userId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.PLACE_LIKE, 1,
                userId, tripId, requestId, at("2026-09-03T11:59:00Z"), Map.of());

        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateId()).isEqualTo(userId);
        assertThat(command.requestId()).isEqualTo(requestId);
        assertThat(command.tripId()).isEqualTo(tripId);
    }

    @Test
    @DisplayName("🔴 사용자 축 이벤트인데 userId 가 없으면 거부한다 — 다른 값을 대신 넣지 않는다")
    void userAxisEventWithoutUserIdIsRejected() {
        assertThatThrownBy(() -> this.service.ingestFromClient(UUID.randomUUID(), EventType.PLACE_LIKE, 1,
                null, null, UUID.randomUUID(), at("2026-09-03T11:59:00Z"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("🔴 저장·제외·방문은 클라이언트도 보낼 수 있고, producer 칸에 CLIENT 로 남는다")
    void placeSignalsAreAcceptedFromTheClientAndStayLabelled() {
        UUID userId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.PLACE_DISLIKE, 1,
                userId, UUID.randomUUID(), null, at("2026-09-03T11:59:00Z"), Map.of());

        assertThat(captureCommand().producer()).isEqualTo(Producer.CLIENT);
    }

    @Test
    @DisplayName("체크인 후기(place_visit)는 여행 축 그대로다 — 방문은 여행 안에서만 일어난다")
    void visitEventsStayOnTheTripAxis() {
        UUID tripId = UUID.randomUUID();

        this.service.ingestFromClient(UUID.randomUUID(), EventType.PLACE_VISIT, 1,
                UUID.randomUUID(), tripId, null, at("2026-09-03T11:59:00Z"), Map.of("rating", 5));

        OutboxAppendCommand command = captureCommand();
        assertThat(command.aggregateType()).isEqualTo("trip");
        assertThat(command.aggregateId()).isEqualTo(tripId);
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
