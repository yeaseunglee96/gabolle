package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.PaceFactorCalculator;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;

/**
 * 개인 속도 계수 계산 규칙 — S15P21E201-304.
 *
 * <p>Spring 도 DB 도 안 띄운다. 이 계산은 순수 함수라 그 둘이 있어도 재는 것이 늘어나지
 * 않고, 대신 한 번 도는 데 몇 밀리초라 갈래를 촘촘히 재도 부담이 없다.
 *
 * <p>검사 이름은 티켓의 완료 기준 문장을 그대로 옮긴 것이 넷이고, 그 문장들이 조용히
 * 통과하지 못하도록 막는 것이 나머지다.
 */
class PaceFactorCalculatorTest {

    private static final String ITINERARY_ID = "itn-1";

    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 9, 10);

    private static final Instant RECORDED_BASE = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    @DisplayName("완료 기준 — 기록 5건이면 계수가 나온다")
    void fiveRecordsProduceAFactor() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 90);
        fixture.visit(2, 60, 90);
        fixture.visit(3, 60, 90);
        fixture.visit(4, 60, 90);
        fixture.visit(5, 60, 90);

        Optional<PaceFactorCalculator.Result> result = fixture.calculate();

        assertThat(result).isPresent();
        assertThat(result.get().factor()).isEqualByComparingTo(new BigDecimal("1.50"));
        assertThat(result.get().sampleCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("완료 기준 — 기록 2건에서는 계수가 만들어지지 않는다")
    void twoRecordsAreNotEnough() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 120);
        fixture.visit(2, 60, 120);

        assertThat(fixture.calculate()).isEmpty();
    }

    @Test
    @DisplayName("경계 — 기록 3건은 계수를 만든다")
    void threeRecordsAreTheThreshold() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 60);
        fixture.visit(2, 60, 90);
        fixture.visit(3, 60, 120);

        Optional<PaceFactorCalculator.Result> result = fixture.calculate();

        assertThat(result).isPresent();
        assertThat(result.get().sampleCount()).isEqualTo(PaceFactorCalculator.MIN_SAMPLES);
        // 배수 1.0 · 1.5 · 2.0 의 가운데
        assertThat(result.get().factor()).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    @DisplayName("한 곳에서 오래 머문 하루가 계수를 통째로 끌어올리지 않는다")
    void oneLongStayDoesNotDominate() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 60);
        fixture.visit(2, 60, 60);
        fixture.visit(3, 60, 66);
        fixture.visit(4, 60, 60);
        // 점심을 다섯 시간 먹은 날. 평균이면 배수가 2를 넘는다.
        fixture.visit(5, 60, 600);

        BigDecimal factor = fixture.calculate().orElseThrow().factor();

        // 배수는 1.0 · 1.0 · 1.1 · 1.0 · 10.0 이다. 가운데 값은 1.00 이라 그 한 점이
        // 못 옮긴다. 평균이었다면 2.82 로 "두 배 넘게 느린 사람" 이 됐을 것이다.
        assertThat(factor).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(factor).isLessThan(new BigDecimal("1.50"));
    }

    @Test
    @DisplayName("도착만 찍힌 기록은 표본이 아니다 — 머문 시간을 모른다")
    void arrivalWithoutDepartureIsNotASample() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 90);
        fixture.visit(2, 60, 90);
        fixture.visit(3, 60, 90);
        fixture.arrivalOnly(4);

        assertThat(fixture.calculate().orElseThrow().sampleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("계획 머문 시간이 없는 항목은 표본이 아니다 — 나눌 값이 없다")
    void itemWithoutPlannedStayIsNotASample() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 90);
        fixture.visit(2, 60, 90);
        fixture.visit(3, 60, 90);
        // 사용자가 손으로 더한 장소는 시각이 비어 있다(ItineraryRevision.withAddedItem).
        fixture.visitWithoutPlannedTime(4);

        assertThat(fixture.calculate().orElseThrow().sampleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("계획 시간이 없어도 시작·끝 시각이 있으면 표본이 된다")
    void plannedStayFallsBackToTheTimePair() {
        Fixture fixture = new Fixture();
        fixture.visitWithTimesOnly(1, LocalTime.of(9, 0), LocalTime.of(10, 0), 120);
        fixture.visitWithTimesOnly(2, LocalTime.of(11, 0), LocalTime.of(12, 0), 120);
        fixture.visitWithTimesOnly(3, LocalTime.of(13, 0), LocalTime.of(14, 0), 120);

        Optional<PaceFactorCalculator.Result> result = fixture.calculate();

        assertThat(result).isPresent();
        assertThat(result.get().factor()).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    @DisplayName("표본으로 쓴 기록의 마지막 시각까지만 봤다고 답한다")
    void observedUntilIsTheLatestSampledRecord() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 90);
        fixture.visit(2, 60, 90);
        fixture.visit(3, 60, 90);
        // 표본이 되지 못한 기록이 더 나중에 적혔다. 이 시각을 "봤다" 고 답하면
        // 다음 계산이 아직 안 쓴 기록을 건너뛴다.
        fixture.arrivalOnly(9);

        Instant observedUntil = fixture.calculate().orElseThrow().observedUntil();

        assertThat(observedUntil).isEqualTo(RECORDED_BASE.plusSeconds(3));
    }

    @Test
    @DisplayName("말이 안 되는 배수는 한계에서 잘린다 — 사람이 느린 게 아니라 기록이 틀린 것이다")
    void absurdRatiosAreClamped() {
        Fixture fixture = new Fixture();
        // 출발을 다음 날 찍은 기록 셋. 배수가 24를 넘는다.
        fixture.visit(1, 60, 60 * 25);
        fixture.visit(2, 60, 60 * 25);
        fixture.visit(3, 60, 60 * 25);

        BigDecimal factor = fixture.calculate().orElseThrow().factor();

        assertThat(factor).isEqualByComparingTo(PaceFactorCalculator.MAX_FACTOR);
    }

    @Test
    @DisplayName("도착과 출발이 같은 분에 찍힌 기록은 표본이 아니다")
    void zeroLengthVisitIsNotASample() {
        Fixture fixture = new Fixture();
        fixture.visit(1, 60, 90);
        fixture.visit(2, 60, 90);
        fixture.visit(3, 60, 90);
        fixture.visit(4, 60, 0);

        assertThat(fixture.calculate().orElseThrow().sampleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("기록이 하나도 없으면 계수가 없다 — 1.0 을 지어내지 않는다")
    void noRecordsMeansNoFactor() {
        Fixture fixture = new Fixture();
        fixture.plannedOnly(1);
        fixture.plannedOnly(2);
        fixture.plannedOnly(3);

        assertThat(fixture.calculate()).isEmpty();
    }

    /** 항목과 기록을 짝지어 쌓는 픽스처. 검사마다 몇 줄로 상황을 세우려고 둔다. */
    private static final class Fixture {

        private final List<ItineraryItem> items = new ArrayList<>();

        private final List<ItineraryItemActual> actuals = new ArrayList<>();

        Optional<PaceFactorCalculator.Result> calculate() {
            return PaceFactorCalculator.calculate(items, actuals);
        }

        /** 계획 {@code plannedMinutes} 분인 방문지에 실제로 {@code actualMinutes} 분 머물렀다. */
        void visit(int sequence, int plannedMinutes, int actualMinutes) {
            String itemKey = "item-" + sequence;
            items.add(item(itemKey, sequence, plannedMinutes, LocalTime.of(9, 0), LocalTime.of(10, 0)));
            Instant arrived = Instant.parse("2026-09-10T00:00:00Z").plusSeconds(sequence * 3600L);
            actuals.add(new ItineraryItemActual(ITINERARY_ID, itemKey, arrived,
                    arrived.plusSeconds(actualMinutes * 60L), "user-1",
                    RECORDED_BASE.plusSeconds(sequence)));
        }

        void visitWithTimesOnly(int sequence, LocalTime start, LocalTime end, int actualMinutes) {
            String itemKey = "item-" + sequence;
            items.add(item(itemKey, sequence, null, start, end));
            Instant arrived = Instant.parse("2026-09-10T00:00:00Z").plusSeconds(sequence * 3600L);
            actuals.add(new ItineraryItemActual(ITINERARY_ID, itemKey, arrived,
                    arrived.plusSeconds(actualMinutes * 60L), "user-1",
                    RECORDED_BASE.plusSeconds(sequence)));
        }

        /** 도착만 찍혔다. 아직 그 자리에 있거나 출발을 안 눌렀다. */
        void arrivalOnly(int sequence) {
            String itemKey = "item-" + sequence;
            items.add(item(itemKey, sequence, 60, LocalTime.of(9, 0), LocalTime.of(10, 0)));
            actuals.add(new ItineraryItemActual(ITINERARY_ID, itemKey,
                    Instant.parse("2026-09-10T05:00:00Z"), null, "user-1",
                    RECORDED_BASE.plusSeconds(sequence)));
        }

        /** 시각이 비어 있는 항목에 기록만 있다. */
        void visitWithoutPlannedTime(int sequence) {
            String itemKey = "item-" + sequence;
            items.add(item(itemKey, sequence, null, null, null));
            Instant arrived = Instant.parse("2026-09-10T06:00:00Z");
            actuals.add(new ItineraryItemActual(ITINERARY_ID, itemKey, arrived,
                    arrived.plusSeconds(3600), "user-1", RECORDED_BASE.plusSeconds(sequence)));
        }

        /** 계획만 있고 아직 안 간 방문지. */
        void plannedOnly(int sequence) {
            items.add(item("item-" + sequence, sequence, 60, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        }

        private static ItineraryItem item(String itemKey, int sequence, Integer stayMinutes,
                LocalTime start, LocalTime end) {
            return new ItineraryItem("id-" + itemKey, "ver-1", itemKey, 0, VISIT_DATE, sequence,
                    "place-" + sequence, start, end, stayMinutes, false, null,
                    ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null,
                    Instant.parse("2026-09-09T00:00:00Z"));
        }
    }
}
