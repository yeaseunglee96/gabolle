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
import com.gabolle.backend.event.config.OutboxRelayProperties;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.OutboxPublishStatus;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.repository.EventOutboxRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 릴레이가 실패를 견디는가.
 */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    private EventOutboxRepository repository;
    private RecordingPublisher publisher;
    private OutboxRelayProperties properties;
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
        this.properties = new OutboxRelayProperties();
        this.relay = new OutboxRelayService(this.repository, this.publisher, this.properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        given(this.repository.save(any(EventOutbox.class))).willAnswer((call) -> call.getArgument(0));
    }

    private EventOutbox pending() {
        return new EventOutbox(UUID.randomUUID(), "recommendation_impression", 1, "recommendation",
                UUID.randomUUID(), "user", "{}", OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)),
                OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)),
                null, null, null, Producer.CLIENT);
    }

    private void givenPending(EventOutbox... events) {
        // S15P21E201-561 — 조회가 재시도 상한을 함께 본다. anyInt() 로 둔 것은 이 묶음의
        // 시험들이 상한 자체를 검사하지 않기 때문이다 — 상한은 아래 별도 시험이 본다.
        given(this.repository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderBySeqAsc(anyInt(), any()))
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

    // ── 재시도 상한 (S15P21E201-561) ────────────────────────────────────

    @Test
    @DisplayName("🔴 상한을 넘긴 행은 조회에서 뺀다 — 안 빼면 영원히 실패하는 한 건이 뒤 전부를 막는다")
    void exhaustedRowsAreExcludedFromTheQuery() {
        this.properties.setMaxAttempts(3);
        givenPending();

        this.relay.relayOnce();

        // 설정값이 그대로 조회 조건으로 내려가야 한다. 여기가 어긋나면 상한을 올려도
        // 줄이 안 풀리는데, 그 증상은 "그냥 안 나간다" 라서 원인을 짚기 어렵다.
        verify(this.repository).findByPublishedAtIsNullAndPublishAttemptsLessThanOrderBySeqAsc(eq(3), any());
    }

    @Test
    @DisplayName("배치 크기도 설정에서 온다 — 상수로 박아 두면 운영에서 못 줄인다")
    void batchSizeComesFromProperties() {
        this.properties.setBatchSize(7);
        givenPending();

        this.relay.relayOnce();

        verify(this.repository).findByPublishedAtIsNullAndPublishAttemptsLessThanOrderBySeqAsc(anyInt(),
                argThat((pageable) -> pageable != null && pageable.getPageSize() == 7));
    }

    @Test
    @DisplayName("🔴 상한을 넘겨도 행을 지우지 않는다 — 원인을 고치면 되살릴 수 있어야 한다")
    void exhaustedRowIsKeptNotDeleted() {
        EventOutbox event = pending();
        for (int attempt = 0; attempt < 5; attempt++) {
            event.markFailed("계속 실패");
        }

        assertThat(event.getPublishAttempts()).isEqualTo(5);
        assertThat(event.getPublishStatus()).isEqualTo(OutboxPublishStatus.FAILED);
        // published_at 이 여전히 null 이라 "안 나갔다" 는 사실이 표에 그대로 남는다.
        assertThat(event.isPending()).isTrue();
        assertThat(event.getLastError()).contains("계속 실패");
    }
}
