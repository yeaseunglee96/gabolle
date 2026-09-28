package com.gabolle.backend.weather.presentation.dto;

import java.util.List;

import com.gabolle.backend.weather.domain.WeatherForecastResult;

/**
 * {@code GET /api/v1/weather} 응답 본문.
 *
 * hourly 는 나중에 더한 칸이다(S15P21E201-1534). forecast 를 비롯한 앞의 칸은 이름도 뜻도 그대로라
 * hourly 를 모르는 옛 화면은 그대로 돈다.
 */
public record WeatherForecastResponseDto(int nx, int ny, boolean cached, DailyForecastDto forecast,
		List<HourlyForecastDto> hourly) {

	public static WeatherForecastResponseDto from(WeatherForecastResult result) {
		return new WeatherForecastResponseDto(result.grid().nx(), result.grid().ny(), result.cached(),
				DailyForecastDto.from(result.forecast()),
				result.hourly().stream().map(HourlyForecastDto::from).toList());
	}
}
