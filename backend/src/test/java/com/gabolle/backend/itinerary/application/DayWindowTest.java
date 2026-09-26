package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 오늘 출발하는 여행의 첫날 시간대(S15P21E201-1734) — 경계값. 순수 계산이라 DB 도 스프링도 없다.
 *
 * <p>시각은 부산 시각으로 적고 Instant 로 바꿔 넣는다. 서버 기본 시간대(CI 러너는 UTC)와 상관없이 같은 답이어야 한다.
 */
class DayWindowTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

	private static final Trip TRIP = new Trip("itn_trip_1", "usr_1", TODAY, TODAY.plusDays(1), null, null, null, 2,
			"09:00-18:00", "Asia/Seoul", null, LocalTime.of(9, 0), LocalTime.of(18, 0), Instant.now());

	private static DayWindow firstDayMadeAt(String time) {
		return DayWindow.of(TRIP, 0, LocalDateTime.of(TODAY, LocalTime.parse(time)).atZone(ZoneId.of("Asia/Seoul"))
				.toInstant());
	}

	@Test
	@DisplayName("14:07 → 14:30~18:00, 하루 4곳이면 2곳")
	void afternoonStartsFromTheNextHalfHour() {
		DayWindow window = firstDayMadeAt("14:07");

		assertThat(window.kind()).isEqualTo(DayWindow.Kind.FROM_NOW);
		assertThat(window.start()).isEqualTo(LocalTime.of(14, 30));
		assertThat(window.end()).isEqualTo(LocalTime.of(18, 0));
		assertThat(window.scaledItems(4, DayWindow.of(TRIP))).isEqualTo(2);
	}

	@Test
	@DisplayName("정각 반은 그대로, 1초라도 넘으면 다음 반 시각")
	void roundingIsACeiling() {
		assertThat(firstDayMadeAt("14:30").start()).isEqualTo(LocalTime.of(14, 30));
		assertThat(firstDayMadeAt("14:30:01").start()).isEqualTo(LocalTime.of(15, 0));
	}

	@Test
	@DisplayName("활동 시작 전에 만들면 그대로 — 08:10 에 만든 오늘 여행은 09:00 부터 하루치")
	void beforeTheWindowNothingChanges() {
		DayWindow window = firstDayMadeAt("08:10");

		assertThat(window.kind()).isEqualTo(DayWindow.Kind.USUAL);
		assertThat(window.start()).isEqualTo(LocalTime.of(9, 0));
		assertThat(window.scaledItems(4, DayWindow.of(TRIP))).isEqualTo(4);
	}

	@Test
	@DisplayName("16:31 → 17:00 부터면 남은 60분 — 저녁으로 17:00~22:00")
	void lessThanNinetyMinutesLeftBecomesAnEvening() {
		DayWindow window = firstDayMadeAt("16:31");

		assertThat(window.kind()).isEqualTo(DayWindow.Kind.EVENING);
		assertThat(window.start()).isEqualTo(LocalTime.of(17, 0));
		assertThat(window.end()).isEqualTo(LocalTime.of(22, 0));
		assertThat(window.scaledItems(4, DayWindow.of(TRIP))).isEqualTo(2);
	}

	@Test
	@DisplayName("20:30 → 22:00 까지 딱 90분 — 저녁 1곳. 20:31 → 21:00 부터 60분 — 넣을 시간 없음")
	void theLastEveningIsHalfPastEight() {
		DayWindow last = firstDayMadeAt("20:30");
		assertThat(last.kind()).isEqualTo(DayWindow.Kind.EVENING);
		assertThat(last.scaledItems(4, DayWindow.of(TRIP))).isEqualTo(1);

		DayWindow tooLate = firstDayMadeAt("20:31");
		assertThat(tooLate.kind()).isEqualTo(DayWindow.Kind.NO_TIME_LEFT);
		assertThat(tooLate.scaledItems(4, DayWindow.of(TRIP))).isZero();
		assertThat(tooLate.known()).as("시간이 없는 날은 시각을 안 깐다").isFalse();
	}

	@Test
	@DisplayName("22:10 · 23:45(올리면 자정 넘김) — 넣을 시간 없음")
	void lateAtNightThereIsNoTimeLeft() {
		assertThat(firstDayMadeAt("22:10").kind()).isEqualTo(DayWindow.Kind.NO_TIME_LEFT);
		assertThat(firstDayMadeAt("23:45").kind()).isEqualTo(DayWindow.Kind.NO_TIME_LEFT);
	}

	@Test
	@DisplayName("둘째 날과 오늘이 아닌 여행은 늘 그대로다")
	void onlyTheFirstDayOfATripStartingTodayChanges() {
		Instant afternoon = LocalDateTime.of(TODAY, LocalTime.of(14, 7)).atZone(ZoneId.of("Asia/Seoul")).toInstant();
		assertThat(DayWindow.of(TRIP, 1, afternoon).kind()).isEqualTo(DayWindow.Kind.USUAL);

		Instant yesterday = LocalDateTime.of(TODAY.minusDays(1), LocalTime.of(14, 7)).atZone(ZoneId.of("Asia/Seoul"))
				.toInstant();
		assertThat(DayWindow.of(TRIP, 0, yesterday).kind()).isEqualTo(DayWindow.Kind.USUAL);
	}

	@Test
	@DisplayName("🔴 부산 날짜·시각으로 가른다 — 부산 08:10(UTC 로는 전날 23:10)에 만든 07시 시작 오늘 여행은 08:30 부터")
	void theDateAndTimeAreBusans() {
		Trip early = new Trip("itn_trip_2", "usr_1", TODAY, TODAY, null, null, null, 2, "07:00-18:00", "Asia/Seoul", null,
				LocalTime.of(7, 0), LocalTime.of(18, 0), Instant.now());
		// 부산 2026-09-26 08:10 = UTC 2026-09-25 23:10. UTC 날짜로 가르면 「어제 만든 것」이라 07:00 부터 하루치가 된다.
		DayWindow window = DayWindow.of(early, 0, Instant.parse("2026-09-25T23:10:00Z"));

		assertThat(window.kind()).isEqualTo(DayWindow.Kind.FROM_NOW);
		assertThat(window.start()).isEqualTo(LocalTime.of(8, 30));
	}
}
