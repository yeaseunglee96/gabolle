package com.gabolle.backend.weather.domain;

import java.util.List;

/**
 * 날씨 조회 결과 하나. cached 는 이번 요청에서 업체를 부르지 않고 캐시로 답했는지다.
 *
 * hourly 는 forecast(하루 요약)를 만든 바로 그 원문에서 뽑은 그 날짜의 시간별 예보다 —
 * 시각 오름차순이고, 원문에 있는 시각만 담는다(간격을 채우지 않는다).
 */
public record WeatherForecastResult(KmaGridCoordinate grid, DailyForecast forecast, List<HourlyForecast> hourly,
		boolean cached) {

	public WeatherForecastResult {
		hourly = List.copyOf(hourly);
	}
}
