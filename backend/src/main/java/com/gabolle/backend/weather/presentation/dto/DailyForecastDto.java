package com.gabolle.backend.weather.presentation.dto;

import com.gabolle.backend.weather.domain.DailyForecast;

/** 하루치 예보 응답 조각 — {@code GET /api/v1/weather} 응답의 본문. S15P21E201-366. */
public record DailyForecastDto(String date, Double minTemperature, Double maxTemperature,
		Integer precipitationProbability, String skyCondition) {

	public static DailyForecastDto from(DailyForecast forecast) {
		return new DailyForecastDto(forecast.date().toString(), forecast.minTemperature(),
				forecast.maxTemperature(), forecast.precipitationProbability(),
				forecast.skyCondition() == null ? null : forecast.skyCondition().name());
	}
}
