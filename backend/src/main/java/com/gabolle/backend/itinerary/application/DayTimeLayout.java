package com.gabolle.backend.itinerary.application;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 하루치 방문 시각을 깐다 — 갈래별 체류 + 이동 + 빈 시각 (S15P21E201-1667). 순수 계산이다.
 *
 * <p>🔴 왜. 전에는 활동 시간에서 이동을 뺀 나머지를 곳 수로 똑같이 나눴다. 09~21시 · 하루 5곳 · 이동 1시간이면 녹나무
 * 한 그루에도 2시간 12분이 붙었다. 이제는 곳마다 {@link StayDefaults} 만큼 머물고, 남는 시간은 곳 사이의 빈 시각이 된다.
 * 새 항목 종류는 만들지 않는다 — 빈 시각은 앞 곳의 끝과 다음 곳의 시작 사이로만 드러난다(이미 나간 앱이 모르는 항목을
 * 받으면 깨질 수 있다).
 *
 * <p>규칙 셋 (2026-09-25 사용자 결정):
 * <ol>
 * <li>남으면 — 끼니가 식사 시각대에 오도록 먼저 맞추고(시각대 시작보다 일찍 닿으면 기다린다), 나머지는 곳 사이에
 *     고르게. 고르게 나누다 끼니가 시각대 밖으로 밀리면 거기서 멈춘다 — 끼니는 제 시각대 안에서 시작해 그 안에 끝난다.
 *     끼니는 밥 칸에 앉은 밥집이고, 시각대는 그 밥 칸이 맡은 것이다 — 부르는 쪽이 {@link Stop} 로 준다. 마지막 끼니
 *     뒤에 곳이 없어 못 나눈 것만 하루 끝에 남는다.
 * <li>넘치면 — 체류를 같은 비율로 줄여 하루 안에 맞춘다. 한 곳 최소 {@link StayDefaults#MIN_STAY_MINUTES}분.
 * <li>그래도 안 들어가면 — 하루 밖으로 넘기지 않는 것이 먼저다. 전처럼 남는 시간을 곳 수로 똑같이 나누고, 그마저
 *     1분이 안 되면 시각을 안 준다.
 * </ol>
 */
final class DayTimeLayout {

    /**
     * 한 곳.
     *
     * @param mealStart 끼니면 그 식사 시각대의 시작, 아니면 {@code null}
     * @param mealEnd   끼니면 그 식사 시각대의 끝, 아니면 {@code null}
     */
    record Stop(int stayMinutes, LocalTime mealStart, LocalTime mealEnd) {
    }

    /** 깐 결과 한 곳. */
    record Visit(LocalTime start, LocalTime end, int stayMinutes) {
    }

    private DayTimeLayout() {
    }

    /**
     * @param travelMinutes i 번째 곳으로 오는 이동 분(첫 곳은 출발지에서). 모르면 {@code null} — 0 으로 본다
     * @param returnMinutes 마지막 곳에서 돌아가는 분. 모르면 {@code null}
     * @return 곳마다 시각. 하루에 못 들어가면 {@code null}
     */
    static List<Visit> layout(LocalTime windowStart, LocalTime windowEnd, List<Stop> stops,
            List<Integer> travelMinutes, Integer returnMinutes) {
        int n = stops.size();
        long[] travel = new long[n];
        long travelTotal = (returnMinutes == null) ? 0 : returnMinutes;
        for (int i = 0; i < n; i++) {
            Integer move = (i < travelMinutes.size()) ? travelMinutes.get(i) : null;
            travel[i] = (move == null) ? 0 : move;
            travelTotal += travel[i];
        }
        // 머무름과 빈 시각에 쓸 수 있는 분.
        long room = Duration.between(windowStart, windowEnd).toMinutes() - travelTotal;
        long wanted = 0;
        for (Stop stop : stops) {
            wanted += stop.stayMinutes();
        }

        long[] stay = new long[n];
        long[] gap = new long[n];
        if (room >= wanted) {
            for (int i = 0; i < n; i++) {
                stay[i] = stops.get(i).stayMinutes();
            }
            spreadFreeTime(windowStart, stops, travel, stay, gap, room - wanted);
        }
        else if (room >= (long) StayDefaults.MIN_STAY_MINUTES * n) {
            shrink(stops, stay, room);
        }
        else {
            long each = (n == 0) ? 0 : room / n;
            if (each < 1) {
                return null;
            }
            Arrays.fill(stay, each);
        }

        List<Visit> visits = new ArrayList<>(n);
        LocalTime cursor = windowStart;
        for (int i = 0; i < n; i++) {
            cursor = cursor.plusMinutes(travel[i] + gap[i]);
            LocalTime end = cursor.plusMinutes(stay[i]);
            visits.add(new Visit(cursor, end, (int) stay[i]));
            cursor = end;
        }
        return visits;
    }

    /**
     * 남는 {@code free} 분을 곳 앞 빈 시각({@code gap})에 나눈다 — 곳 사이에 고르게, 단 끼니는 제 식사 시각대 안에서 시작하고
     * 끝나게.
     *
     * <p>끼니마다 그 앞에 쌓이는 빈 시각의 합(= 빈 시각 없이 깔았을 때보다 늦어지는 분)에 아래·위가 있다. 시각대 시작보다
     * 일찍 닿으면 그만큼은 넣어야 하고(아래), 시각대 끝까지 다 먹게 그 이상은 못 넣는다(위). 곳 사이에 고르게 나눈 몫을
     * 이 사이로 자르고, 끼니와 끼니 사이 곳들에 고르게 편다. 마지막 끼니 뒤에 곳이 없으면 남는 것은 하루 끝에 남는다.
     * 아래가 우선이다 — 빈 시각이 모자라면 앞 끼니부터 있는 만큼 맞춘다.
     */
    private static void spreadFreeTime(LocalTime windowStart, List<Stop> stops, long[] travel, long[] stay,
            long[] gap, long free) {
        int n = stops.size();
        List<Integer> meals = new ArrayList<>();
        List<long[]> bounds = new ArrayList<>();
        long at = 0;              // 빈 시각 없이 깔았을 때, 활동 시작에서 몇 분 뒤에 앞 곳이 끝나나
        for (int i = 0; i < n; i++) {
            long arrive = at + travel[i];
            Stop stop = stops.get(i);
            if (stop.mealStart() != null && stop.mealEnd() != null) {
                long low = clamp(minutesFrom(windowStart, stop.mealStart()) - arrive, 0, free);
                long high = clamp(minutesFrom(windowStart, stop.mealEnd()) - stay[i] - arrive, low, free);
                meals.add(i);
                bounds.add(new long[] { low, high });
            }
            at = arrive + stay[i];
        }
        // 빈 시각은 앞에서부터 쌓이기만 한다 — 뒤 끼니의 아래는 앞 끼니의 아래보다 작을 수 없고, 앞 끼니의 위는 뒤 끼니의
        // 위보다 클 수 없다.
        for (int j = 1; j < bounds.size(); j++) {
            bounds.get(j)[0] = Math.max(bounds.get(j)[0], bounds.get(j - 1)[0]);
        }
        for (int j = bounds.size() - 2; j >= 0; j--) {
            bounds.get(j)[1] = Math.min(bounds.get(j)[1], bounds.get(j + 1)[1]);
        }

        int between = n - 1;      // 곳 「사이」 — 그날 첫 곳 앞은 빼고 센다
        long placed = 0;
        int from = 0;
        for (int j = 0; j < meals.size(); j++) {
            int m = meals.get(j);
            long[] bound = bounds.get(j);
            long even = (between == 0) ? 0 : free * m / between;
            long shift = Math.max(placed, clamp(even, bound[0], Math.max(bound[0], bound[1])));
            spread(gap, from, m, shift - placed, true);
            placed = shift;
            from = m + 1;
        }
        spread(gap, from, n - 1, free - placed, false);
    }

    private static long clamp(long value, long low, long high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * {@code from}~{@code to} 곳들 앞에 {@code minutes} 를 고르게 — 나누어떨어지지 않는 분은 뒤쪽부터 1분씩. 그날 첫 곳 앞은
     * 곳 「사이」가 아니라 빼는데, 끼니가 그날 첫 곳이면({@code beforeMeal}) 거기밖에 없어 첫 곳이 늦게 시작한다.
     * 받을 자리가 없으면 하루 끝에 남는다.
     */
    private static void spread(long[] gap, int from, int to, long minutes, boolean beforeMeal) {
        if (minutes <= 0) {
            return;
        }
        int first = Math.max(from, 1);
        if (first > to) {
            if (beforeMeal && to == 0) {
                gap[0] += minutes;
            }
            return;
        }
        int slots = to - first + 1;
        long each = minutes / slots;
        long rest = minutes % slots;
        for (int j = first; j <= to; j++) {
            gap[j] += each + ((j > to - rest) ? 1 : 0);
        }
    }

    /**
     * 체류를 같은 비율로 줄여 {@code room} 안에 맞춘다. 비율대로 줄이면 최소 밑으로 가는 곳은 최소에 묶고, 나머지로 다시
     * 비율을 낸다. 나눗셈에서 버린 몇 분은 하루 끝에 남는다. 부르는 쪽이 {@code room >= 최소 × 곳 수} 를 지킨다.
     */
    private static void shrink(List<Stop> stops, long[] stay, long room) {
        int n = stops.size();
        long min = StayDefaults.MIN_STAY_MINUTES;
        boolean[] atMin = new boolean[n];
        while (true) {
            long pinned = 0;
            long wanted = 0;
            for (int i = 0; i < n; i++) {
                if (atMin[i]) {
                    pinned += min;
                }
                else {
                    wanted += stops.get(i).stayMinutes();
                }
            }
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                if (!atMin[i] && stops.get(i).stayMinutes() * (room - pinned) < min * wanted) {
                    atMin[i] = true;
                    moved = true;
                }
            }
            if (!moved) {
                for (int i = 0; i < n; i++) {
                    stay[i] = atMin[i] ? min : stops.get(i).stayMinutes() * (room - pinned) / wanted;
                }
                return;
            }
        }
    }

    private static long minutesFrom(LocalTime windowStart, LocalTime time) {
        return Duration.between(windowStart, time).toMinutes();
    }
}
