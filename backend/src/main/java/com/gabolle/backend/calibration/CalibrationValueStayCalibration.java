package com.gabolle.backend.calibration;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalInt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code calibration_value} 에서 체류 작업의 「갈래마다 가장 최근 PASSED」를 읽는다 — S15P21E201-1692.
 *
 * <p>🔴 끄는 스위치는 {@code gabolle.calibration.stay.enabled}(환경변수 {@code GABOLLE_CALIBRATION_STAY_ENABLED})다. 꺼져 있으면
 * 이 부품이 아예 안 생기고 일정 조립은 기본값만 쓴다. 기본은 꺼짐이다.
 *
 * <p>읽기 · 기억 · 되돌림 규칙은 {@link LatestPassedValues} 에 있다 — 이동 시간 창구(S15P21E201-1700)와 같이 쓴다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.calibration.stay.enabled", havingValue = "true")
public class CalibrationValueStayCalibration implements StayCalibrationPort {

	static final String JOB = "stay_minutes";

	static final Duration REFRESH = LatestPassedValues.REFRESH;

	private final LatestPassedValues values;

	public CalibrationValueStayCalibration(JdbcTemplate jdbc, Clock clock) {
		this.values = new LatestPassedValues(jdbc, clock, JOB);
	}

	@Override
	public OptionalInt minutesFor(String category) {
		Double minutes = this.values.valueFor(category);
		return (minutes == null) ? OptionalInt.empty() : OptionalInt.of((int) Math.round(minutes));
	}
}
