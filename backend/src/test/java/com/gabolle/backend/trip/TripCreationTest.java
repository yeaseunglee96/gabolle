package com.gabolle.backend.trip;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.user.support.ConsentGuards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 여행 생성 - S15P21E201-461 TRIP-01. 티켓의 완료 기준을 그대로 검증한다. */
class TripCreationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    /**
     * 🔴 민감 제약을 저장하는 검사만 이 값을 쓴다 — S15P21E201-549.
     *
     * <p>다른 검사들이 쓰는 {@code "usr_1"} 은 UUID 가 아니다. 건강 동의 검사는 사용자를
     * UUID 로 찾는데, 형식이 아니면 <b>동의를 확인할 수 없으므로 막는 쪽</b>이라 그 값으로는
     * 403 이 난다. 운영에서는 {@code TripController} 가 인증 주체({@code UUID})를 문자열로
     * 바꿔 넘기므로 언제나 UUID 다 — {@code "usr_1"} 이 검사 전용 값이었을 뿐이다.
     *
     * <p>민감 제약을 안 쓰는 검사는 가드를 지나지 않으므로 그대로 {@code "usr_1"} 을 쓴다.
     */
    private static final String CONSENTING_USER = UUID.randomUUID().toString();

    private InMemoryTripRepository repository;
    private TripCreationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTripRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new TripCreationService(repository, clock,
                new PreferenceDefaultsService(repository, clock), ConsentGuards.granting());
    }

    private TripCreationService.Command command() {
        return new TripCreationService.Command(
                "usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604,
                300000, 2,
                "MORNING_TO_EVENING", "Asia/Seoul",
                List.of(
                        new PreferenceSnapshot.PreferenceAnswer(
                                "pace", "RELAXED", PreferenceSnapshot.AnswerStatus.SELECTED),
                        new PreferenceSnapshot.PreferenceAnswer(
                                "theme", "NATURE", PreferenceSnapshot.AnswerStatus.SELECTED)),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.HARD, "LTE", null, 5000.0,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED, null)));
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

    @Test
    @DisplayName("만든 사람이 OWNER 로 자동 등록된다")
    void creatorBecomesOwner() {
        var result = service.create(command(), null);

        List<TripMember> members = repository.findMembers(result.trip().tripId());
        assertEquals(1, members.size());
        assertEquals("usr_1", members.get(0).userId());
        assertEquals(TripMember.Role.OWNER, members.get(0).role());
        assertTrue(members.get(0).role().canEdit());
    }

    @Test
    @DisplayName("선호 스냅샷 v1 이 함께 굳는다 - UUID 와 version 을 둘 다 갖는다")
    void preferenceSnapshotIsFrozen() {
        var result = service.create(command(), null);

        var snapshot = repository.findLatestSnapshot(result.trip().tripId()).orElseThrow();
        assertEquals(1, snapshot.version(), "REC-01 이 받는 값");
        assertFalse(snapshot.snapshotId().isBlank(), "로그와 이벤트가 가리키는 값");
        assertEquals(PersonalizationScope.TRIP, snapshot.scope(), "TRIP-01 이 만드는 스냅샷은 항상 이번 여행 전용이다");

        var pace = snapshot.answers().stream()
                .filter(a -> a.dimension().equals("pace")).findFirst().orElseThrow();
        assertEquals("RELAXED", pace.valueJson());
        assertEquals(PreferenceSnapshot.AnswerStatus.SELECTED, pace.status());
        assertEquals(1, snapshot.constraintIds().size(), "그때의 제약도 함께 굳는다");

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.answers().add(new PreferenceSnapshot.PreferenceAnswer(
                        "x", "y", PreferenceSnapshot.AnswerStatus.SELECTED)));
    }

    // 2026-09-03 - SELECTED/SKIPPED/UNKNOWN 구분 (고지혁 님 실측)

    @Test
    @DisplayName("건너뛴 취향과 안 물어본 취향이 값 없이 저장되고 서로 구분된다")
    void skippedAndUnknownPreferencesAreDistinctWithoutValues() {
        var withSkipAndUnknown = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, // 출발지 좌표 - 이 검사가 재는 것은 취향 상태 구분이지 좌표가 아니다
                List.of(
                        new PreferenceSnapshot.PreferenceAnswer("theme", null, PreferenceSnapshot.AnswerStatus.SKIPPED),
                        new PreferenceSnapshot.PreferenceAnswer("locality", null, PreferenceSnapshot.AnswerStatus.UNKNOWN)),
                List.of());

        var result = service.create(withSkipAndUnknown, null);
        var snapshot = repository.findLatestSnapshot(result.trip().tripId()).orElseThrow();

        var theme = snapshot.answers().stream().filter(a -> a.dimension().equals("theme")).findFirst().orElseThrow();
        var locality = snapshot.answers().stream().filter(a -> a.dimension().equals("locality")).findFirst().orElseThrow();
        assertEquals(PreferenceSnapshot.AnswerStatus.SKIPPED, theme.status());
        assertEquals(PreferenceSnapshot.AnswerStatus.UNKNOWN, locality.status());
        assertNotEquals(theme.status(), locality.status(), "이전에는 둘 다 키 없음으로 뭉개졌다");
    }

    @Test
    @DisplayName("SELECTED 인데 값이 없는 취향 답은 거부된다")
    void selectedPreferenceWithoutValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PreferenceSnapshot.PreferenceAnswer("pace", null, PreferenceSnapshot.AnswerStatus.SELECTED));
    }

    @Test
    @DisplayName("SKIPPED 인데 값이 있는 취향 답은 거부된다")
    void skippedPreferenceWithValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new PreferenceSnapshot.PreferenceAnswer("pace", "RELAXED", PreferenceSnapshot.AnswerStatus.SKIPPED));
    }

    @Test
    @DisplayName("제약도 없다와 안 물어봄을 값 없이 구분해 저장한다")
    void constraintNoneAndUnknownAreDistinctWithoutValues() {
        var withNoneAndUnknown = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), // 출발지 좌표 - 이 검사가 재는 것은 제약 상태 구분이지 좌표가 아니다
                List.of(
                        new TripCreationService.Command.ConstraintInput("DIET", "HALAL", TripConstraint.Severity.SOFT,
                                null, null, null, TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                                TripConstraint.AnswerStatus.NONE, null),
                        new TripCreationService.Command.ConstraintInput("MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.SOFT,
                                null, null, null, TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                                TripConstraint.AnswerStatus.UNKNOWN, null)));

        var result = service.create(withNoneAndUnknown, null);
        List<TripConstraint> constraints = repository.findConstraints(result.trip().tripId());

        var diet = constraints.stream().filter(c -> c.type().equals("DIET")).findFirst().orElseThrow();
        var mobility = constraints.stream().filter(c -> c.type().equals("MOBILITY")).findFirst().orElseThrow();
        assertEquals(TripConstraint.AnswerStatus.NONE, diet.answerStatus());
        assertEquals(TripConstraint.AnswerStatus.UNKNOWN, mobility.answerStatus());
    }

    @Test
    @DisplayName("SELECTED 인데 값도 임계치도 없는 제약은 거부된다")
    void selectedConstraintWithoutValueOrThresholdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TripConstraint("c1", "trp_1", "MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.SOFT,
                        null, null, null, TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                        TripConstraint.AnswerStatus.SELECTED, PersonalizationScope.TRIP, null));
    }

    @Test
    @DisplayName("NONE 인데 임계치가 있는 제약은 거부된다")
    void noneConstraintWithThresholdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TripConstraint("c1", "trp_1", "MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.SOFT,
                        "LTE", null, 5000.0, TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                        TripConstraint.AnswerStatus.NONE, PersonalizationScope.TRIP, null));
    }

    @Test
    @DisplayName("알레르기는 SOFT 로 저장할 수 없다 - 항상 HARD 다")
    void allergyMustBeHard() {
        assertThrows(IllegalArgumentException.class,
                () -> new TripConstraint("c1", "trp_1", "ALLERGY", "PEANUT", TripConstraint.Severity.SOFT,
                        null, null, null, TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                        TripConstraint.AnswerStatus.NONE, PersonalizationScope.TRIP, null));
    }

    // 기존 완료 기준

    @Test
    @DisplayName("같은 Idempotency-Key 로 두 번 보내도 여행이 하나만 만들어진다")
    void sameIdempotencyKeyReturnsSameTrip() {
        var first = service.create(command(), "key_1");
        var second = service.create(command(), "key_1");

        assertTrue(first.created(), "첫 요청은 새로 만든다");
        assertFalse(second.created(), "재시도는 기존 것을 돌려준다");
        assertEquals(first.trip().tripId(), second.trip().tripId());
        assertEquals(1, repository.tripCount(), "여행은 하나뿐이다");
    }

    @Test
    @DisplayName("같은 키를 다른 조건으로 재사용하면 거부된다")
    void sameKeyDifferentBodyIsRejected() {
        service.create(command(), "key_1");

        var different = new TripCreationService.Command(
                "usr_1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3),
                35.1587, 129.1604, 300000, 2,
                "MORNING_TO_EVENING", "Asia/Seoul", List.of(), List.of());

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

    @Test
    @DisplayName("같은 키를 8개가 동시에 보내도 새로 만든 것은 하나뿐이다")
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
                        // 경쟁에서 진 쪽 - 여행을 만들지 않았다는 것이 중요하다
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

        assertEquals(1, created.get(), "새로 만든 것은 정확히 하나여야 한다");
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞이면 거부된다")
    void finishBeforeStartIsRejected() {
        var bad = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 6),
                null, null, null, 1, null, null, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.create(bad, null));
    }

    @Test
    @DisplayName("인원이 0명이면 거부된다")
    void zeroPartySizeIsRejected() {
        var bad = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                null, null, null, 0, null, null, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> service.create(bad, null));
    }

    @Test
    @DisplayName("알레르기 자유 입력(OTHER)은 거부된다 - 암호화 경로가 없다")
    void sensitiveFreeTextConstraintIsRejected() {
        var withAllergy = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), // 출발지 좌표 - 이 검사가 재는 것은 민감 제약 거부이지 좌표가 아니다
                List.of(new TripCreationService.Command.ConstraintInput(
                        "ALLERGY", "OTHER", TripConstraint.Severity.HARD, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.NONE, null)));

        assertThrows(TripConstraint.SensitiveConstraintNotSupportedException.class,
                () -> service.create(withAllergy, null));
    }

    // 건강·식이 동의 (S15P21E201-549)

    /**
     * 🔴 이 검사가 없을 때 무엇이 통과했나.
     *
     * <p>{@code ConsentType.HEALTH_CONSTRAINTS} 는 열거형과 응답 DTO 에만 있었고 아무도 안
     * 봤다. 동의를 한 번도 안 한 사람의 알레르기가 그대로 표에 들어갔다. 자유 입력 거부는
     * 평문 보관을 막는 것이지 <b>동의 없는 수집</b>을 막는 것이 아니다 — 코드로 된 값은
     * 그 거부를 지나간다.
     */
    @Test
    @DisplayName("🔴 건강 동의가 없으면 알레르기가 저장되지 않는다 - 여행도 안 만들어진다")
    void sensitiveConstraintNeedsHealthConsent() {
        TripCreationService refusing = new TripCreationService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                new PreferenceDefaultsService(repository, Clock.fixed(NOW, ZoneOffset.UTC)),
                ConsentGuards.refusing());

        var withAllergy = new TripCreationService.Command(CONSENTING_USER,
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "ALLERGY", "PEANUT", TripConstraint.Severity.HARD, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED, null)));

        var refused = assertThrows(AuthException.class, () -> refusing.create(withAllergy, null));
        assertEquals("HEALTH_CONSENT_REQUIRED", refused.getCode());
        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    @DisplayName("민감하지 않은 제약은 건강 동의 없이도 저장된다 - 이동 제약까지 막으면 안 된다")
    void ordinaryConstraintDoesNotNeedHealthConsent() {
        TripCreationService refusing = new TripCreationService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                new PreferenceDefaultsService(repository, Clock.fixed(NOW, ZoneOffset.UTC)),
                ConsentGuards.refusing());

        var result = refusing.create(command(), null);

        assertEquals(1, repository.findConstraints(result.trip().tripId()).size());
    }

    /**
     * 🔴 {@code DIET} 는 {@code dietRequirement} 가 민감 여부를 가른다 — 종류 이름만으로는
     * 안 갈린다. 그 판정을 {@code TripConstraint.isSensitive} 에 맡기고 있다는 것을 여기서
     * 확인한다. 목록을 여기 다시 적으면 두 벌이 되고, 한쪽만 늘어나는 날 조용히 새어 나간다.
     */
    @Test
    @DisplayName("DIET+PREFERRED 는 건강 동의 없이도 저장된다 - 민감한 것은 REQUIRED 뿐이다")
    void preferredDietDoesNotNeedHealthConsent() {
        TripCreationService refusing = new TripCreationService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                new PreferenceDefaultsService(repository, Clock.fixed(NOW, ZoneOffset.UTC)),
                ConsentGuards.refusing());

        var withPreferredDiet = new TripCreationService.Command(CONSENTING_USER,
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "DIET", "VEGETARIAN", TripConstraint.Severity.SOFT, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED,
                        TripConstraint.DietRequirement.PREFERRED)));

        var result = refusing.create(withPreferredDiet, null);

        assertEquals("VEGETARIAN", repository.findConstraints(result.trip().tripId()).get(0).constraintKey());
    }

    @Test
    @DisplayName("2026-09-04 회귀 - 코드로 된 알레르기(PEANUT)는 저장된다 (고지혁 님 리뷰)")
    void codedAllergyConstraintIsAllowed() {
        var withAllergy = new TripCreationService.Command(CONSENTING_USER,
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), // 출발지 좌표 - 이 검사가 재는 것은 코드로 된 알레르기 저장이지 좌표가 아니다
                List.of(new TripCreationService.Command.ConstraintInput(
                        "ALLERGY", "PEANUT", TripConstraint.Severity.HARD, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED, null)));

        var result = service.create(withAllergy, null);
        var allergy = repository.findConstraints(result.trip().tripId()).get(0);
        assertEquals("PEANUT", allergy.constraintKey());
    }

    @Test
    @DisplayName("2026-09-04 회귀 - DIET+REQUIRED 자유 입력은 거부된다 (HEALTH_DIET 판정 구멍 수정)")
    void requiredDietConstraintIsRejected() {
        var withRequiredDiet = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), // 출발지 좌표 - 이 검사가 재는 것은 DIET+REQUIRED 자유 입력 거부이지 좌표가 아니다
                List.of(new TripCreationService.Command.ConstraintInput(
                        "DIET", "OTHER", TripConstraint.Severity.HARD, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.NONE,
                        TripConstraint.DietRequirement.REQUIRED)));

        assertThrows(TripConstraint.SensitiveConstraintNotSupportedException.class,
                () -> service.create(withRequiredDiet, null));
    }

    @Test
    @DisplayName("코드로 된 DIET+REQUIRED(예: HALAL)는 저장된다 - 구조화된 값이다")
    void codedRequiredDietConstraintIsAllowed() {
        var withHalal = new TripCreationService.Command(CONSENTING_USER,
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), // 출발지 좌표 - 이 검사가 재는 것은 코드로 된 DIET+REQUIRED 저장이지 좌표가 아니다
                List.of(new TripCreationService.Command.ConstraintInput(
                        "DIET", "HALAL", TripConstraint.Severity.HARD, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED,
                        TripConstraint.DietRequirement.REQUIRED)));

        var result = service.create(withHalal, null);
        var diet = repository.findConstraints(result.trip().tripId()).get(0);
        assertEquals("HALAL", diet.constraintKey());
    }

    @Test
    @DisplayName("DIET+PREFERRED 는 값을 보내도 저장된다 - 민감하지 않다")
    void preferredDietConstraintIsAllowed() {
        var withPreferredDiet = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), // 출발지 좌표 - 이 검사가 재는 것은 DIET+PREFERRED 저장이지 좌표가 아니다
                List.of(new TripCreationService.Command.ConstraintInput(
                        "DIET", "VEGETARIAN", TripConstraint.Severity.SOFT, "EXCLUDES", null, null,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED,
                        TripConstraint.DietRequirement.PREFERRED)));

        var result = service.create(withPreferredDiet, null);
        var diet = repository.findConstraints(result.trip().tripId()).get(0);
        assertEquals(TripConstraint.DietRequirement.PREFERRED, diet.dietRequirement());
    }

    @Test
    @DisplayName("dietRequirement 는 DIET 가 아니면 거부된다")
    void dietRequirementOnNonDietTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new TripConstraint("c1", "trp_1", "MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.HARD,
                        "LTE", null, 5000.0, TripConstraint.EvidenceStatus.NEEDS_REVIEW,
                        TripConstraint.AnswerStatus.SELECTED, PersonalizationScope.TRIP,
                        TripConstraint.DietRequirement.REQUIRED));
    }

    @Test
    @DisplayName("HARD 제약에 비교 방법이 없으면 거부된다 - 판정할 수 없다")
    void hardConstraintNeedsOperator() {
        var noOperator = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                null, null, null, 1, null, null, List.of(),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.HARD, null, null, 5000.0,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED, null)));

        assertThrows(IllegalArgumentException.class, () -> service.create(noOperator, null));
    }
}
