package com.gabolle.backend.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KmaForecastAggregatorTest {

	private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

	@Test
	@DisplayName("TMN·TMX 가 있으면 그 값을 그대로 쓴다")
	void usesTmnTmxWhenPresent() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("TMN", DAY, LocalTime.of(6, 0), "18.0"),
				new KmaForecastItem("TMX", DAY, LocalTime.of(15, 0), "27.0"),
				new KmaForecastItem("POP", DAY, LocalTime.of(9, 0), "30"),
				new KmaForecastItem("POP", DAY, LocalTime.of(12, 0), "60"),
				new KmaForecastItem("SKY", DAY, LocalTime.of(12, 0), "3"));

		DailyForecast forecast = KmaForecastAggregator.aggregate(items, DAY).orElseThrow();

		assertThat(forecast.minTemperature()).isEqualTo(18.0);
		assertThat(forecast.maxTemperature()).isEqualTo(27.0);
		// 강수확률은 그 날의 최댓값을 쓴다
		assertThat(forecast.precipitationProbability()).isEqualTo(60);
		assertThat(forecast.skyCondition()).isEqualTo(SkyCondition.PARTLY_CLOUDY);
	}

	@Test
	@DisplayName("TMN·TMX 가 없으면 TMP 값들의 최솟값·최댓값으로 대신한다")
	void fallsBackToTmpMinMaxWhenTmnTmxMissing() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("TMP", DAY, LocalTime.of(6, 0), "17.0"),
				new KmaForecastItem("TMP", DAY, LocalTime.of(9, 0), "20.0"),
				new KmaForecastItem("TMP", DAY, LocalTime.of(15, 0), "26.0"));

		DailyForecast forecast = KmaForecastAggregator.aggregate(items, DAY).orElseThrow();

		assertThat(forecast.minTemperature()).isEqualTo(17.0);
		assertThat(forecast.maxTemperature()).isEqualTo(26.0);
	}

	@Test
	@DisplayName("하늘상태는 정오에 가장 가까운 시간대 값을 대표로 쓴다")
	void skyConditionPicksSlotClosestToNoon() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("SKY", DAY, LocalTime.of(6, 0), "1"),
				new KmaForecastItem("SKY", DAY, LocalTime.of(13, 0), "4"),
				new KmaForecastItem("SKY", DAY, LocalTime.of(21, 0), "3"));

		DailyForecast forecast = KmaForecastAggregator.aggregate(items, DAY).orElseThrow();

		assertThat(forecast.skyCondition()).isEqualTo(SkyCondition.CLOUDY);
	}

	@Test
	@DisplayName("🔴 요청한 날짜의 항목이 하나도 없으면 빈 값이다 — 지어낸 값을 만들지 않는다")
	void emptyWhenNoItemsMatchDate() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("TMP", DAY.plusDays(5), LocalTime.of(6, 0), "17.0"));

		Optional<DailyForecast> forecast = KmaForecastAggregator.aggregate(items, DAY);

		assertThat(forecast).isEmpty();
	}

	// 시간별 예보(hourly) — S15P21E201-1534

	/** 한 시각의 네 항목을 한 벌 만든다. */
	private static List<KmaForecastItem> slot(LocalDate date, int hour, String tmp, String sky, String pop,
			String pty) {
		LocalTime time = LocalTime.of(hour, 0);
		return List.of(
				new KmaForecastItem("TMP", date, time, tmp),
				new KmaForecastItem("SKY", date, time, sky),
				new KmaForecastItem("POP", date, time, pop),
				new KmaForecastItem("PTY", date, time, pty));
	}

	private static List<KmaForecastItem> concat(List<List<KmaForecastItem>> slots) {
		return slots.stream().flatMap(List::stream).toList();
	}

	@Test
	@DisplayName("1시간 간격 원문은 1시간 간격 그대로 나온다 — 네 항목이 한 칸에 모인다")
	void hourlyKeepsOneHourSlots() {
		List<KmaForecastItem> items = concat(List.of(
				slot(DAY, 15, "24", "1", "0", "0"),
				slot(DAY, 16, "23.5", "3", "20", "0"),
				slot(DAY, 17, "22", "4", "60", "1")));

		List<HourlyForecast> hourly = KmaForecastAggregator.hourly(items, DAY);

		assertThat(hourly).containsExactly(
				new HourlyForecast(LocalTime.of(15, 0), 24.0, SkyCondition.CLEAR, 0, PrecipitationType.NONE),
				new HourlyForecast(LocalTime.of(16, 0), 23.5, SkyCondition.PARTLY_CLOUDY, 20, PrecipitationType.NONE),
				new HourlyForecast(LocalTime.of(17, 0), 22.0, SkyCondition.CLOUDY, 60, PrecipitationType.RAIN));
	}

	@Test
	@DisplayName("🔴 3시간 간격 원문은 3시간 간격 그대로다 — 사이 시각을 채우거나 지어내지 않는다")
	void hourlyDoesNotFillThreeHourGaps() {
		List<KmaForecastItem> items = concat(List.of(
				slot(DAY, 0, "18", "1", "0", "0"),
				slot(DAY, 3, "17", "1", "0", "0"),
				slot(DAY, 6, "17", "3", "10", "0"),
				slot(DAY, 9, "20", "3", "10", "0"),
				slot(DAY, 12, "25", "4", "30", "0"),
				slot(DAY, 15, "26", "4", "30", "0"),
				slot(DAY, 18, "23", "3", "20", "0"),
				slot(DAY, 21, "21", "1", "0", "0")));

		List<HourlyForecast> hourly = KmaForecastAggregator.hourly(items, DAY);

		assertThat(hourly).extracting(HourlyForecast::time).containsExactly(
				LocalTime.of(0, 0), LocalTime.of(3, 0), LocalTime.of(6, 0), LocalTime.of(9, 0),
				LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 0), LocalTime.of(21, 0));
	}

	@Test
	@DisplayName("2026-09-23 운영 캐시의 모양 — 오늘 15~23시 · 나흘째 3시간 간격 8개 · 닷새째 00시 하나를 그대로 낸다")
	void hourlyFollowsTheMeasuredShapeOfARealRound() {
		LocalDate today = LocalDate.of(2026, 9, 23);
		List<List<KmaForecastItem>> slots = new ArrayList<>();
		for (int hour = 15; hour <= 23; hour++) {
			slots.add(slot(today, hour, "24", "1", "0", "0"));
		}
		for (int day = 1; day <= 2; day++) {
			for (int hour = 0; hour < 24; hour++) {
				slots.add(slot(today.plusDays(day), hour, "22", "3", "20", "0"));
			}
		}
		for (int hour = 0; hour < 24; hour += 3) {
			slots.add(slot(today.plusDays(3), hour, "21", "4", "40", "0"));
		}
		slots.add(slot(today.plusDays(4), 0, "19", "1", "0", "0"));
		List<KmaForecastItem> items = concat(slots);

		assertThat(KmaForecastAggregator.hourly(items, today)).hasSize(9);
		assertThat(KmaForecastAggregator.hourly(items, today).get(0).time()).isEqualTo(LocalTime.of(15, 0));
		assertThat(KmaForecastAggregator.hourly(items, today.plusDays(1))).hasSize(24);
		assertThat(KmaForecastAggregator.hourly(items, today.plusDays(2))).hasSize(24);
		assertThat(KmaForecastAggregator.hourly(items, today.plusDays(3))).hasSize(8);
		assertThat(KmaForecastAggregator.hourly(items, today.plusDays(4)))
				.extracting(HourlyForecast::time).containsExactly(LocalTime.of(0, 0));
	}

	@Test
	@DisplayName("🔴 그 시각에 없는 항목은 null 이다 — 0 으로 채우지 않는다(0 은 「없다」, null 은 「모른다」)")
	void missingCategoryIsNullNotZero() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("TMP", DAY, LocalTime.of(12, 0), "25"),
				new KmaForecastItem("POP", DAY, LocalTime.of(13, 0), "0"),
				new KmaForecastItem("PTY", DAY, LocalTime.of(13, 0), "0"));

		List<HourlyForecast> hourly = KmaForecastAggregator.hourly(items, DAY);

		// 시각 집합은 네 항목 중 하나라도 있는 시각의 합집합이다.
		assertThat(hourly).containsExactly(
				new HourlyForecast(LocalTime.of(12, 0), 25.0, null, null, null),
				new HourlyForecast(LocalTime.of(13, 0), null, null, 0, PrecipitationType.NONE));
	}

	@Test
	@DisplayName("다른 날짜의 항목은 빠진다 — 그 날짜 것이 하나도 없으면 빈 목록이다")
	void otherDatesAreExcluded() {
		List<KmaForecastItem> items = concat(List.of(
				slot(DAY.minusDays(1), 23, "19", "1", "0", "0"),
				slot(DAY, 9, "20", "1", "0", "0"),
				slot(DAY.plusDays(1), 0, "18", "1", "0", "0")));

		assertThat(KmaForecastAggregator.hourly(items, DAY))
				.extracting(HourlyForecast::time).containsExactly(LocalTime.of(9, 0));
		assertThat(KmaForecastAggregator.hourly(items, DAY.plusDays(5))).isEmpty();
	}

	@Test
	@DisplayName("원문 순서가 뒤섞여 있어도 시각 오름차순으로 낸다")
	void hourlyIsSortedByTime() {
		List<KmaForecastItem> items = concat(List.of(
				slot(DAY, 21, "20", "1", "0", "0"),
				slot(DAY, 6, "17", "1", "0", "0"),
				slot(DAY, 14, "26", "1", "0", "0"),
				slot(DAY, 0, "18", "1", "0", "0")));

		assertThat(KmaForecastAggregator.hourly(items, DAY)).extracting(HourlyForecast::time).containsExactly(
				LocalTime.of(0, 0), LocalTime.of(6, 0), LocalTime.of(14, 0), LocalTime.of(21, 0));
	}

	@Test
	@DisplayName("강수형태 PTY 는 0 없음 · 1 비 · 2 비/눈 · 3 눈 · 4 소나기이고, 모르는 코드는 null 이다")
	void precipitationTypeMapsKmaCodes() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("PTY", DAY, LocalTime.of(0, 0), "0"),
				new KmaForecastItem("PTY", DAY, LocalTime.of(1, 0), "1"),
				new KmaForecastItem("PTY", DAY, LocalTime.of(2, 0), "2"),
				new KmaForecastItem("PTY", DAY, LocalTime.of(3, 0), "3"),
				new KmaForecastItem("PTY", DAY, LocalTime.of(4, 0), "4"),
				new KmaForecastItem("PTY", DAY, LocalTime.of(5, 0), "7"));

		assertThat(KmaForecastAggregator.hourly(items, DAY)).extracting(HourlyForecast::precipitationType)
				.containsExactly(PrecipitationType.NONE, PrecipitationType.RAIN, PrecipitationType.RAIN_SNOW,
						PrecipitationType.SNOW, PrecipitationType.SHOWER, null);
	}

	@Test
	@DisplayName("🔴 시간별의 이상한 값 하나가 요청을 죽이지 않는다 — 모르는 하늘 코드·못 읽는 기온은 그 칸만 null")
	void unreadableHourlyValueBecomesNullInsteadOfFailing() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("SKY", DAY, LocalTime.of(6, 0), "2"),
				new KmaForecastItem("TMP", DAY, LocalTime.of(6, 0), "-"),
				new KmaForecastItem("POP", DAY, LocalTime.of(6, 0), "30"));

		assertThat(KmaForecastAggregator.hourly(items, DAY)).containsExactly(
				new HourlyForecast(LocalTime.of(6, 0), null, null, 30, null));
	}

	@Test
	@DisplayName("TMN·TMX 같은 다른 항목만 있는 시각은 시간별에 안 들어간다")
	void onlyFourCategoriesMakeASlot() {
		List<KmaForecastItem> items = List.of(
				new KmaForecastItem("TMN", DAY, LocalTime.of(6, 0), "18.0"),
				new KmaForecastItem("TMX", DAY, LocalTime.of(15, 0), "27.0"),
				new KmaForecastItem("TMP", DAY, LocalTime.of(9, 0), "21"));

		assertThat(KmaForecastAggregator.hourly(items, DAY))
				.extracting(HourlyForecast::time).containsExactly(LocalTime.of(9, 0));
	}
}
