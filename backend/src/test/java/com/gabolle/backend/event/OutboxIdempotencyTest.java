package com.gabolle.backend.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.OutboxEvent;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.infra.InMemoryEventOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이벤트 멱등성과 유실 방지 — S15P21E201-352 · 354 · 160.
 *
 * <p>세 티켓의 완료 기준이 전부 <b>실패를 견디는 것</b>에 관한 것이다.
 * 그래서 여기서 하는 일은 정상 동작 확인이 아니라 <b>일부러 고장을 내는 것</b>이다.
 *
 * <p>🔴 {@link Clock} 을 고정한다. 코드에서 시각을 직접 읽으면
 * "발생 시각이 수신 시각보다 뒤면 거부" 같은 규칙을 검증할 수 없다.
 */
class OutboxIdempotencyTest {

    /** 서버 수신 시각을 고정한다. */
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private InMemoryEventOutboxRepository repository;
    private EventIngestService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEventOutboxRepository();
        service = new EventIngestService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private boolean ingest(String eventId) {
        return service.ingestFromClient(eventId, EventType.RECOMMENDATION_IMPRESSION, 1,
                "usr_1", "trp_1", "req_1",
                NOW.minusSeconds(3), Map.of("placeId", "plc_1", "rank", 1));
    }

    @Test
    @DisplayName("이벤트를 적으면 미발행 상태로 쌓인다")
    void ingestedEventIsPending() {
        assertTrue(ingest("evt_1"));

        OutboxEvent e = repository.findById("evt_1").orElseThrow();
        assertTrue(e.isPending(), "적재 직후에는 아직 안 보낸 상태여야 한다");
        assertNull(e.publishedAt());
        assertEquals(NOW, e.receivedAt(), "수신 시각은 서버가 찍는다");
        assertEquals(NOW.minusSeconds(3), e.occurredAt(), "발생 시각은 클라이언트가 준 값");
    }

    /**
     * 🔴 재전송은 오류가 아니다.
     *
     * <p>지하철에서 응답이 끊긴 앱은 반드시 다시 보낸다. 그때 실패로 답하면
     * 사용자 화면에 오류가 뜨는데, 사용자는 아무 잘못도 안 했다.
     */
    @Test
    @DisplayName("🔴 같은 eventId 를 다시 보내도 중복이 쌓이지 않고 성공으로 처리된다")
    void resendIsNotAnError() {
        assertTrue(ingest("evt_1"), "첫 전송은 새로 적힌다");
        assertFalse(ingest("evt_1"), "재전송은 이미 있다고 알린다 (오류가 아니다)");
        assertFalse(ingest("evt_1"));

        assertEquals(1, repository.pendingCount(), "행이 하나만 있어야 한다");
    }

    /**
     * 🔴 같은 eventId 가 <b>동시에</b> 들어와도 하나만 들어간다.
     *
     * <p>먼저 확인하고 넣는 방식만으로는 부족하다 — 확인과 저장 사이에
     * 다른 요청이 끼어들 수 있다(경쟁 조건). 마지막 방어선은
     * {@code putIfAbsent} 이고, DB 에서는
     * {@code INSERT ... ON CONFLICT (event_id) DO NOTHING} 이 그 자리를 대신한다.
     */
    @Test
    @DisplayName("🔴 같은 eventId 를 8개가 동시에 보내도 행은 하나뿐이다")
    void concurrentResendStoresOnlyOne() throws Exception {
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger storedFirst = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    go.await();
                    if (ingest("evt_same")) {
                        storedFirst.incrementAndGet();
                    }
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "모두 끝나야 한다");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, storedFirst.get(), "🔴 새로 적은 것은 정확히 하나여야 한다");
        assertEquals(1, repository.pendingCount(), "행도 하나뿐이어야 한다");
    }

    /** 🔴 API-07 — requestId 로 연결되지 않으면 분석용 정상 데이터로 승인하지 않는다. */
    @Test
    @DisplayName("🔴 requestId 가 없으면 이벤트 생성 자체가 거부된다")
    void requestIdIsMandatory() {
        assertThrows(IllegalArgumentException.class, () -> new OutboxEvent(
                "evt_x", EventType.PLACE_VIEW, 1, Producer.CLIENT,
                "usr_1", "trp_1", null,
                NOW.minusSeconds(1), NOW, Map.of()));
    }

    /** 🔴 DR-13 — 생산 책임이 뒤바뀌면 신뢰할 수 없는 값이 들어온다. */
    @Test
    @DisplayName("🔴 서버가 만들어야 하는 이벤트를 클라이언트가 보내면 거부된다")
    void producerMustMatch() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new OutboxEvent("evt_y", EventType.PLACE_LIKE, 1, Producer.CLIENT,
                        "usr_1", "trp_1", "req_1", NOW.minusSeconds(1), NOW, Map.of()));
        assertTrue(e.getMessage().contains("SERVER"), "누가 만들어야 하는지 알려줘야 한다");
    }

    /** 발생이 수신보다 뒤일 수는 없다 — 기기 시계가 틀렸거나 조작된 값이다. */
    @Test
    @DisplayName("발생 시각이 수신 시각보다 뒤면 거부된다")
    void occurredAtCannotBeAfterReceivedAt() {
        assertThrows(IllegalArgumentException.class, () -> new OutboxEvent(
                "evt_z", EventType.PLACE_VIEW, 1, Producer.CLIENT,
                "usr_1", "trp_1", "req_1",
                NOW.plusSeconds(60), NOW, Map.of()));
    }

    /**
     * 🔴 탈퇴 시 익명화 — 삭제가 아니다.
     *
     * <p>통째로 지우면 과거 오프라인 평가를 재현할 수 없다(NFR-08).
     * 익명화하면 집계와 발표 지표는 남고 개인은 사라진다.
     */
    @Test
    @DisplayName("🔴 탈퇴하면 userId 만 지워지고 이벤트는 남는다")
    void anonymizeKeepsEventDropsUser() {
        ingest("evt_1");
        service.ingestFromClient("evt_2", EventType.PLACE_VIEW, 1,
                "usr_other", "trp_1", "req_2", NOW.minusSeconds(1), Map.of());

        int changed = service.anonymizeUser("usr_1");

        assertEquals(1, changed, "그 사용자 것만 바뀌어야 한다");
        assertNull(repository.findById("evt_1").orElseThrow().userId(), "userId 는 사라진다");
        assertTrue(repository.findById("evt_1").isPresent(), "🔴 이벤트 자체는 남는다");
        assertEquals("usr_other", repository.findById("evt_2").orElseThrow().userId(),
                "남의 이벤트는 그대로여야 한다");
    }

    /** 🔴 2026-09-02 DATA 결정 — 실패 이벤트는 버전을 아는 것만 담는다. */
    @Test
    @DisplayName("🔴 recommendation_failed 는 버전을 아는 것만 담아도 된다")
    void failedEventAllowsPartialVersions() {
        assertEquals(EventType.VersionRequirement.BEST_EFFORT,
                EventType.RECOMMENDATION_FAILED.versionRequirement());
        assertEquals(Producer.SERVER, EventType.RECOMMENDATION_FAILED.expectedProducer(),
                "FE·APP 이 계측할 것이 없어야 한다 (DR-13)");
        assertTrue(EventType.RECOMMENDATION_FAILED.requiredForM1());

        assertEquals(EventType.VersionRequirement.RECOMMENDATION,
                EventType.RECOMMENDATION_IMPRESSION.versionRequirement(),
                "성공 이벤트의 버전 규칙은 안 흔들렸어야 한다");
    }

    @Test
    @DisplayName("이벤트 사전이 17종이고 M1 필수는 6종이다")
    void eventDictionaryIsFixed() {
        assertEquals(17, EventType.values().length, "합집합 16종 + recommendation_failed");
        long m1 = java.util.Arrays.stream(EventType.values())
                .filter(EventType::requiredForM1).count();
        assertEquals(6, m1, "M1 필수 5종 + recommendation_failed");
    }
}
