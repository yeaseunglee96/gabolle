package com.gabolle.backend.calibration;

import java.time.Clock;
import java.util.OptionalDouble;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code calibration_value} 에서 이동 작업의 「수단마다 가장 최근 PASSED 배율」을 읽는다 — S15P21E201-1700.
 *
 * <p>🔴 끄는 스위치는 {@code gabolle.calibration.travel.enabled}(환경변수 {@code GABOLLE_CALIBRATION_TRAVEL_ENABLED})다. 체류와
 * 따로다. 꺼져 있으면 이 부품이 아예 안 생기고 구간은 엔진의 어림을 그대로 쓴다. 기본은 꺼짐이다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.calibration.travel.enabled", havingValue = "true")
public class CalibrationValueTravelCalibration implements TravelCalibrationPort {

	static final String JOB = "travel_multiplier";

	private final LatestPassedValues values;

	public CalibrationValueTravelCalibration(JdbcTemplate jdbc, Clock clock) {
		this.values = new LatestPassedValues(jdbc, clock, JOB);
	}

	@Override
	public OptionalDouble multiplierFor(String travelMode) {
		Double multiplier = this.values.valueFor(travelMode);
		return (multiplier == null) ? OptionalDouble.empty() : OptionalDouble.of(multiplier);
	}
}
