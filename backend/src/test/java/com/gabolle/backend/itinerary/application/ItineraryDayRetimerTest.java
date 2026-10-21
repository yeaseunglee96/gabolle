package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 하루 시각 다시 매기기 — 머무는 시간을 바꾸거나 장소를 끼워 넣은 뒤 뒤따르는 곳의 시각을 정한다(S15P21E201-2017).
 *
 * <p>규칙 셋.
 * <ol>
 * <li>곳과 곳 사이 간격(이동 + 빈 시각, {@code leadMinutes})은 그대로 둔다 — 앞 곳이 짧아지면 뒤가 그만큼 당겨진다.
 * <li>「시각 고정」 곳은 제 시각을 지킨다. 앞이 당겨지면 그 앞은 빈 시각이 된다. 앞이 늘어나 제 시각에 못 닿으면 밀리고 그 사실을
 *     {@code missedTimeFixed} 로 알린다.
 * <li>자정을 넘기면 시각을 지어내지 않는다 — {@code overflowing} 으로 알린다.
 * </ol>
 */
class ItineraryDayRetimerTest {

	private static ItineraryDayRetimer.Stop stop(String key, String start, int stay, boolean timeFixed, int lead) {
		return new ItineraryDayRetimer.Stop(key, start == null ? null : LocalTime.parse(start), stay, timeFixed, lead);
	}

	@Test
	@DisplayName("앞 곳을 줄이면 뒤 곳이 그만큼 당겨지고 사이 간격은 그대로다")
	void shorteningPullsFollowingStopsEarlierKeepingGaps() {
		// A 09:00~(90분으로 줄임) · B 는 원래 12:30 시작, 앞 곳 끝과 30분 간격 · C 는 B 끝과 15분 간격
		ItineraryDayRetimer.Result result = ItineraryDayRetimer.retime(LocalTime.of(9, 0), List.of(
				stop("A", "09:00", 90, false, 0),
				stop("B", "12:30", 60, false, 30),
				stop("C", "13:45", 60, false, 15)));

		assertThat(result.starts().get("A")).isEqualTo(LocalTime.of(9, 0));
		assertThat(result.ends().get("A")).isEqualTo(LocalTime.of(10, 30));
		assertThat(result.starts().get("B")).isEqualTo(LocalTime.of(11, 0));
		assertThat(result.ends().get("B")).isEqualTo(LocalTime.of(12, 0));
		assertThat(result.starts().get("C")).isEqualTo(LocalTime.of(12, 15));
		assertThat(result.missedTimeFixed()).isEmpty();
		assertThat(result.overflowing()).isEmpty();
	}

	@Test
	@DisplayName("🔴 시각 고정 곳은 앞이 당겨져도 제 시각에 시작한다 — 그 앞은 빈 시각이 된다")
	void timeFixedStopKeepsItsStartWhenEarlierStopsShrink() {
		ItineraryDayRetimer.Result result = ItineraryDayRetimer.retime(LocalTime.of(9, 0), List.of(
				stop("A", "09:00", 60, false, 0),
				stop("B", "12:47", 60, true, 20),
				stop("C", "14:00", 60, false, 13)));

		assertThat(result.starts().get("B")).isEqualTo(LocalTime.of(12, 47));
		assertThat(result.starts().get("C")).isEqualTo(LocalTime.of(14, 0));
		assertThat(result.missedTimeFixed()).isEmpty();
	}

	@Test
	@DisplayName("시각 고정 곳에 앞이 늘어나 못 닿으면 밀리고 그 사실을 알린다")
	void timeFixedStopThatCannotBeReachedIsPushedAndReported() {
		ItineraryDayRetimer.Result result = ItineraryDayRetimer.retime(LocalTime.of(9, 0), List.of(
				stop("A", "09:00", 240, false, 0),
				stop("B", "12:00", 60, true, 30)));

		assertThat(result.starts().get("B")).isEqualTo(LocalTime.of(13, 30));
		assertThat(result.missedTimeFixed()).containsExactly("B");
	}

	@Test
	@DisplayName("자정을 넘기면 시각을 지어내지 않고 넘친 곳을 알린다")
	void crossingMidnightIsReportedNotWrapped() {
		ItineraryDayRetimer.Result result = ItineraryDayRetimer.retime(LocalTime.of(21, 0), List.of(
				stop("A", "21:00", 120, false, 0),
				stop("B", "23:00", 120, false, 0)));

		assertThat(result.overflowing()).containsExactly("B");
		assertThat(result.starts()).doesNotContainKey("B");
	}

	@Test
	@DisplayName("시각을 모르는 곳(처음 시각 null)은 앞 곳 끝에 간격만큼 붙는다")
	void stopWithoutOriginalTimeFollowsThePrevious() {
		ItineraryDayRetimer.Result result = ItineraryDayRetimer.retime(LocalTime.of(10, 0), List.of(
				stop("A", "10:00", 60, false, 0),
				stop("N", null, 45, false, 12)));

		assertThat(result.starts().get("N")).isEqualTo(LocalTime.of(11, 12));
		assertThat(result.ends().get("N")).isEqualTo(LocalTime.of(11, 57));
	}
}
