package com.gabolle.backend.weather.domain;

/**
 * 하늘 상태 — 기상청 단기예보 {@code SKY} 항목 코드(1=맑음, 3=구름많음, 4=흐림) 을 옮긴다.
 * S15P21E201-366.
 */
public enum SkyCondition {

	CLEAR,
	PARTLY_CLOUDY,
	CLOUDY;

	/** @throws IllegalArgumentException 기상청이 준 적 없는 코드다 — 지어내지 않고 실패한다 */
	public static SkyCondition fromKmaCode(String code) {
		return switch (code) {
			case "1" -> CLEAR;
			case "3" -> PARTLY_CLOUDY;
			case "4" -> CLOUDY;
			default -> throw new IllegalArgumentException("알 수 없는 하늘상태 코드: " + code);
		};
	}
}
