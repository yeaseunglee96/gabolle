package com.gabolle.backend.weather.presentation.dto;

import com.gabolle.backend.weather.domain.WeatherForecastResult;

/** {@code GET /api/v1/weather} 응답 본문. */
public record WeatherForecastResponseDto(int nx, int ny, boolean cached, DailyForecastDto forecast) {

	public static WeatherForecastResponseDto from(WeatherForecastResult result) {
		return new WeatherForecastResponseDto(result.grid().nx(), result.grid().ny(), result.cached(),
				DailyForecastDto.from(result.forecast()));
	}
}
