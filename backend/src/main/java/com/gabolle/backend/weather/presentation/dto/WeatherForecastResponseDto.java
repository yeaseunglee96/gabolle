package com.gabolle.backend.weather.presentation.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.gabolle.backend.weather.domain.HourlyForecast;
import com.gabolle.backend.weather.domain.WeatherForecastResult;

/**
 * {@code GET /api/v1/weather} 응답 본문.
 *
 * hourly 는 나중에 더한 칸이다(S15P21E201-1534). forecast 를 비롯한 앞의 칸은 이름도 뜻도 그대로라
 * hourly 를 모르는 옛 화면은 그대로 돈다.
 *
 * <p>currentTemperature·currentTime 도 나중에 더한 칸이다(S15P21E201-1979). 앱 머리말이 「부산 지금」인데
 * forecast.maxTemperature(하루 최고)를 보여 주고 있었다. 우리가 부르는 기상청 단기예보(getVilageFcst)에는
 * 실황값이 없어서, 오늘을 물을 때 시간별 예보 가운데 지금 시각에 가장 가까운 칸(기온이 있는 것, 90분 안)의
 * 기온을 준다. currentTime 은 그 칸의 시각("HH:mm")이다. 오늘이 아니거나 가까운 칸이 없으면 둘 다 null —
 * 다른 날의 같은 시각이나 먼 칸을 「지금」이라 부르지 않는다.
 */
public record WeatherForecastResponseDto(int nx, int ny, boolean cached, DailyForecastDto forecast,
		List<HourlyForecastDto> hourly, Double currentTemperature, String currentTime) {

	/** 이보다 멀리 떨어진 칸은 지금 기온이라 하지 않는다. 단기예보는 한 시간 간격이라 보통 0~30분 안에 있다. */
	static final Duration CURRENT_SLOT_MAX_DISTANCE = Duration.ofMinutes(90);

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	/** @param nowInSeoul 지금 시각(서울). 응답을 만드는 순간의 값이라 캐시에서 꺼낸 예보에도 맞게 계산된다. */
	public static WeatherForecastResponseDto from(WeatherForecastResult result, LocalDateTime nowInSeoul) {
		Optional<HourlyForecast> current = currentSlot(result, nowInSeoul);
		return new WeatherForecastResponseDto(result.grid().nx(), result.grid().ny(), result.cached(),
				DailyForecastDto.from(result.forecast()),
				result.hourly().stream().map(HourlyForecastDto::from).toList(),
				current.map(HourlyForecast::temperature).orElse(null),
				current.map((slot) -> slot.time().format(TIME_FORMAT)).orElse(null));
	}

	static Optional<HourlyForecast> currentSlot(WeatherForecastResult result, LocalDateTime nowInSeoul) {
		if (result.forecast() == null || !nowInSeoul.toLocalDate().equals(result.forecast().date())) {
			return Optional.empty();
		}
		LocalTime now = nowInSeoul.toLocalTime();
		return result.hourly().stream()
				.filter((slot) -> slot.temperature() != null && slot.time() != null)
				.filter((slot) -> distance(slot.time(), now).compareTo(CURRENT_SLOT_MAX_DISTANCE) <= 0)
				// 가장 가까운 칸, 거리가 같으면 앞의(이미 지난) 칸.
				.min(Comparator.comparing((HourlyForecast slot) -> distance(slot.time(), now))
						.thenComparing(HourlyForecast::time));
	}

	private static Duration distance(LocalTime a, LocalTime b) {
		return Duration.between(a, b).abs();
	}
}
