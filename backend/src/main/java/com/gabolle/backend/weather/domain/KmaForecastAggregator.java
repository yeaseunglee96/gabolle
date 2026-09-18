package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 3시간 간격 항목들을 하루 요약으로 묶는다 — S15P21E201-366.
 *
 * <p>기상청은 최저·최고기온({@code TMN}·{@code TMX})을 하루에 한 번씩만 주고, 그마저도 발표
 * 시점에 따라 안 줄 때가 있다(이미 지난 시간대라 뺐거나, 그날 몫이 아직 안 왔거나). 그런
 * 경우 같은 날짜의 {@code TMP}(그때그때 기온) 값들 중 최솟값·최댓값으로 대신한다 — 실제
 * 최저·최고와 정확히 같지는 않지만, 3시간 간격 기온의 최솟값·최댓값이므로 지어낸 값이 아니라
 * 같은 관측·예보 체계 안에서 다시 계산한 값이다.
 */
public final class KmaForecastAggregator {

	private KmaForecastAggregator() {
	}

	/**
	 * @return 그 날짜의 항목이 하나도 없으면 빈 값 — 요청한 날짜가 이 발표 회차의 예보 범위
	 *         밖에 있다는 뜻이다
	 */
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
