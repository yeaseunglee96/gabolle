package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 하루치 시각 깔기 (S15P21E201-1667) — 갈래별 체류, 남으면 곳 사이에 고르게 나누되 끼니는 제 식사 시각대 안에, 넘치면 같은
 * 비율로 줄이되 한 곳 20분, 그래도 안 되면 하루 밖으로 안 넘긴다.
 */
class DayTimeLayoutTest {

	private static final LocalTime[] LUNCH = { LocalTime.of(11, 30), LocalTime.of(14, 0) };

	private static final LocalTime[] DINNER = { LocalTime.of(17, 0), LocalTime.of(20, 0) };

	@Test
	@DisplayName("🔴 남으면 — 곳마다 갈래별로 머물고, 빈 시각은 곳 사이에 고르게, 점심·저녁은 제 시각대 안에")
	void leftoverTimeIsSpreadBetweenPlacesWithMealsInTheirBands() {
		// 바다 90 · 밥 60 · 문화 60 · 카페 45 · 밥 60 · 걷기 60, 이동 25분씩, 돌아가는 길 25분. 09~21시.
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(21, 0),
				List.of(stop(90), lunch(60), stop(60), stop(45), dinner(60), stop(60)),
				List.of(25, 25, 25, 25, 25, 25), 25);

		// 남는 170분을 사이 다섯에 34분씩 — 그렇게 나눠도 점심(11:54)·저녁(17:36)이 제 시각대 안이라 자를 것이 없다.
		assertThat(times(day)).containsExactly(
				"09:25-10:55",   // 바다 — 첫 곳 앞은 빈 시각이 아니다
				"11:54-12:54",   // 점심
				"13:53-14:53",
				"15:52-16:37",
				"17:36-18:36",   // 저녁
				"19:35-20:35");  // 돌아가는 25분을 더하면 21:00
		assertThat(day).extracting(DayTimeLayout.Visit::stayMinutes).containsExactly(90, 60, 60, 45, 60, 60);
	}

	@Test
	@DisplayName("🔴 고르게 나누다 끼니가 시각대 밖으로 밀리면 거기서 멈춘다 — 남는 시간은 끼니 뒤 곳 사이로")
	void mealIsNotPushedPastItsBand() {
		// 10~17시 420분 = 체류 270 + 이동 90 + 여유 60.
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(10, 0), LocalTime.of(17, 0),
				List.of(stop(90), stop(60), lunch(60), stop(60)), List.of(0, 30, 30, 30), null);

		// 13:30 에 닿는 점심은 이미 14:00 까지 다 못 먹는다 — 더 밀지 않는다. 남은 60분은 점심 뒤 사이 하나에.
		assertThat(times(day)).containsExactly("10:00-11:30", "12:00-13:00", "13:30-14:30", "16:00-17:00");
	}

	@Test
	@DisplayName("끼니가 그날 첫 곳이면 첫 곳이 늦게 시작해 식사 시각대에 맞춘다 — 거기 말고는 기다릴 자리가 없다")
	void firstPlaceMealStartsLate() {
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(10, 30), LocalTime.of(20, 0),
				List.of(lunch(60), stop(60)), List.of(10, 20), null);

		assertThat(times(day).get(0)).isEqualTo("11:30-12:30");
	}

	@Test
	@DisplayName("맞출 만큼 안 남으면 있는 만큼만 기다린다 — 뒤 끼니는 못 맞춰도 하루를 넘기지 않는다")
	void notEnoughFreeTimeAlignsAsFarAsItCan() {
		// 09~13시 240분 = 체류 60+45+60+60 + 여유 15. 10:45 에 닿는 점심은 45분을 기다려야 하는데 15분뿐이다 —
		// 그 15분을 다 점심 앞 사이 둘에 7·8분으로 나눠 11:00 에 먹는다(고르게 나눌 몫보다 끼니가 먼저다).
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(13, 0),
				List.of(stop(60), stop(45), lunch(60), stop(60)), List.of(0, 0, 0, 0), null);

		assertThat(times(day)).containsExactly("09:00-10:00", "10:07-10:52", "11:00-12:00", "12:00-13:00");
	}

	@Test
	@DisplayName("끼니가 없는 날은 남는 시간을 곳 사이에 고르게 — 나누어떨어지지 않는 분은 뒤쪽부터")
	void withoutMealBandsLeftoverIsSpreadEvenly() {
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(14, 0), LocalTime.of(17, 0),
				List.of(stop(45), stop(45), stop(45)), List.of(0, 0, 0), null);

		// 남는 45분을 사이 둘에 22·23분.
		assertThat(times(day)).containsExactly("14:00-14:45", "15:07-15:52", "16:15-17:00");
	}

	@Test
	@DisplayName("🔴 넘치면 — 체류를 같은 비율로 줄여 하루 안에 맞춘다. 빈 시각은 없다")
	void overflowShrinksStaysProportionally() {
		// 09~12시 180분에 90+60+45 = 195분.
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(12, 0),
				List.of(stop(90), lunch(60), stop(45)), List.of(0, 0, 0), null);

		assertThat(day).extracting(DayTimeLayout.Visit::stayMinutes).containsExactly(83, 55, 41);
		assertThat(times(day)).containsExactly("09:00-10:23", "10:23-11:18", "11:18-11:59");
	}

	@Test
	@DisplayName("🔴 넘칠 때 한 곳 20분 밑으로는 안 줄인다 — 거기 묶고 나머지로 다시 비율을 낸다")
	void overflowKeepsTwentyMinutesPerPlace() {
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(10, 40),
				List.of(stop(180), stop(30), stop(30)), List.of(0, 0, 0), null);

		assertThat(day).extracting(DayTimeLayout.Visit::stayMinutes).containsExactly(60, 20, 20);
	}

	@Test
	@DisplayName("🔴 20분씩도 안 들어가면 하루 밖으로 넘기지 않는 것이 먼저 — 전처럼 똑같이 나눈다")
	void whenEvenTwentyMinutesDoNotFitTheDayIsSplitEvenly() {
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(10, 0),
				List.of(stop(60), stop(60), stop(60), stop(60)), List.of(0, 0, 0, 0), null);

		assertThat(day).extracting(DayTimeLayout.Visit::stayMinutes).containsExactly(15, 15, 15, 15);
		assertThat(day.get(3).end()).isEqualTo(LocalTime.of(10, 0));
	}

	@Test
	@DisplayName("이동만으로 하루가 차면 시각을 안 준다")
	void travelEatingTheDayGivesNoTimes() {
		assertThat(DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(stop(60), stop(60)),
				List.of(40, 40), null)).isNull();
	}

	@Test
	@DisplayName("모르는 이동 시간은 0분으로 본다 — 지어내지 않는다")
	void unknownTravelCountsAsZero() {
		List<DayTimeLayout.Visit> day = DayTimeLayout.layout(LocalTime.of(9, 0), LocalTime.of(11, 0),
				List.of(stop(60), stop(60)), Arrays.asList(null, null), null);

		assertThat(times(day)).containsExactly("09:00-10:00", "10:00-11:00");
	}

	@Test
	@DisplayName("🔴 아무렇게나 넣어도 — 하루 밖으로 안 넘고, 이동을 비켜 가고, 남으면 체류를 안 줄이고, 넘치면 20분은 지킨다")
	void invariantsHoldForAnyDay() {
		Random random = new Random(1667);
		int[] stays = { 45, 60, 90 };
		for (int round = 0; round < 5_000; round++) {
			LocalTime windowStart = LocalTime.of(7 + random.nextInt(5), random.nextInt(4) * 15);
			LocalTime windowEnd = windowStart.plusMinutes(120 + random.nextInt(10 * 60));
			int n = 1 + random.nextInt(7);
			List<DayTimeLayout.Stop> stops = new ArrayList<>();
			List<Integer> travel = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				LocalTime[] band = switch (random.nextInt(4)) {
					case 0 -> LUNCH;
					case 1 -> DINNER;
					default -> null;
				};
				stops.add(new DayTimeLayout.Stop(stays[random.nextInt(stays.length)], band == null ? null : band[0],
						band == null ? null : band[1]));
				travel.add(random.nextInt(4) == 0 ? null : random.nextInt(60));
			}
			Integer back = random.nextBoolean() ? null : random.nextInt(60);

			List<DayTimeLayout.Visit> day = DayTimeLayout.layout(windowStart, windowEnd, stops, travel, back);

			long window = Duration.between(windowStart, windowEnd).toMinutes();
			long travelTotal = (back == null ? 0 : back) + travel.stream().mapToLong((t) -> t == null ? 0 : t).sum();
			long room = window - travelTotal;
			long wanted = stops.stream().mapToLong(DayTimeLayout.Stop::stayMinutes).sum();
			if (room / n < 1) {
				assertThat(day).as("라운드 %d", round).isNull();
				continue;
			}
			assertThat(day).as("라운드 %d", round).hasSize(n);
			LocalTime freeAt = windowStart;
			for (int i = 0; i < n; i++) {
				DayTimeLayout.Visit visit = day.get(i);
				long move = travel.get(i) == null ? 0 : travel.get(i);
				assertThat(Duration.between(freeAt, visit.start()).toMinutes()).as("라운드 %d · %d번째 앞 이동", round, i)
						.isGreaterThanOrEqualTo(move);
				assertThat(Duration.between(visit.start(), visit.end()).toMinutes()).isEqualTo(visit.stayMinutes());
				freeAt = visit.end();
			}
			assertThat(Duration.between(windowStart, freeAt).toMinutes() + (back == null ? 0 : back))
					.as("라운드 %d · 하루 밖으로 넘지 않는다", round).isLessThanOrEqualTo(window);
			if (room >= wanted) {
				assertThat(day).extracting(DayTimeLayout.Visit::stayMinutes).as("라운드 %d · 남으면 안 줄인다", round)
						.containsExactlyElementsOf(stops.stream().map(DayTimeLayout.Stop::stayMinutes).toList());
				// 끼니는 고르게 나누는 빈 시각 때문에 제 시각대 밖으로 밀리지 않는다. 밀려도 되는 것은 앞 끼니가 기다리느라
				// 이미 늦어진 만큼뿐이다(빈 시각은 앞에서부터 쌓인다).
				long earliest = 0;
				long pushedBefore = 0;
				for (int i = 0; i < n; i++) {
					earliest += travel.get(i) == null ? 0 : travel.get(i);
					DayTimeLayout.Stop stop = stops.get(i);
					if (stop.mealEnd() != null) {
						long pushed = Duration.between(windowStart, day.get(i).start()).toMinutes() - earliest;
						long room2 = Duration.between(windowStart, stop.mealEnd()).toMinutes() - stop.stayMinutes()
								- earliest;
						assertThat(pushed).as("라운드 %d · %d번째 끼니가 시각대 밖으로 밀렸다", round, i)
								.isLessThanOrEqualTo(Math.max(Math.max(room2, pushedBefore), 0));
						pushedBefore = pushed;
					}
					earliest += stop.stayMinutes();
				}
			}
			else if (room >= 20L * n) {
				assertThat(day).allSatisfy((visit) -> assertThat(visit.stayMinutes()).isGreaterThanOrEqualTo(20));
				assertThat(day.stream().mapToLong(DayTimeLayout.Visit::stayMinutes).sum())
						.as("라운드 %d · 버린 분은 곳 수보다 적다", round).isGreaterThan(room - n);
			}
		}
	}

	private static DayTimeLayout.Stop stop(int minutes) {
		return new DayTimeLayout.Stop(minutes, null, null);
	}

	/** 점심 칸에 앉은 밥집 — 11:30~14:00 안에. */
	private static DayTimeLayout.Stop lunch(int minutes) {
		return new DayTimeLayout.Stop(minutes, LUNCH[0], LUNCH[1]);
	}

	/** 저녁 칸에 앉은 밥집 — 17:00~20:00 안에. */
	private static DayTimeLayout.Stop dinner(int minutes) {
		return new DayTimeLayout.Stop(minutes, DINNER[0], DINNER[1]);
	}

	private static List<String> times(List<DayTimeLayout.Visit> day) {
		return day.stream().map((visit) -> visit.start() + "-" + visit.end()).toList();
	}
}
