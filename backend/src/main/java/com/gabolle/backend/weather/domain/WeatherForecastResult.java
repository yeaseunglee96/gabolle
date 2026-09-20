package com.gabolle.backend.weather.domain;

/** 날씨 조회 결과 하나. cached 는 이번 요청에서 업체를 부르지 않고 캐시로 답했는지다. */
public record WeatherForecastResult(KmaGridCoordinate grid, DailyForecast forecast, boolean cached) {
}
