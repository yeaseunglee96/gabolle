package com.gabolle.backend.weather.presentation.dto;

import java.time.format.DateTimeFormatter;

import com.gabolle.backend.weather.domain.HourlyForecast;

/**
 * {@code GET /api/v1/weather} 응답의 시간별 예보 한 칸. time 은 {@code "HH:mm"} 이다(그 날짜는
 * 옆의 forecast.date). 원문에 없는 값은 null 이다 — 0 과 다르다.
 */
public record HourlyForecastDto(String time, Double temperature, String skyCondition,
		Integer precipitationProbability, String precipitationType) {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	public static HourlyForecastDto from(HourlyForecast hourly) {
		return new HourlyForecastDto(hourly.time().format(TIME_FORMAT), hourly.temperature(),
				hourly.skyCondition() == null ? null : hourly.skyCondition().name(),
				hourly.precipitationProbability(),
				hourly.precipitationType() == null ? null : hourly.precipitationType().name());
	}
}
