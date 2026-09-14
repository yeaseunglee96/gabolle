package com.gabolle.backend.itinerary.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.PaceFactor;

/**
 * 계획 시간 대비 실제 시간의 배수 — S15P21E201-304.
 *
 * <p>"이 사람은 계획보다 1.3배 느리다" 를 방문 기록에서 구한다. Spring 도 DB 도 모르는
 * 순수 계산이라 단위 테스트가 컨텍스트 없이 이 규칙만 재게 만든다.
 *
 * <h2>무엇을 재는가 — 머문 시간이지 걷는 시간이 아니다</h2>
 * 표본 하나는 <b>방문지 한 곳에 머문 시간</b>이다. 계획이 "여기서 90분" 이라고 했는데
 * 실제로 120분 있었으면 배수는 {@code 120/90 = 1.33} 이다. 도착 시각과 출발 시각이
 * <b>둘 다</b> 기록된 방문지만 표본이 된다 — 하나만 있으면 머문 시간을 알 수 없다.
 *
 * <p>🔴 <b>이동 시간은 이 계수에 안 들어가고, 나중에 이 계수를 곱하지도 않는다.</b>
 * 우리가 관측한 것은 "이 사람이 장소에서 오래 머문다" 이지 "이 사람이 느리게 걷는다" 가
 * 아니다. 두 시각 사이의 간격에는 길을 헤맨 시간과 밥 먹은 시간이 섞여 있고, 그중
 * 어느 쪽인지 지금 자료로는 가를 수 없다. 안 잰 것을 잰 것처럼 쓰면 그때부터 이
 * 숫자는 관측이 아니라 추측이다. 티켓의 「경계」 절도 같은 말을 한다 — 길의 경사나
 * 폭 때문에 이 구간이 얼마나 힘든가는 다른 Epic 소관이다.
 *
 * <h2>왜 평균이 아니라 중앙값인가</h2>
 * 티켓이 걱정하는 것이 정확히 평균의 약점이다 — "첫 장소에서 우연히 오래 머문 것만으로
 * 하루가 흔들린다". 점심을 세 시간 먹은 날 한 번이 평균을 2배로 끌어올리는데, 그것은
 * 그 사람이 느린 것이 아니라 그날 그 자리에서 오래 있었던 것이다. 중앙값은 그 한 점이
 * 아무리 커도 가운데 값을 못 옮긴다.
 *
 * <h2>표본이 모자라면 계수를 안 만든다</h2>
 * {@link #MIN_SAMPLES} 건 미만이면 {@link Optional#empty()} 를 답한다. 🔴 1.0 을
 * 대신 돌려주지 않는다 — 1.0 은 "재 보니 계획대로 다니는 사람이다" 라는 관측이고
 * 빈 값은 "아직 못 재봤다" 는 무지다. 둘을 같은 값으로 답하면 화면이 "정확히 계획대로
 * 갑니다" 라는 틀린 안심을 그린다. 부르는 쪽이 이 빈 값을 받아 사용자에게 "아직
 * 판단할 만큼 기록이 없습니다" 라고 말해야 한다.
 */
public final class PaceFactorCalculator {

    /**
     * 아래 셋은 {@link PaceFactor} 가 소유한 값을 그대로 가리킨다.
     *
     * <p>여기에 숫자를 다시 적지 않는 이유는 두 자리가 갈라지면 계산기가 자른 값이 도메인
     * 검증에서 다시 걸리는 모순이 생기기 때문이다. 그리고 이것들은 중앙값이라는 <b>계산
     * 방법</b>에 딸린 값이 아니라 "무엇이 계수로 성립하는가" 라는 <b>도메인 규칙</b>이라
     * 소유자가 그쪽이 맞다 — 방법을 바꿔도 "표본 셋 미만은 계수가 아니다" 는 그대로다.
     * 부르는 쪽이 편하도록 이름만 여기 둔다.
     */
    public static final int MIN_SAMPLES = PaceFactor.MIN_SAMPLES;

    /** {@link PaceFactor#MAX_FACTOR} — 이 위는 사람이 느린 것이 아니라 기록이 틀린 것이다. */
    public static final BigDecimal MAX_FACTOR = PaceFactor.MAX_FACTOR;

    /** {@link PaceFactor#MIN_FACTOR} — 이 아래는 시간이 거꾸로 간다는 뜻이다. */
    public static final BigDecimal MIN_FACTOR = PaceFactor.MIN_FACTOR;

    private PaceFactorCalculator() {
    }

    /**
     * 방문 기록에서 계수를 구한다.
     *
     * @param items 그 사람의 일정 항목들. 계획 머문 시간을 여기서 읽는다
     * @param actuals 실제 도착·출발 기록. {@link ItineraryItemActual#itemKey()} 로 항목과 짝짓는다
     * @return 표본이 {@link #MIN_SAMPLES} 건 이상이면 계수, 아니면 빈 값
     */
    public static Optional<Result> calculate(List<ItineraryItem> items, List<ItineraryItemActual> actuals) {
        Samples samples = collect(items, actuals);
        if (samples.ratios().size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        return Optional.of(new Result(clamp(median(samples.ratios())), samples.ratios().size(),
                samples.observedUntil()));
    }

    /**
     * 지금까지 모인 표본이 몇 개인가. 계수가 아직 안 나오는 사람에게 "3건 중 2건" 을
     * 보여 주려면 이 숫자가 필요하다.
     *
     * <p>{@link #calculate} 가 표본이 모자랄 때 빈 값만 답하는 것은 <b>계수를 지어내지
     * 않기 위해서</b>이지 개수까지 숨기려는 것이 아니다. 그래서 세는 일만 따로 연다 —
     * 부르는 쪽이 같은 판정을 다시 구현하면 두 벌이 갈라지고, 그때부터 화면이 말하는
     * 숫자와 계수를 만드는 기준이 서로 다른 것을 센다.
     */
    public static int countSamples(List<ItineraryItem> items, List<ItineraryItemActual> actuals) {
        return collect(items, actuals).ratios().size();
    }

    /** 표본을 모으는 유일한 자리. 무엇이 표본인가에 대한 판정이 여기에만 있다. */
    private static Samples collect(List<ItineraryItem> items, List<ItineraryItemActual> actuals) {
        if (items == null || actuals == null) {
            return new Samples(List.of(), null);
        }
        Map<String, ItineraryItemActual> byItemKey = new HashMap<>();
        for (ItineraryItemActual actual : actuals) {
            byItemKey.put(actual.itemKey(), actual);
        }

        List<BigDecimal> ratios = new ArrayList<>();
        Instant observedUntil = null;
        for (ItineraryItem item : items) {
            ItineraryItemActual actual = byItemKey.get(item.itemKey());
            Optional<BigDecimal> ratio = ratioOf(item, actual);
            if (ratio.isEmpty()) {
                continue;
            }
            ratios.add(ratio.get());
            // 표본으로 쓴 기록만 센다. 짝이 안 맞아 버린 기록의 시각까지 여기 넣으면
            // 다음 계산이 "저기까지 봤다" 고 믿고 아직 안 쓴 기록을 건너뛴다.
            if (observedUntil == null || actual.recordedAt().isAfter(observedUntil)) {
                observedUntil = actual.recordedAt();
            }
        }
        return new Samples(ratios, observedUntil);
    }

    private record Samples(List<BigDecimal> ratios, Instant observedUntil) {
    }

    /**
     * 표본 하나의 배수. 다음 중 하나라도 없으면 표본이 아니다.
     *
     * <ul>
     *   <li>기록 자체 — 안 간 방문지다
     *   <li>도착·출발 중 하나 — 머문 시간을 모른다
     *   <li>계획 머문 시간이 0 이하 — 나눌 수 없다. 0 으로 나눈 값을 "무한히 느리다" 로
     *       읽으면 안 되고, 이건 계획이 비어 있다는 뜻이지 사람에 대한 관측이 아니다
     * </ul>
     */
    private static Optional<BigDecimal> ratioOf(ItineraryItem item, ItineraryItemActual actual) {
        if (actual == null || actual.arrivedAt() == null || actual.departedAt() == null) {
            return Optional.empty();
        }
        Integer planned = plannedStayMinutes(item);
        if (planned == null || planned <= 0) {
            return Optional.empty();
        }
        long actualMinutes = Duration.between(actual.arrivedAt(), actual.departedAt()).toMinutes();
        if (actualMinutes <= 0) {
            // 도착과 출발이 같은 분에 찍혔다. 이건 "0분 만에 봤다" 가 아니라 기록을
            // 대충 눌렀다는 뜻에 가깝다. 배수 0 을 표본에 넣으면 중앙값을 끌어내린다.
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(actualMinutes)
                .divide(BigDecimal.valueOf(planned), 4, RoundingMode.HALF_UP));
    }

    /**
     * 계획 머문 시간. {@code stayMinutes} 를 먼저 믿고, 없으면 시각 두 개에서 구한다.
     *
     * <p>둘 다 없으면 {@code null} — 계획이 시각을 안 정한 항목이다(사용자가 직접 더한
     * 장소가 그렇다. {@code ItineraryRevision.withAddedItem} 이 시각을 지어내지 않는다).
     */
    static Integer plannedStayMinutes(ItineraryItem item) {
        if (item.stayMinutes() != null) {
            return item.stayMinutes();
        }
        if (item.startTime() == null || item.endTime() == null) {
            return null;
        }
        return (int) Duration.between(item.startTime(), item.endTime()).toMinutes();
    }

    /** 홀수면 가운데 값, 짝수면 가운데 둘의 평균. */
    private static BigDecimal median(List<BigDecimal> ratios) {
        List<BigDecimal> sorted = new ArrayList<>(ratios);
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    /** 표에 들어갈 모양(소수 두 자리)으로 맞추고 한계 안으로 자른다. */
    private static BigDecimal clamp(BigDecimal raw) {
        BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(MAX_FACTOR) > 0) {
            return MAX_FACTOR;
        }
        if (rounded.compareTo(MIN_FACTOR) < 0) {
            return MIN_FACTOR;
        }
        return rounded;
    }

    /**
     * 계산 결과.
     *
     * @param factor 계획 대비 배수. 1.30 이면 계획의 1.3배 걸린다
     * @param sampleCount 이 값을 만든 표본 수. 🔴 계수와 <b>항상 함께</b> 다닌다 —
     *     3건짜리 1.30 과 30건짜리 1.30 은 같은 확신이 아니다
     * @param observedUntil 표본으로 쓴 기록 중 가장 나중에 적힌 시각. 다음 계산이
     *     어디서부터 이어야 하는지를 이 값이 답한다
     */
    public record Result(BigDecimal factor, int sampleCount, Instant observedUntil) {
    }
}
