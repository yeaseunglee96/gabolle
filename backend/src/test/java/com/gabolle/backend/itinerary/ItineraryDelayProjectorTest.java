package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ItineraryDelayProjector;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;

/**
 * 남은 방문지의 예상 도착 시각과 지연 위험.
 *
 * <p>하루를 이렇게 세워 두고 잰다(계획).
 *
 * <pre>
 *   09:00-10:00  A   (60분)      ← 들어오는 구간 없음
 *   10:30-11:30  B   (60분)      ← A→B 30분
 *   12:00-13:00  C   (60분)      ← B→C 30분
 * </pre>
 *
 * 하루 끝은 C 의 계획 종료인 13:00 이다.
 */
class ItineraryDelayProjectorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    private static final String ITINERARY_ID = "itn-1";

    /** 아직 아무 데도 안 간 시각. 계획 시작보다 이르다. */
    private static final Instant BEFORE_THE_DAY = at(LocalTime.of(6, 0));

    @Test
    @DisplayName("완료 기준 — 계수가 있으면 예상 도착 시각이 계획과 달라진다")
    void withAFactorPredictedArrivalsDivergeFromThePlan() {
        Fixture fixture = new Fixture();

        ItineraryDelayProjector.Projection projection = fixture.project(new BigDecimal("1.50"), BEFORE_THE_DAY);

        // A 는 첫 항목이라 도착이 그대로다 — 계수는 머무는 시간에만 곱하므로 도착을 못 민다.
        assertThat(entry(projection, "A").predictedArrival()).isEqualTo(at(LocalTime.of(9, 0)));
        // A 에 90분(60 × 1.5) 머물면 10:30 에 나오고, 30분 이동해 11:00 도착.
        assertThat(entry(projection, "B").predictedArrival()).isEqualTo(at(LocalTime.of(11, 0)));
        assertThat(entry(projection, "B").delayMinutes()).isEqualTo(30L);
        // B 에 90분 머물면 12:30, 30분 이동해 13:00 도착. 계획은 12:00 이었다.
        assertThat(entry(projection, "C").predictedArrival()).isEqualTo(at(LocalTime.of(13, 0)));
        assertThat(entry(projection, "C").delayMinutes()).isEqualTo(60L);
        assertThat(projection.factorApplied()).isTrue();
    }

    @Test
    @DisplayName("완료 기준 — 계수가 없으면 예상 도착 시각이 계획 그대로다")
    void withoutAFactorThePlanStands() {
        Fixture fixture = new Fixture();

        ItineraryDelayProjector.Projection projection = fixture.project(null, BEFORE_THE_DAY);

        assertThat(entry(projection, "A").predictedArrival()).isEqualTo(at(LocalTime.of(9, 0)));
        assertThat(entry(projection, "B").predictedArrival()).isEqualTo(at(LocalTime.of(10, 30)));
        assertThat(entry(projection, "C").predictedArrival()).isEqualTo(at(LocalTime.of(12, 0)));
        assertThat(projection.entries()).allSatisfy(e -> assertThat(e.delayMinutes()).isEqualTo(0L));
        assertThat(projection.factorApplied()).isFalse();
    }

    @Test
    @DisplayName("🔴 시간 안에 끝나는 일정은 위험이 아니다 — 첫 구간 이동을 두 번 세지 않는다 (S15P21E201-1571)")
    void theFirstLegIsNotCountedTwice() {
        Fixture fixture = new Fixture();
        // 출발지(부산역)에서 A 까지 79분. A 의 계획 도착 09:00 은 이 이동을 이미 넣고 깐 시각이다.
        fixture.legs.removeIf(l -> l.sequence() == 1);
        fixture.legs.add(Fixture.leg(1, null, "place-A", 79));

        ItineraryDelayProjector.Projection projection = fixture.project(null, BEFORE_THE_DAY);

        assertThat(entry(projection, "A").predictedArrival()).isEqualTo(at(LocalTime.of(9, 0)));
        assertThat(entry(projection, "C").delayMinutes()).isEqualTo(0L);
        assertThat(projection.atRisk()).as("계획대로면 하루 끝을 안 넘긴다").isEmpty();
    }

    @Test
    @DisplayName("완료 기준 — 하루 끝을 넘기는 항목이 따로 구분된다")
    void itemsThatOverrunTheDayAreSeparated() {
        Fixture fixture = new Fixture();

        ItineraryDelayProjector.Projection projection = fixture.project(new BigDecimal("2.00"), BEFORE_THE_DAY);

        // A 120분 → 11:00 출발, 30분 → B 11:30 도착, 120분 → 13:30 출발(하루 끝 13:00 을 넘긴다)
        assertThat(entry(projection, "A").overrunsDay()).isFalse();
        assertThat(entry(projection, "B").overrunsDay()).isTrue();
        assertThat(entry(projection, "C").overrunsDay()).isTrue();
        assertThat(projection.atRisk()).extracting(ItineraryDelayProjector.Entry::itemKey)
                .containsExactly("B", "C");
        assertThat(projection.plannedDayEnd()).isEqualTo(at(LocalTime.of(13, 0)));
    }

    @Test
    @DisplayName("완료 기준 — 지나간 방문지는 기록된 시각 그대로 남고 예측이 덮어쓰지 않는다")
    void visitedItemsKeepTheirRecordedTimes() {
        Fixture fixture = new Fixture();
        Instant arrivedAtA = at(LocalTime.of(9, 20));
        Instant departedFromA = at(LocalTime.of(11, 0));
        fixture.visited("A", arrivedAtA, departedFromA);

        ItineraryDelayProjector.Projection projection =
                fixture.project(new BigDecimal("1.50"), at(LocalTime.of(11, 0)));

        ItineraryDelayProjector.Entry a = entry(projection, "A");
        assertThat(a.visited()).isTrue();
        assertThat(a.predictedArrival()).isEqualTo(arrivedAtA);
        assertThat(a.predictedDeparture()).isEqualTo(departedFromA);
        // 그리고 남은 방문지는 그 출발 시각에서 이어진다 — 11:00 + 30분 = 11:30
        assertThat(entry(projection, "B").visited()).isFalse();
        assertThat(entry(projection, "B").predictedArrival()).isEqualTo(at(LocalTime.of(11, 30)));
    }

    @Test
    @DisplayName("계수는 머무는 시간에만 곱한다 — 이동 시간은 길찾기가 답한 값 그대로다")
    void theFactorNeverTouchesTravelTime() {
        Fixture fixture = new Fixture();
        fixture.visited("A", at(LocalTime.of(9, 0)), at(LocalTime.of(10, 0)));

        ItineraryDelayProjector.Projection doubled = fixture.project(new BigDecimal("2.00"), at(LocalTime.of(10, 0)));
        ItineraryDelayProjector.Projection plain = fixture.project(null, at(LocalTime.of(10, 0)));

        // 계수를 두 배로 줘도 A 출발 뒤 B 까지의 30분은 안 변한다. 두 예측의 B 도착이 같다.
        assertThat(entry(doubled, "B").predictedArrival()).isEqualTo(at(LocalTime.of(10, 30)));
        assertThat(entry(plain, "B").predictedArrival()).isEqualTo(at(LocalTime.of(10, 30)));
        // 달라지는 것은 B 에 머무는 시간이고 그래서 C 도착만 벌어진다. 벌어진 폭(60분)은
        // B 의 계획 체류 60분이 120분이 된 딱 그만큼이고, 이동 30분은 양쪽에 그대로 있다.
        assertThat(entry(doubled, "C").predictedArrival()).isEqualTo(at(LocalTime.of(13, 0)));
        assertThat(entry(plain, "C").predictedArrival()).isEqualTo(at(LocalTime.of(12, 0)));
    }

    @Test
    @DisplayName("마지막 출발이 지금보다 이르면 지금부터 센다 — 그 사이 시간도 이미 지나갔다")
    void theClockMovesOnEvenWhenNothingWasRecorded() {
        Fixture fixture = new Fixture();
        fixture.visited("A", at(LocalTime.of(9, 0)), at(LocalTime.of(10, 0)));

        // 10시에 나왔다고 적혀 있지만 지금은 12시다. 두 시간은 어디에도 안 적혔지만 지났다.
        ItineraryDelayProjector.Projection projection =
                fixture.project(null, at(LocalTime.of(12, 0)));

        assertThat(entry(projection, "B").predictedArrival()).isEqualTo(at(LocalTime.of(12, 30)));
    }

    @Test
    @DisplayName("이동 시간을 모르는 구간은 0 으로 센다 — 늦어진 날에서 그 구간만큼 덜 밀린다")
    void anUnknownLegContributesNothing() {
        Fixture fixture = new Fixture();
        fixture.dropLegInto(2);

        ItineraryDelayProjector.Projection projection = fixture.project(new BigDecimal("1.50"), BEFORE_THE_DAY);

        // A 에 90분(60 × 1.5) 머물면 10:30 에 나온다. 구간을 모르니 이동 0 — B 도착 10:30. 구간(30분)을 알면 11:00 이다
        // (withAFactorPredictedArrivalsDivergeFromThePlan). 전에는 계수 없이 재서 10:00(30분 이르다)으로 드러냈는데, 이제
        // 예상은 계획보다 이르게 안 잡힌다(S15P21E201-1740).
        assertThat(entry(projection, "B").predictedArrival()).isEqualTo(at(LocalTime.of(10, 30)));
        assertThat(entry(projection, "B").delayMinutes()).isZero();
    }

    @Test
    @DisplayName("다른 날 항목은 이 하루의 예측에 안 섞인다")
    void otherDaysAreNotProjected() {
        Fixture fixture = new Fixture();
        fixture.itemOnAnotherDay();

        ItineraryDelayProjector.Projection projection = fixture.project(null, BEFORE_THE_DAY);

        assertThat(projection.entries()).extracting(ItineraryDelayProjector.Entry::itemKey)
                .containsExactly("A", "B", "C");
    }

    // ── 곳 사이에 자유 시간이 있는 날 (S15P21E201-1740) ─────────────────────
    //
    //   09:00-10:00 A · 10:30-11:30 B · (자유 시간) · 14:00-15:00 C   — B→C 이동 30분, B 끝에서 C 까지 2시간 반
    //
    // 시각표는 곳 사이에 빈 시각을 둔다(S15P21E201-1667). 예측이 그걸 건너뛰고 이어 붙이면 C 가 12:00 으로 나와
    // 카드에 「14:00」과 「예상 도착 12:00」이 함께 뜬다 — 시연 점검에서 「16:04」 옆 「예상 도착 11:45」로 보였다.

    @Test
    @DisplayName("🔴 시작 안 한 날은 자유 시간이 있어도 예상 = 계획이다 — 빈 시각을 건너뛰어 앞당기지 않는다")
    void freeTimeIsNotSkippedBeforeTheDayStarts() {
        Fixture fixture = new Fixture();
        fixture.freeTimeBeforeC();

        ItineraryDelayProjector.Projection projection = fixture.project(null, BEFORE_THE_DAY);

        assertThat(entry(projection, "C").predictedArrival()).as("이어 붙이면 12:00 이다").isEqualTo(at(LocalTime.of(14, 0)));
        assertThat(projection.entries()).allSatisfy(e -> assertThat(e.delayMinutes()).isEqualTo(0L));
    }

    @Test
    @DisplayName("🔴 앞선 경우 — B 를 일찍 떠나도 C 의 예상 도착은 계획 14:00 이다. 자유 시간이 앞선 만큼을 흡수한다")
    void beingAheadIsAbsorbedByFreeTime() {
        Fixture fixture = new Fixture();
        fixture.freeTimeBeforeC();
        fixture.visited("A", at(LocalTime.of(9, 0)), at(LocalTime.of(9, 40)));
        fixture.visited("B", at(LocalTime.of(10, 10)), at(LocalTime.of(10, 50)));

        ItineraryDelayProjector.Projection projection = fixture.project(null, at(LocalTime.of(10, 55)));

        // 이어 붙이면 10:55 + 30분 = 11:25 — 계획보다 2시간 35분 이르다고 나왔다.
        assertThat(entry(projection, "C").predictedArrival()).isEqualTo(at(LocalTime.of(14, 0)));
        assertThat(entry(projection, "C").delayMinutes()).isZero();
        assertThat(entry(projection, "C").predictedDeparture()).isEqualTo(at(LocalTime.of(15, 0)));
    }

    @Test
    @DisplayName("🔴 늦은 경우 — 자유 시간보다 더 늦으면 그 넘친 만큼만 밀린다")
    void beingLateBeyondTheFreeTimePushesTheRest() {
        Fixture fixture = new Fixture();
        fixture.freeTimeBeforeC();
        fixture.visited("A", at(LocalTime.of(9, 0)), at(LocalTime.of(10, 0)));
        // B 를 13:45 에 떠났다 — 계획(11:30)보다 2시간 15분 늦다. 자유 시간 2시간 반 중 2시간 15분을 썼다.
        fixture.visited("B", at(LocalTime.of(10, 30)), at(LocalTime.of(13, 45)));

        ItineraryDelayProjector.Projection projection = fixture.project(null, at(LocalTime.of(13, 45)));

        // 13:45 + 이동 30분 = 14:15 — 계획 14:00 보다 15분 늦다.
        assertThat(entry(projection, "C").predictedArrival()).isEqualTo(at(LocalTime.of(14, 15)));
        assertThat(entry(projection, "C").delayMinutes()).isEqualTo(15L);
        // C 의 끝(15:15)이 하루 끝(15:00)을 넘는다 — 그 위험은 그대로 알린다.
        assertThat(entry(projection, "C").overrunsDay()).isTrue();
    }

    @Test
    @DisplayName("늦은 경우 — 자유 시간 안에서 늦은 것은 흡수된다. 계수로 늦어진 B 뒤에도 C 는 계획대로다")
    void lateInsideTheFreeTimeIsAbsorbed() {
        Fixture fixture = new Fixture();
        fixture.freeTimeBeforeC();

        ItineraryDelayProjector.Projection projection = fixture.project(new BigDecimal("1.50"), BEFORE_THE_DAY);

        // A 90분 → 10:30 출발 → B 11:00 도착(30분 늦음) → B 90분 → 12:30 출발 → C 는 13:00 에 닿을 수 있지만 계획은 14:00.
        assertThat(entry(projection, "B").delayMinutes()).isEqualTo(30L);
        assertThat(entry(projection, "C").predictedArrival()).isEqualTo(at(LocalTime.of(14, 0)));
        assertThat(entry(projection, "C").delayMinutes()).isZero();
    }

    private static ItineraryDelayProjector.Entry entry(ItineraryDelayProjector.Projection projection,
            String itemKey) {
        return projection.entries().stream()
                .filter(e -> e.itemKey().equals(itemKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("예측에 " + itemKey + " 가 없다"));
    }

    private static Instant at(LocalTime time) {
        return DAY.atTime(time).atZone(SEOUL).toInstant();
    }

    /** 위 주석의 하루를 세우고, 검사마다 한 군데씩 바꿔 쓴다. */
    private static final class Fixture {

        private final List<ItineraryItem> items = new ArrayList<>();

        private final List<ItineraryLeg> legs = new ArrayList<>();

        private final List<ItineraryItemActual> actuals = new ArrayList<>();

        Fixture() {
            items.add(item("A", 1, LocalTime.of(9, 0), LocalTime.of(10, 0)));
            items.add(item("B", 2, LocalTime.of(10, 30), LocalTime.of(11, 30)));
            items.add(item("C", 3, LocalTime.of(12, 0), LocalTime.of(13, 0)));
            legs.add(leg(1, null, "place-A", null));
            legs.add(leg(2, "place-A", "place-B", 30));
            legs.add(leg(3, "place-B", "place-C", 30));
        }

        ItineraryDelayProjector.Projection project(BigDecimal factor, Instant now) {
            return ItineraryDelayProjector.project(items, legs, actuals, 0, factor, now);
        }

        void visited(String itemKey, Instant arrived, Instant departed) {
            actuals.add(new ItineraryItemActual(ITINERARY_ID, itemKey, arrived, departed, "user-1",
                    departed.plusSeconds(60)));
        }

        /** C 를 14:00-15:00 으로 옮겨 B 와 C 사이에 자유 시간을 둔다 — 시각표가 곳 사이에 빈 시각을 두는 모양. */
        void freeTimeBeforeC() {
            items.removeIf(i -> i.itemKey().equals("C"));
            items.add(item("C", 3, LocalTime.of(14, 0), LocalTime.of(15, 0)));
        }

        /** 그 자리로 들어오는 구간의 이동 시간을 지운다 — 길찾기가 답을 못 준 상황. */
        void dropLegInto(int sequence) {
            legs.removeIf(l -> l.sequence() == sequence);
        }

        void itemOnAnotherDay() {
            items.add(new ItineraryItem("id-Z", "ver-1", "Z", 1, DAY.plusDays(1), 1, "place-Z",
                    LocalTime.of(9, 0), LocalTime.of(10, 0), 60, false, null,
                    ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null,
                    Instant.parse("2026-09-09T00:00:00Z")));
        }

        private static ItineraryItem item(String itemKey, int sequence, LocalTime start, LocalTime end) {
            return new ItineraryItem("id-" + itemKey, "ver-1", itemKey, 0, DAY, sequence,
                    "place-" + itemKey, start, end, 60, false, null,
                    ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null,
                    Instant.parse("2026-09-09T00:00:00Z"));
        }

        private static ItineraryLeg leg(int sequence, String from, String to, Integer durationMin) {
            return new ItineraryLeg("leg-" + sequence, "ver-1", 0, sequence, from, to, "WALK",
                    durationMin == null ? null : 1000, durationMin, null, null, null,
                    ItineraryItem.DataStatus.VERIFIED, Instant.parse("2026-09-09T00:00:00Z"));
        }
    }
}
