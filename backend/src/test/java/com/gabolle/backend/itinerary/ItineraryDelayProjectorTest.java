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
    @DisplayName("이동 시간을 모르는 구간은 0 으로 세지만 그 사실이 값에 드러난다")
    void anUnknownLegContributesNothing() {
        Fixture fixture = new Fixture();
        fixture.dropLegInto(2);

        ItineraryDelayProjector.Projection projection = fixture.project(null, BEFORE_THE_DAY);

        // A 는 10:00 에 끝나고 구간을 모르니 B 도착도 10:00 이다. 계획 10:30 보다 30분 이르다.
        assertThat(entry(projection, "B").predictedArrival()).isEqualTo(at(LocalTime.of(10, 0)));
        assertThat(entry(projection, "B").delayMinutes()).isEqualTo(-30L);
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
