package com.gabolle.backend.weather.presentation.dto;

import com.gabolle.backend.weather.domain.DailyForecast;

/** {@code GET /api/v1/weather} 응답의 하루치 예보 조각. */
public record DailyForecastDto(String date, Double minTemperature, Double maxTemperature,
		Integer precipitationProbability, String skyCondition) {

	public static DailyForecastDto from(DailyForecast forecast) {
		return new DailyForecastDto(forecast.date().toString(), forecast.minTemperature(),
				forecast.maxTemperature(), forecast.precipitationProbability(),
				forecast.skyCondition() == null ? null : forecast.skyCondition().name());
	}
}
