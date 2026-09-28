package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 기상청 단기예보 항목들을 하루 요약(aggregate)과 시간별 예보(hourly)로 묶는다.
 *
 * 기상청은 최저·최고기온(TMN·TMX)을 하루에 한 번만 주고 발표 시점에 따라 아예 안 주기도
 * 한다. 그때는 같은 날짜 TMP 값들의 최솟값·최댓값으로 대신한다 — 실제 최저·최고와 정확히
 * 같지는 않다.
 *
 * 항목 간격은 고정이 아니다. 2026-09-23 운영 캐시 실측(14:00 발표 원문 798행, 요청 한도
 * 1000행 안이라 잘림 없음)에서 TMP·SKY·POP·PTY 는 — 오늘은 15~23시 1시간 간격(이미 지난
 * 시각은 없음), 이튿날·사흗날은 하루 24개 1시간 간격, 나흘째는 3시간 간격 8개, 닷새째는
 * 00시 하나뿐이었다. 그래서 hourly 는 원문에 있는 시각만 그대로 내보내고 비는 시각을
 * 채우거나 간격을 지어내지 않는다.
 */
public final class KmaForecastAggregator {

	/** 시간별 예보에 쓰는 항목 — 기온 · 하늘상태 · 강수확률 · 강수형태. */
	private static final Set<String> HOURLY_CATEGORIES = Set.of("TMP", "SKY", "POP", "PTY");

	private KmaForecastAggregator() {
	}

	/**
	 * 그 날짜의 시간별 예보를 시각 오름차순으로 낸다. 시각 집합은 TMP·SKY·POP·PTY 중 하나라도
	 * 있는 fcstTime 의 합집합이고, 그 시각에 없는 항목은 null 이다 — 0 으로 채우지 않는다.
	 *
	 * 값을 못 읽거나 모르는 코드여도 null 로 둔다(지어내지 않는다). 이 배열은 하루 요약 옆에
	 * 나중에 더한 칸이라, 한 시각의 이상한 값 하나가 요청 전체를 실패로 만들어 기존 하루 요약까지
	 * 못 받게 하면 안 된다.
	 *
	 * 그 날짜의 항목이 없으면 빈 목록이다.
	 */
	public static List<HourlyForecast> hourly(List<KmaForecastItem> items, LocalDate date) {
		// 시각 → (항목 → 값). TreeMap 이라 꺼낼 때 시각 오름차순이다.
		Map<LocalTime, Map<String, String>> valuesByTime = new TreeMap<>();
		for (KmaForecastItem item : items) {
			if (!item.fcstDate().equals(date) || !HOURLY_CATEGORIES.contains(item.category())) {
				continue;
			}
			// 같은 시각·같은 항목이 두 번 오면 먼저 온 값을 쓴다 — aggregate 의 singleValue 와 같다.
			valuesByTime.computeIfAbsent(item.fcstTime(), time -> new HashMap<>())
					.putIfAbsent(item.category(), item.value());
		}
		return valuesByTime.entrySet().stream()
				.map(entry -> toHourly(entry.getKey(), entry.getValue()))
				.toList();
	}

	private static HourlyForecast toHourly(LocalTime time, Map<String, String> values) {
		return new HourlyForecast(time,
				parseOrNull(values.get("TMP"), Double::parseDouble),
				parseOrNull(values.get("SKY"), SkyCondition::fromKmaCode),
				parseOrNull(values.get("POP"), Integer::parseInt),
				parseOrNull(values.get("PTY"), code -> PrecipitationType.fromKmaCode(code).orElse(null)));
	}

	/**
	 * 값이 없거나 못 읽으면 null. NumberFormatException 도 IllegalArgumentException 이라 함께 잡힌다
	 * — SkyCondition.fromKmaCode 가 모르는 코드에 던지는 것과 같은 갈래다.
	 */
	private static <T> T parseOrNull(String value, Function<String, T> parser) {
		if (value == null) {
			return null;
		}
		try {
			return parser.apply(value);
		}
		catch (IllegalArgumentException unreadable) {
			return null;
		}
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
