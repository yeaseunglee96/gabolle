package com.gabolle.backend.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 여행 생성 — S15P21E201-461 · TRIP-01. 티켓의 완료 기준을 그대로 검증한다. */
class TripCreationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private InMemoryTripRepository repository;
    private TripCreationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTripRepository();
        service = new TripCreationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TripCreationService.Command command() {
        return new TripCreationService.Command(
                "usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604,
                300000, 2,
                "MORNING_TO_EVENING", "Asia/Seoul",
                Map.of("pace", "RELAXED", "theme", "NATURE"),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "MOBILITY", TripConstraint.Severity.HARD, "LTE", null, 5000.0,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW)));
    }

    @Test
    @DisplayName("조건을 저장하면 식별자가 돌아오고 보낸 조건이 그대로 나온다")
    void savesAndReadsBackConditions() {
        var result = service.create(command(), "key_1");

        assertTrue(result.created());
        var trip = repository.findById(result.trip().tripId()).orElseThrow();

        assertEquals(LocalDate.of(2026, 9, 6), trip.startDate());
        assertEquals(LocalDate.of(2026, 9, 8), trip.finishDate());
        assertEquals(300000, trip.budgetKrw());
        assertEquals(2, trip.partySize());
        assertEquals("Asia/Seoul", trip.timezone());
        assertEquals(3, trip.days(), "9월 6일부터 8일까지는 3일이다");
        assertEquals(1, repository.findConstraints(trip.tripId()).size());
    }

    /**
     * 🔴 만든 사람이 OWNER 로 들어가야 한다.
     *
     * <p>안 들어가면 자기 여행을 못 본다 — 조회 권한 판정이 이 표를 본다(FR-SEC-01).
     * 별도 호출로 두면 언젠가 빠뜨리므로 생성과 같은 트랜잭션에서 만든다.
     */
    @Test
    @DisplayName("🔴 만든 사람이 OWNER 로 자동 등록된다")
    void creatorBecomesOwner() {
        var result = service.create(command(), null);

        List<TripMember> members = repository.findMembers(result.trip().tripId());
        assertEquals(1, members.size());
        assertEquals("usr_1", members.get(0).userId());
        assertEquals(TripMember.Role.OWNER, members.get(0).role());
        assertTrue(members.get(0).role().canEdit());
    }

    /**
     * 🔴 선호 스냅샷 v1 이 함께 굳는다.
     *
     * <p>계정 취향은 계속 바뀌지만 이 여행은 그때의 취향으로 만들어진 것이다.
     * 복사해 두지 않으면 3주 뒤에 왜 이 일정이 나왔는지 답할 수 없다 (NFR-08).
     */
    @Test
    @DisplayName("🔴 선호 스냅샷 v1 이 함께 굳는다 — UUID 와 version 을 둘 다 갖는다")
    void preferenceSnapshotIsFrozen() {
        var result = service.create(command(), null);

        var snapshot = repository.findLatestSnapshot(result.trip().tripId()).orElseThrow();
        assertEquals(1, snapshot.version(), "REC-01 이 받는 값");
        assertFalse(snapshot.snapshotId().isBlank(), "로그와 이벤트가 가리키는 값");
        assertEquals("RELAXED", snapshot.dimensions().get("pace"));
        assertEquals(1, snapshot.constraintIds().size(), "그때의 제약도 함께 굳는다");

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.dimensions().put("pace", "PACKED"));
    }

    /** 🔴 티켓 핵심 완료 기준 — 같은 요청을 두 번 보내도 여행이 하나만. */
    @Test
    @DisplayName("🔴 같은 Idempotency-Key 로 두 번 보내도 여행이 하나만 만들어진다")
    void sameIdempotencyKeyReturnsSameTrip() {
        var first = service.create(command(), "key_1");
        var second = service.create(command(), "key_1");

        assertTrue(first.created(), "첫 요청은 새로 만든다");
        assertFalse(second.created(), "재시도는 기존 것을 돌려준다");
        assertEquals(first.trip().tripId(), second.trip().tripId());
        assertEquals(1, repository.tripCount(), "🔴 여행은 하나뿐이다");
    }

    /** 🔴 API-09 — 같은 키를 다른 본문으로 재사용하면 409 다. */
    @Test
    @DisplayName("🔴 같은 키를 다른 조건으로 재사용하면 거부된다")
    void sameKeyDifferentBodyIsRejected() {
        service.create(command(), "key_1");

        var different = new TripCreationService.Command(
                "usr_1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                35.1587, 129.1604, 300000, 2,
                "MORNING_TO_EVENING", "Asia/Seoul", Map.of(), List.of());

        assertThrows(TripRepository.IdempotencyKeyConflictException.class,
                () -> service.create(different, "key_1"));
    }

    @Test
    @DisplayName("Idempotency-Key 가 없으면 매번 새 여행이 만들어진다")
    void withoutKeyEachRequestCreatesNewTrip() {
        var a = service.create(command(), null);
        var b = service.create(command(), null);

        assertNotEquals(a.trip().tripId(), b.trip().tripId());
        assertEquals(2, repository.tripCount());
    }

    /**
     * 🔴 같은 키가 동시에 들어와도 하나만 만들어진다.
     *
     * <p>먼저 확인하고 넣는 방식만으로는 부족하다 — 확인과 저장 사이에 다른 요청이
     * 끼어들 수 있다. 마지막 방어선은 putIfAbsent 이고, DB 에서는
     * UNIQUE (user_id, idempotency_key) 가 그 자리를 대신한다.
     */
    @Test
    @DisplayName("🔴 같은 키를 8개가 동시에 보내도 새로 만든 것은 하나뿐이다")
    void concurrentSameKeyCreatesOneTrip() throws Exception {
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    go.await();
                    try {
                        if (service.create(command(), "key_same").created()) {
                            created.incrementAndGet();
                        }
                    } catch (RuntimeException ignored) {
                        // 경쟁에서 진 쪽 — 여행을 만들지 않았다는 것이 중요하다
                    }
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, created.get(), "🔴 새로 만든 것은 정확히 하나여야 한다");
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞이면 거부된다")
    void finishBeforeStartIsRejected() {
        var bad = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 6),
                null, null, null, 1, null, null, Map.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.create(bad, null));
    }

    @Test
    @DisplayName("인원이 0명이면 거부된다")
    void zeroPartySizeIsRejected() {
        var bad = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                null, null, null, 0, null, null, Map.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.create(bad, null));
    }

    /**
     * 🔴 민감 제약은 M1 에서 받지 않는다.
     *
     * <p>암호화 경로가 없고, 평문으로 한 번 저장하면 그 데이터가 남는다.
     * 나중에 처리하겠다고 넘기지 않는다.
     */
    @Test
    @DisplayName("🔴 알레르기 값을 보내면 거부된다 — 암호화 경로가 없다")
    void sensitiveConstraintIsRejected() {
        var withAllergy = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                null, null, null, 1, null, null, Map.of(),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "ALLERGY", TripConstraint.Severity.HARD, "EXCLUDES", "peanut", null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW)));

        assertThrows(TripConstraint.SensitiveConstraintNotSupportedException.class,
                () -> service.create(withAllergy, null));
    }

    @Test
    @DisplayName("HARD 제약에 비교 방법이 없으면 거부된다 — 판정할 수 없다")
    void hardConstraintNeedsOperator() {
        var noOperator = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                null, null, null, 1, null, null, Map.of(),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "MOBILITY", TripConstraint.Severity.HARD, null, null, 5000.0,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW)));

        assertThrows(IllegalArgumentException.class, () -> service.create(noOperator, null));
    }
}
