package com.gabolle.backend.trip;

import java.util.Optional;
import com.gabolle.backend.user.support.ConsentGuards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TripQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    private InMemoryTripRepository repository;
    private TripCreationService creationService;
    private TripQueryService queryService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTripRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        creationService = new TripCreationService(repository, clock,
                new PreferenceDefaultsService(repository, clock), ConsentGuards.granting(), Optional.empty(), Optional.empty());
        queryService = new TripQueryService(repository);
    }

    private TripCreationService.Command command() {
        return new TripCreationService.Command(
                "usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604,
                300000, 2,
                "MORNING_TO_EVENING", "Asia/Seoul",
                List.of(new PreferenceSnapshot.PreferenceAnswer(
                        "pace", "RELAXED", PreferenceSnapshot.AnswerStatus.SELECTED)),
                List.of(new TripCreationService.Command.ConstraintInput(
                        "MOBILITY", "MAX_WALKING_METERS", TripConstraint.Severity.HARD, "LTE", null, 5000.0,
                        TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED, null)));
    }

    @Test
    @DisplayName("만든 사람이 조회하면 보낸 조건이 그대로 나온다")
    void ownerReadsBackWhatWasSent() {
        var created = creationService.create(command(), "key_1");

        var view = queryService.get(created.trip().tripId(), "usr_1");

        assertEquals(LocalDate.of(2026, 9, 6), view.trip().startDate());
        assertEquals(300000, view.trip().budgetKrw());
        assertEquals(1, view.constraints().size());
        assertEquals("MOBILITY", view.constraints().get(0).type());
        // 만든 사람은 trip_member 에 OWNER 로 들어간다(TripCreationService).
        assertEquals(TripMember.Role.OWNER, view.role());

        var pace = view.snapshot().answers().stream()
                .filter(a -> a.dimension().equals("pace")).findFirst().orElseThrow();
        assertEquals("RELAXED", pace.valueJson());
    }

    @Test
    @DisplayName("없는 여행을 조회하면 TripNotFoundException")
    void unknownTripIsNotFound() {
        assertThrows(TripQueryService.TripNotFoundException.class,
                () -> queryService.get("trp_unknown", "usr_1"));
    }

    @Test
    @DisplayName("회원이 아니면 있어도 TripNotFoundException - 존재를 확인해 주지 않는다")
    void nonMemberGetsTheSameNotFoundAsMissing() {
        var created = creationService.create(command(), "key_1");

        assertThrows(TripQueryService.TripNotFoundException.class,
                () -> queryService.get(created.trip().tripId(), "usr_stranger"));
    }

    @Test
    @DisplayName("조건 없이 만든 여행을 조회하면 빈 목록이 나온다 - null 이 아니다")
    void tripWithNoConstraintsReturnsEmptyListNotNull() {
        var noConstraints = new TripCreationService.Command("usr_1",
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
                35.1587, 129.1604, null, 1, null, null, List.of(), List.of()); // 출발지 좌표 - 이 검사가 재는 것은 빈 제약 목록이지 좌표가 아니다
        var created = creationService.create(noConstraints, null);

        var view = queryService.get(created.trip().tripId(), "usr_1");

        assertTrue(view.constraints().isEmpty());
    }
}
