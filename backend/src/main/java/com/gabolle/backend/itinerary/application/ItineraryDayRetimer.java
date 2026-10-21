package com.gabolle.backend.itinerary.application;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 하루 시각을 다시 매긴다 — 머무는 시간을 바꾸거나 장소를 끼워 넣은 뒤(S15P21E201-2017). 순수 계산이다.
 *
 * <p>{@link DayTimeLayout} 과 무엇이 다른가 — 저것은 하루를 «처음부터» 깐다(빈 시각을 곳 사이에 고르게 나누고, 넘치면 체류를
 * 줄인다). 이것은 «이미 깔린» 하루에서 사용자가 한 곳만 손댄 경우다. 처음부터 다시 깔면 사용자가 손대지 않은 곳의 시각과 머무는
 * 시간까지 바뀌어 「한 곳을 30분 줄였는데 나머지가 다 흔들렸다」가 된다. 그래서 곳 사이 간격(이동 + 빈 시각)을 그대로 두고 앞
 * 곳 끝에서부터 차례로 민다.
 *
 * <p>「시각 고정」 곳은 제 시각을 지킨다 — 앞이 당겨지면 그 앞이 빈 시각이 되고, 앞이 늘어나 못 닿으면 밀리되 그 사실을
 * {@link Result#missedTimeFixed()} 로 알린다(지키지 못한 약속을 조용히 넘기지 않는다).
 */
final class ItineraryDayRetimer {

	private static final long MINUTES_PER_DAY = 24L * 60;

	/**
	 * 한 곳.
	 *
	 * @param originalStart 지금 시작 시각. 모르면 {@code null}. 시각 고정 곳은 이 값을 지킨다
	 * @param stayMinutes   머무를 분
	 * @param timeFixed     「시각 고정」인가
	 * @param leadMinutes   앞 곳이 끝나고 이 곳이 시작하기까지 둘 분(이동 + 빈 시각). 첫 곳은 안 쓴다
	 */
	record Stop(String itemKey, LocalTime originalStart, int stayMinutes, boolean timeFixed, int leadMinutes) {
	}

	/**
	 * @param missedTimeFixed 시각 고정인데 제 시각에 못 닿아 밀린 곳
	 * @param overflowing     자정을 넘겨 시각을 못 준 곳 — 시작·끝 지도에 없다
	 */
	record Result(Map<String, LocalTime> starts, Map<String, LocalTime> ends, List<String> missedTimeFixed,
			List<String> overflowing) {
	}

	private ItineraryDayRetimer() {
	}

	/**
	 * @param dayStart 첫 곳의 시작 시각(첫 곳이 시각 고정이면 그 시각이 우선이다)
	 */
	static Result retime(LocalTime dayStart, List<Stop> stops) {
		Map<String, LocalTime> starts = new HashMap<>();
		Map<String, LocalTime> ends = new HashMap<>();
		List<String> missed = new ArrayList<>();
		List<String> overflowing = new ArrayList<>();

		long previousEnd = -1;
		for (int i = 0; i < stops.size(); i++) {
			Stop stop = stops.get(i);
			long arrive = (i == 0) ? minutesOf(dayStart) : previousEnd + Math.max(0, stop.leadMinutes());
			long start = arrive;
			if (stop.timeFixed() && stop.originalStart() != null) {
				long fixed = minutesOf(stop.originalStart());
				if (arrive <= fixed) {
					start = fixed;
				}
				else {
					missed.add(stop.itemKey());
				}
			}
			long end = start + stop.stayMinutes();
			// 자정을 넘기면 LocalTime 이 00:30 처럼 되감겨 「새벽에 간다」는 거짓이 된다 — 시각을 안 준다.
			if (end >= MINUTES_PER_DAY) {
				overflowing.add(stop.itemKey());
				previousEnd = end;
				continue;
			}
			starts.put(stop.itemKey(), LocalTime.of((int) (start / 60), (int) (start % 60)));
			ends.put(stop.itemKey(), LocalTime.of((int) (end / 60), (int) (end % 60)));
			previousEnd = end;
		}
		return new Result(starts, ends, List.copyOf(missed), List.copyOf(overflowing));
	}

	private static long minutesOf(LocalTime time) {
		return time.getHour() * 60L + time.getMinute();
	}
}
