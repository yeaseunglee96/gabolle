package com.gabolle.backend.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link KmaForecastAggregator} 검증 — S15P21E201-366. */
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
		// 🔴 강수확률은 그 날의 최댓값을 쓴다
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
}
