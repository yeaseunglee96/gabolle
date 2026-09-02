package com.gabolle.backend.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.application.OutboxRelayService;
import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.OutboxEvent;
import com.gabolle.backend.event.infra.InMemoryEventOutboxRepository;
import com.gabolle.backend.event.infra.NoOpEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 전달 워커 — S15P21E201-354 · 160.
 *
 * <p>완료 기준 셋을 그대로 검증한다.
 * <ul>
 *   <li>중계 서버를 껐다 켜면 그 사이 쌓인 이벤트가 전달된다</li>
 *   <li>🔴 <b>두 번 돌려도 같은 이벤트가 두 번 기록되지 않는다</b></li>
 *   <li>중계 서버가 안 되는 동안 워커가 오류로 죽지 않는다</li>
 * </ul>
 */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private InMemoryEventOutboxRepository repository;
    private EventIngestService ingest;

    /** 보낸 것을 세는 가짜 발행자. 껐다 켤 수 있다. */
    private static final class RecordingPublisher implements EventPublisherPort {
        final List<String> published = new ArrayList<>();
        boolean available = true;
        boolean throwOnPublish = false;

        @Override
        public void publish(OutboxEvent event) {
            if (throwOnPublish) {
                throw new EventPublishException("중계 서버가 응답하지 않습니다", null);
            }
            published.add(event.eventId());
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    private RecordingPublisher publisher;
    private OutboxRelayService relay;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new InMemoryEventOutboxRepository();
        ingest = new EventIngestService(repository, clock);
        publisher = new RecordingPublisher();
        relay = new OutboxRelayService(repository, publisher, clock);
    }

    private void store(String eventId) {
        ingest.ingestFromClient(eventId, EventType.RECOMMENDATION_IMPRESSION, 1,
                "usr_1", "trp_1", "req_" + eventId,
                NOW.minusSeconds(1), Map.of("placeId", "plc_1"));
    }

    /** 🔴 완료 기준 — "중계 서버가 꺼져 있어도 정상 응답하고 DB 에 쌓인다" */
    @Test
    @DisplayName("🔴 중계 서버가 꺼져 있으면 이벤트가 쌓이기만 하고 아무것도 안 나간다")
    void nothingLeavesWhilePublisherIsDown() {
        publisher.available = false;

        store("evt_1");
        store("evt_2");

        int sent = relay.relayOnce();

        assertEquals(0, sent, "꺼져 있으면 한 건도 안 나간다");
        assertEquals(2, repository.pendingCount(), "🔴 그러나 유실되지 않고 쌓여 있다");
        assertTrue(publisher.published.isEmpty());
    }

    /** 🔴 완료 기준 — "껐다 켜면 그 사이 쌓인 이벤트가 전달된다" */
    @Test
    @DisplayName("🔴 중계 서버를 다시 켜면 쌓여 있던 것이 전부 나간다")
    void pendingEventsFlushAfterRecovery() {
        publisher.available = false;
        store("evt_1");
        store("evt_2");
        store("evt_3");
        relay.relayOnce();
        assertEquals(3, repository.pendingCount());

        publisher.available = true;
        int sent = relay.relayOnce();

        assertEquals(3, sent);
        assertEquals(0, repository.pendingCount(), "전부 발행됐다");
        assertEquals(List.of("evt_1", "evt_2", "evt_3"), publisher.published,
                "받은 순서대로 나가야 한다");
        assertNotNull(repository.findById("evt_1").orElseThrow().publishedAt(),
                "발행 시각이 적혀야 한다");
    }

    /**
     * 🔴 이 테스트가 -354 의 핵심 완료 기준이다.
     *
     * <p>"워커를 두 번 돌려도 같은 이벤트가 두 번 기록되지 않는다"
     */
    @Test
    @DisplayName("🔴 워커를 두 번 돌려도 같은 이벤트가 두 번 나가지 않는다")
    void relayingTwiceDoesNotResend() {
        store("evt_1");
        store("evt_2");

        assertEquals(2, relay.relayOnce(), "첫 차례에 둘 다 나간다");
        assertEquals(2, publisher.published.size());

        int sentAgain = relay.relayOnce();

        assertEquals(0, sentAgain, "두 번째 차례에는 보낼 것이 없다");
        assertEquals(2, publisher.published.size(), "🔴 발행 횟수가 늘지 않았다");
    }

    /** 🔴 완료 기준 — "중계 서버가 안 되는 동안 워커가 오류로 죽지 않는다" */
    @Test
    @DisplayName("🔴 전송이 실패해도 워커가 죽지 않고 이벤트가 표에 남는다")
    void publishFailureKeepsEventForRetry() {
        store("evt_1");
        publisher.throwOnPublish = true;

        int sent = relay.relayOnce();   // 예외가 밖으로 나오면 이 줄에서 터진다

        assertEquals(0, sent);
        OutboxEvent e = repository.findById("evt_1").orElseThrow();
        assertTrue(e.isPending(), "🔴 실패한 것을 표에서 지우지 않는다");
        assertEquals(1, e.attempts(), "시도 횟수가 올라간다");
        assertNotNull(e.lastError(), "왜 실패했는지 남는다");

        // 중계 서버가 살아나면 다음 차례에 나간다.
        publisher.throwOnPublish = false;
        assertEquals(1, relay.relayOnce());
        assertFalse(repository.findById("evt_1").orElseThrow().isPending());
    }

    /**
     * 🔴 한 건 실패하면 거기서 멈춘다.
     *
     * <p>건너뛰고 진행하면 순서가 뒤바뀌고, 원인이 공통(중계 서버 다운)일 때
     * 나머지 전부가 실패 카운트만 올린다.
     */
    @Test
    @DisplayName("🔴 첫 건이 실패하면 뒤 것을 건너뛰지 않고 멈춘다")
    void relayStopsAtFirstFailure() {
        store("evt_1");
        store("evt_2");
        store("evt_3");
        publisher.throwOnPublish = true;

        relay.relayOnce();

        assertEquals(3, repository.pendingCount(), "하나도 나가지 않았다");
        assertEquals(1, repository.findById("evt_1").orElseThrow().attempts(),
                "첫 건만 시도했다");
        assertEquals(0, repository.findById("evt_3").orElseThrow().attempts(),
                "🔴 뒤 것은 시도조차 안 했다 — 실패 카운트만 올리지 않는다");
    }

    /**
     * 🔴 M1·M2 의 실제 구성 — 개발계획서 4.4.
     *
     * <p>"M1·M2 에서 Kafka·Spark 기동" 은 하지 않는 일이고,
     * "그 전에는 PostgreSQL Outbox 테이블에 쌓아만 둔다".
     */
    @Test
    @DisplayName("🔴 M1 기본 구성(NoOpEventPublisher)에서는 쌓이기만 한다")
    void m1ConfigurationOnlyAccumulates() {
        OutboxRelayService m1Relay = new OutboxRelayService(
                repository, new NoOpEventPublisher(), Clock.fixed(NOW, ZoneOffset.UTC));

        store("evt_1");
        store("evt_2");

        assertEquals(0, m1Relay.relayOnce(), "M1 에는 발행 대상이 없다");
        assertEquals(2, repository.pendingCount(),
                "🔴 M3 에 Kafka 를 꽂으면 그때까지 쌓인 것도 전부 나간다");
    }
}
