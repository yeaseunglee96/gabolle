package com.gabolle.backend.weather.domain;

/**
 * 날씨 조회 결과 하나 — S15P21E201-366.
 *
 * @param grid 요청 좌표가 변환된 기상청 격자
 * @param forecast 요청한 날짜의 하루 요약
 * @param cached 이번 요청에서 업체를 부르지 않고 캐시로 답했는가
 */
public record WeatherForecastResult(KmaGridCoordinate grid, DailyForecast forecast, boolean cached) {
}
