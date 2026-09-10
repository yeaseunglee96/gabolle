package com.gabolle.backend.weather.domain;

import java.time.LocalDate;

/**
 * 날씨 조회 요청 하나 — S15P21E201-366.
 *
 * <p>🔴 좌표 검사를 <b>여기서</b> 한다 — {@code RouteQuery} 가 같은 이유로 자기 생성자에서
 * 좌표를 검사하는 것과 같은 판단이다.
 */
public record WeatherQuery(double lat, double lon, LocalDate date) {

	public WeatherQuery {
		if (lat < -90 || lat > 90) {
			throw new IllegalArgumentException("lat 은 -90~90 이어야 합니다: " + lat);
		}
		if (lon < -180 || lon > 180) {
			throw new IllegalArgumentException("lon 은 -180~180 이어야 합니다: " + lon);
		}
		if (date == null) {
			throw new IllegalArgumentException("date 가 필요합니다.");
		}
	}
}
