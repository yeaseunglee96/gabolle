package com.gabolle.backend.weather.domain;

import java.time.LocalDate;

/** 날씨 조회 요청 하나. 좌표 검사를 컨트롤러가 아니라 여기서 한다. */
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
