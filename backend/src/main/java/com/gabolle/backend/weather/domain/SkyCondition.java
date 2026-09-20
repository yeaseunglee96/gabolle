package com.gabolle.backend.weather.domain;

/** 기상청 단기예보 SKY 항목 코드를 옮긴다 — 1=맑음, 3=구름많음, 4=흐림. */
public enum SkyCondition {

	CLEAR,
	PARTLY_CLOUDY,
	CLOUDY;

	/** 모르는 코드는 지어내지 않고 실패한다. */
	public static SkyCondition fromKmaCode(String code) {
		return switch (code) {
			case "1" -> CLEAR;
			case "3" -> PARTLY_CLOUDY;
			case "4" -> CLOUDY;
			default -> throw new IllegalArgumentException("알 수 없는 하늘상태 코드: " + code);
		};
	}
}
