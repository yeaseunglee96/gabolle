package com.gabolle.backend.event;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.event.application.OutboxRelayService;
import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.OutboxPublishStatus;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.repository.EventOutboxRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 릴레이가 실패를 견디는가.
 */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    private EventOutboxRepository repository;
    private RecordingPublisher publisher;
    private OutboxRelayService relay;

    /** 켜고 끌 수 있고, 무엇을 보냈는지 기억하는 발행자. */
    private static final class RecordingPublisher implements EventPublisherPort {

        private final List<UUID> sent = new ArrayList<>();
        private boolean available = true;
        private RuntimeException failWith;

        @Override
        public void publish(EventOutbox event) {
            if (this.failWith != null) {
                throw this.failWith;
            }
            this.sent.add(event.getEventId());
        }

        @Override
        public boolean isAvailable() {
            return this.available;
        }
    }

    @BeforeEach
    void setUp() {
        this.repository = mock(EventOutboxRepository.class);
        this.publisher = new RecordingPublisher();
        this.relay = new OutboxRelayService(this.repository, this.publisher, Clock.fixed(NOW, ZoneOffset.UTC));
        given(this.repository.save(any(EventOutbox.class))).willAnswer((call) -> call.getArgument(0));
    }

    private EventOutbox pending() {
        return new EventOutbox(UUID.randomUUID(), "recommendation_impression", 1, "recommendation",
                UUID.randomUUID(), "user", "{}", OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)),
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)),
                null, null, null, Producer.CLIENT);
    }

    private void givenPending(EventOutbox... events) {
        given(this.repository.findByPublishedAtIsNullOrderBySeqAsc(any()))
                .willReturn(List.of(events));
    }

    @Test
    @DisplayName("대기 중인 것을 보내고 발행 시각을 적는다")
    void sendsPendingAndStampsPublishedAt() {
        EventOutbox event = pending();
        givenPending(event);

        int sent = this.relay.relayOnce();

        assertThat(sent).isEqualTo(1);
        assertThat(this.publisher.sent).containsExactly(event.getEventId());
        assertThat(event.getPublishedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.PUBLISHED);
    }

    @Test
    @DisplayName("🔴 두 번 돌려도 같은 이벤트가 두 번 나가지 않는다")
    void alreadyPublishedIsNotSentAgain() {
        EventOutbox event = pending();
        event.markPublished(OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        givenPending(event);

        this.relay.relayOnce();

        assertThat(this.publisher.sent).isEmpty();
        // 발행 시각이 덮어써지지 않는다 — 덮어쓰면 "언제 보냈나" 가 흐려진다.
        assertThat(event.getPublishedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("🔴 중계 서버가 꺼져 있으면 조용히 물러난다 — 워커가 죽지 않는다")
    void backsOffQuietlyWhenPublisherIsDown() {
        this.publisher.available = false;
        givenPending(pending());

        assertThat(this.relay.relayOnce()).isZero();
        assertThat(this.publisher.sent).isEmpty();
    }

    @Test
    @DisplayName("🔴 M1·M2 기본 발행자는 꺼져 있다 — 이벤트는 쌓이기만 한다 (개발계획서 4.4)")
    void milestoneOneKeepsEventsInTheTable() {
        assertThat(new com.gabolle.backend.event.infra.NoOpEventPublisher().isAvailable()).isFalse();
    }

    @Test
    @DisplayName("실패한 것은 표에 남고 오류가 기록된다 — 지우지 않는다")
    void failureIsRecordedNotDiscarded() {
        this.publisher.failWith = new IllegalStateException("중계 서버 없음");
        EventOutbox event = pending();
        givenPending(event);

        assertThat(this.relay.relayOnce()).isZero();
        assertThat(event.isPending()).isTrue();
        assertThat(event.getPublishAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("중계 서버 없음");
        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.FAILED);
    }

    @Test
    @DisplayName("🔴 한 건이 실패하면 거기서 멈춘다 — 뒤 것을 건너뛰면 순서가 뒤바뀐다")
    void stopsAtTheFirstFailure() {
        this.publisher.failWith = new IllegalStateException("중계 서버 없음");
        givenPending(pending(), pending(), pending());

        this.relay.relayOnce();

        assertThat(this.publisher.sent).isEmpty();
    }

    @Test
    @DisplayName("🔴 실패했던 것도 다음 차례에 다시 대기로 잡힌다 — 상태가 아니라 발행 시각이 기준이다")
    void failedEventIsStillPending() {
        EventOutbox event = pending();
        event.markFailed("앞 차례에서 실패");

        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.FAILED);
        assertThat(event.isPending()).isTrue();

        this.publisher.failWith = null;
        givenPending(event);

        assertThat(this.relay.relayOnce()).isEqualTo(1);
        assertThat(this.publisher.sent).containsExactly(event.getEventId());
    }
}
