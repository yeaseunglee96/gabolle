package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 식사 칸 고르기 — 점심을 저녁이 덮어써 점심이 사라지던 것(S15P21E201-1905, 팀원 보고 「강제 다이어트」). */
class MealSlotBandsTest {

	private static final LocalTime LUNCH = LocalTime.of(11, 30);
	private static final LocalTime DINNER = LocalTime.of(17, 0);

	private static long mealsStarting(LocalTime[][] bands, LocalTime start) {
		return Arrays.stream(bands).filter(Objects::nonNull).filter(b -> b[0].equals(start)).count();
	}

	@Test
	@DisplayName("🔴 한 칸이 점심·저녁 모두에 가장 잘 맞아도 점심을 지우지 않는다 — 먼저 맡은 식사는 그대로")
	void lunchIsNotOverwrittenByDinner() {
		LocalTime[][] bands = ItineraryDraftService.mealSlotBands(new DayWindow(LocalTime.of(11, 0), LocalTime.of(21, 0), DayWindow.Kind.USUAL), 1);
		assertThat(bands[0]).isNotNull();
		assertThat(bands[0][0]).isEqualTo(LUNCH);
	}

	@Test
	@DisplayName("보통 하루(09:00~21:00 · 4곳)는 점심 칸과 저녁 칸이 서로 다른 칸에 하나씩 있다")
	void usualDayHasLunchAndDinner() {
		LocalTime[][] bands = ItineraryDraftService.mealSlotBands(new DayWindow(LocalTime.of(9, 0), LocalTime.of(21, 0), DayWindow.Kind.USUAL), 4);
		assertThat(mealsStarting(bands, LUNCH)).isEqualTo(1);
		assertThat(mealsStarting(bands, DINNER)).isEqualTo(1);
	}
}
