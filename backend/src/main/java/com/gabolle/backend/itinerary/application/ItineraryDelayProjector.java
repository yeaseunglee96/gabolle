package com.gabolle.backend.itinerary.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;

/**
 * 남은 방문지의 예상 도착 시각과 지연 위험 — S15P21E201-304 · -96.
 *
 * <p>{@link PaceFactorCalculator} 가 "얼마나 느린가" 를 답하면 이 클래스가 "그래서 오늘
 * 몇 시에 도착하나" 를 답한다. 순수 계산이라 Spring 도 DB 도 모른다.
 *
 * <h2>지나간 방문지는 예측하지 않는다</h2>
 * 도착·출발이 기록된 방문지는 <b>이미 일어난 사실</b>이다. 그 위에 예측을 덮어쓰면
 * 사용자가 방금 적은 시각이 화면에서 다른 값으로 바뀐다. 그래서 기록이 있는 항목은
 * 그대로 두고, 예측은 <b>마지막으로 출발한 자리</b>에서 시작한다.
 *
 * <h2>🔴 어디서부터 세는가</h2>
 * 시작점은 {@code max(마지막 실제 출발, now)} 다. 둘 중 큰 값을 쓰는 이유가 각각 있다.
 * <ul>
 *   <li>마지막 실제 출발 — 이미 30분 밀린 채 출발했으면 그 30분은 남은 일정 전부에
 *       그대로 얹힌다. 계수보다 이 값이 보통 더 크게 작용한다
 *   <li>{@code now} — 두 시에 출발했는데 지금 네 시면, 그 사이 두 시간은 어디에도
 *       기록되지 않았지만 <b>이미 지나갔다.</b> 출발 시각만 보고 세면 "지금 두 시라고
 *       치고" 남은 일정을 그리게 된다
 * </ul>
 * 기록이 하나도 없으면(아직 시작 안 한 날) 첫 항목의 계획 시각에서 시작한다. 그러면
 * 미래의 날에도 "그때 가면 이만큼 걸릴 것" 을 그릴 수 있다.
 *
 * <h2>🔴 계수는 머무는 시간에만 곱한다. 이동 시간에는 안 곱한다</h2>
 * 계수를 잰 자리가 방문지에 머문 시간이기 때문이다({@link PaceFactorCalculator} 참고).
 * 이동 시간은 길찾기가 답한 값이고 그 사람이 실제로 얼마나 걸렸는지는 우리가 안 쟀다.
 * 안 잰 것에 곱하면 그 숫자는 관측이 아니라 추측이 되고, 그때부터 "예상 도착 시각" 이
 * 무엇을 근거로 나온 값인지 아무도 설명할 수 없다.
 */
public final class ItineraryDelayProjector {

    /** 계획과 실제를 같은 시계로 읽는다. 저장소 전체가 이 시간대를 쓴다. */
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 지연 판정을 못 한 이유 — 계수를 만들 만큼 기록이 없다. */
    public static final String REASON_NOT_ENOUGH_RECORDS = "NOT_ENOUGH_RECORDS";

    /** 판정 이름. {@code ItineraryOpeningHoursChecker.CHECK} 와 같은 어휘를 쓴다. */
    public static final String CHECK = "PACE_FACTOR";

    private ItineraryDelayProjector() {
    }

    /**
     * 그 하루의 예상 시각표를 만든다.
     *
     * @param items 일정 <b>전체</b> 항목. 이 메서드가 {@code dayIndex} 로 그날만 고른다
     * @param legs 일정 전체 구간. 마찬가지로 그날 것만 쓴다
     * @param actuals 실제 도착·출발 기록
     * @param dayIndex 몇째 날인가
     * @param factor 속도 계수. {@code null} 이면 계수를 안 쓰고 계획대로 민다
     * @param now 지금. 시작점 판정에 쓴다
     */
    public static Projection project(List<ItineraryItem> items, List<ItineraryLeg> legs,
            List<ItineraryItemActual> actuals, int dayIndex, BigDecimal factor, Instant now) {

        List<ItineraryItem> day = items.stream()
                .filter(item -> item.dayIndex() == dayIndex)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence))
                .toList();
        if (day.isEmpty()) {
            return new Projection(List.of(), null, factor != null);
        }

        Map<String, ItineraryItemActual> actualByKey = new HashMap<>();
        for (ItineraryItemActual actual : actuals) {
            actualByKey.put(actual.itemKey(), actual);
        }
        Map<Integer, ItineraryLeg> legBySequence = new HashMap<>();
        for (ItineraryLeg leg : legs) {
            if (leg.dayIndex() == dayIndex) {
                legBySequence.put(leg.sequence(), leg);
            }
        }

        Instant dayEnd = plannedEndOfDay(day);
        Instant cursor = startingPoint(day, actualByKey, now);

        List<Entry> entries = new ArrayList<>();
        for (ItineraryItem item : day) {
            ItineraryItemActual actual = actualByKey.get(item.itemKey());
            if (isVisited(actual)) {
                entries.add(Entry.visited(item.itemKey(), actual.arrivedAt(), actual.departedAt()));
                continue;
            }

            Integer travelMin = travelMinutesInto(legBySequence.get(item.sequence()), item);
            Instant predictedArrival = cursor.plus(Duration.ofMinutes(travelMin == null ? 0 : travelMin));
            Instant predictedDeparture = predictedArrival.plus(stayOf(item, factor));
            cursor = predictedDeparture;

            Instant plannedArrival = plannedInstant(item, item.startTime());
            Long delayMinutes = plannedArrival == null
                    ? null
                    : Duration.between(plannedArrival, predictedArrival).toMinutes();
            boolean overrunsDay = dayEnd != null && predictedDeparture.isAfter(dayEnd);

            entries.add(new Entry(item.itemKey(), false, predictedArrival, predictedDeparture,
                    plannedArrival, delayMinutes, overrunsDay));
        }
        return new Projection(entries, dayEnd, factor != null);
    }

    /**
     * 예측을 시작할 자리.
     *
     * <p>기록이 있으면 마지막 출발과 {@code now} 중 늦은 쪽, 없으면 첫 항목의 계획 시각.
     * 계획 시각조차 없으면(사용자가 손으로 더한 항목만 있는 날) {@code now} 로 민다 —
     * 시각을 지어내지 않는 대신 "지금부터" 라고 답한다.
     */
    private static Instant startingPoint(List<ItineraryItem> day,
            Map<String, ItineraryItemActual> actualByKey, Instant now) {

        Instant lastDeparture = null;
        for (ItineraryItem item : day) {
            ItineraryItemActual actual = actualByKey.get(item.itemKey());
            if (isVisited(actual)
                    && (lastDeparture == null || actual.departedAt().isAfter(lastDeparture))) {
                lastDeparture = actual.departedAt();
            }
        }
        if (lastDeparture != null) {
            return lastDeparture.isAfter(now) ? lastDeparture : now;
        }
        Instant plannedStart = plannedInstant(day.get(0), day.get(0).startTime());
        return plannedStart == null ? now : plannedStart;
    }

    /** 그날 마지막 항목의 계획 끝 시각. 이 시각을 넘기는 항목이 "하루를 넘길 위험" 이다. */
    private static Instant plannedEndOfDay(List<ItineraryItem> day) {
        for (int i = day.size() - 1; i >= 0; i--) {
            Instant end = plannedInstant(day.get(i), day.get(i).endTime());
            if (end != null) {
                return end;
            }
        }
        return null;
    }

    /**
     * 그 항목으로 <b>들어오는</b> 구간의 이동 시간.
     *
     * <p>구간의 {@code sequence} 는 도착하는 항목의 것과 같게 매겨져 있다. 도착지까지
     * 함께 보는 이유는 순서를 바꾼 직후처럼 번호는 같아도 향하는 곳이 다를 수 있어서다.
     */
    private static Integer travelMinutesInto(ItineraryLeg leg, ItineraryItem item) {
        if (leg == null || leg.durationMin() == null) {
            return null;
        }
        if (leg.toPlaceId() != null && !leg.toPlaceId().equals(item.placeId())) {
            return null;
        }
        return leg.durationMin();
    }

    /** 계획 머문 시간에 계수를 곱한다. 계수가 없거나 계획 시간이 없으면 그대로 둔다. */
    private static Duration stayOf(ItineraryItem item, BigDecimal factor) {
        Integer planned = PaceFactorCalculator.plannedStayMinutes(item);
        if (planned == null || planned <= 0) {
            return Duration.ZERO;
        }
        if (factor == null) {
            return Duration.ofMinutes(planned);
        }
        return Duration.ofMinutes(BigDecimal.valueOf(planned).multiply(factor)
                .setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static Instant plannedInstant(ItineraryItem item, LocalTime time) {
        if (item.visitDate() == null || time == null) {
            return null;
        }
        return item.visitDate().atTime(time).atZone(ZONE).toInstant();
    }

    /** 도착·출발이 <b>둘 다</b> 있어야 지나간 것으로 본다. 도착만 찍혔으면 아직 그 자리에 있다. */
    private static boolean isVisited(ItineraryItemActual actual) {
        return actual != null && actual.arrivedAt() != null && actual.departedAt() != null;
    }

    /**
     * 그 하루의 예상 시각표.
     *
     * @param entries 항목 순서대로
     * @param plannedDayEnd 그날 계획상 끝나는 시각. 항목에 시각이 하나도 없으면 {@code null}
     * @param factorApplied 계수를 실제로 곱했나. 거짓이면 예상 시각이 계획과 같은 폭으로만 밀린다
     */
    public record Projection(List<Entry> entries, Instant plannedDayEnd, boolean factorApplied) {

        /** 하루 끝을 넘길 위험이 있는 항목만. 응답이 이 목록을 따로 싣는다. */
        public List<Entry> atRisk() {
            return entries.stream().filter(Entry::overrunsDay).toList();
        }
    }

    /**
     * 방문지 하나의 예측.
     *
     * @param visited 이미 다녀온 곳인가. 참이면 아래 예측 값들은 기록된 사실 그대로다
     * @param predictedArrival 예상 도착. 다녀온 곳이면 실제 도착
     * @param predictedDeparture 예상 출발. 다녀온 곳이면 실제 출발
     * @param plannedArrival 원래 계획된 도착. 화면이 둘을 나란히 보여 준다
     * @param delayMinutes 계획보다 몇 분 늦나. 음수면 이르다. 계획 시각이 없으면 {@code null}
     * @param overrunsDay 이 항목이 하루 끝을 넘기나
     */
    public record Entry(String itemKey, boolean visited, Instant predictedArrival,
            Instant predictedDeparture, Instant plannedArrival, Long delayMinutes,
            boolean overrunsDay) {

        static Entry visited(String itemKey, Instant arrivedAt, Instant departedAt) {
            return new Entry(itemKey, true, arrivedAt, departedAt, null, null, false);
        }
    }
}
