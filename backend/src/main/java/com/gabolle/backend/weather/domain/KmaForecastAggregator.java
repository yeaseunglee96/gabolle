package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 3시간 간격 항목들을 하루 요약으로 묶는다.
 *
 * 기상청은 최저·최고기온(TMN·TMX)을 하루에 한 번만 주고 발표 시점에 따라 아예 안 주기도
 * 한다. 그때는 같은 날짜 TMP 값들의 최솟값·최댓값으로 대신한다 — 실제 최저·최고와 정확히
 * 같지는 않다.
 */
public final class KmaForecastAggregator {

	private KmaForecastAggregator() {
	}

	/** 그 날짜의 항목이 하나도 없으면 빈 값이다 — 이 발표 회차의 예보 범위 밖이라는 뜻이다. */
	public static Optional<DailyForecast> aggregate(List<KmaForecastItem> items, LocalDate date) {
		List<KmaForecastItem> ofDay = items.stream().filter(item -> item.fcstDate().equals(date)).toList();
		if (ofDay.isEmpty()) {
			return Optional.empty();
		}

		Double minTemp = singleValue(ofDay, "TMN").map(Double::parseDouble)
				.orElseGet(() -> minOfCategory(ofDay, "TMP"));
		Double maxTemp = singleValue(ofDay, "TMX").map(Double::parseDouble)
				.orElseGet(() -> maxOfCategory(ofDay, "TMP"));
		Integer pop = ofDay.stream()
				.filter(item -> item.category().equals("POP"))
				.map(item -> Integer.parseInt(item.value()))
				.max(Comparator.naturalOrder())
				.orElse(null);
		SkyCondition sky = ofDay.stream()
				.filter(item -> item.category().equals("SKY"))
				.min(Comparator.comparingLong(item -> Math.abs(minutesFromNoon(item.fcstTime()))))
				.map(item -> SkyCondition.fromKmaCode(item.value()))
				.orElse(null);

		return Optional.of(new DailyForecast(date, minTemp, maxTemp, pop, sky));
	}

	private static Optional<String> singleValue(List<KmaForecastItem> items, String category) {
		return items.stream().filter(item -> item.category().equals(category)).map(KmaForecastItem::value).findFirst();
	}

	private static Double minOfCategory(List<KmaForecastItem> items, String category) {
		return items.stream().filter(item -> item.category().equals(category))
				.map(item -> Double.parseDouble(item.value()))
				.min(Comparator.naturalOrder())
				.orElse(null);
	}

	private static Double maxOfCategory(List<KmaForecastItem> items, String category) {
		return items.stream().filter(item -> item.category().equals(category))
				.map(item -> Double.parseDouble(item.value()))
				.max(Comparator.naturalOrder())
				.orElse(null);
	}

	private static long minutesFromNoon(LocalTime time) {
		return Math.abs(java.time.Duration.between(LocalTime.NOON, time).toMinutes());
	}
}
