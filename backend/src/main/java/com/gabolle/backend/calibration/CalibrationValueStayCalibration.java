package com.gabolle.backend.calibration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code calibration_value} 에서 체류 작업의 「갈래마다 가장 최근 PASSED」를 읽는다 — S15P21E201-1692.
 *
 * <p>🔴 끄는 스위치는 {@code gabolle.calibration.stay.enabled}(환경변수 {@code GABOLLE_CALIBRATION_STAY_ENABLED})다. 꺼져 있으면
 * 이 부품이 아예 안 생기고 일정 조립은 기본값만 쓴다. 기본은 꺼짐이다.
 *
 * <p>되돌리기는 표에서 그 판을 {@code REVOKED} 로 바꾸는 것이다. 여기는 PASSED 만 보므로 다음 읽기부터 그 갈래의 직전
 * PASSED 가 나온다. 읽은 값은 {@link #REFRESH} 동안 기억한다 — 일정을 짤 때마다 표를 읽지 않으면서, 되돌린 것이 늦어도 그
 * 안에 반영되게.
 *
 * <p>읽다가 DB 오류가 나면 기억해 둔 값(처음이면 빈 값 = 기본값)을 그대로 쓴다. 보정은 없어도 일정은 짜여야 한다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnProperty(name = "gabolle.calibration.stay.enabled", havingValue = "true")
public class CalibrationValueStayCalibration implements StayCalibrationPort {

	static final String JOB = "stay_minutes";

	static final Duration REFRESH = Duration.ofMinutes(10);

	private static final Logger log = LoggerFactory.getLogger(CalibrationValueStayCalibration.class);

	private static final String LATEST_PASSED = """
			SELECT DISTINCT ON (key) key, value
			  FROM calibration_value
			 WHERE job = ? AND basis = 'MEASURED' AND status = 'PASSED'
			 ORDER BY key, version DESC
			""";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private volatile Snapshot snapshot;

	public CalibrationValueStayCalibration(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public OptionalInt minutesFor(String category) {
		if (category == null) {
			return OptionalInt.empty();
		}
		Integer minutes = current().minutes().get(category.trim().toUpperCase(Locale.ROOT));
		return (minutes == null) ? OptionalInt.empty() : OptionalInt.of(minutes);
	}

	private Snapshot current() {
		Instant now = this.clock.instant();
		Snapshot held = this.snapshot;
		if (held != null && now.isBefore(held.readAt().plus(REFRESH))) {
			return held;
		}
		Map<String, Integer> minutes = new HashMap<>();
		try {
			this.jdbc.query(LATEST_PASSED, (row) -> {
				minutes.put(row.getString("key"), (int) Math.round(row.getDouble("value")));
			}, JOB);
		}
		catch (DataAccessException exception) {
			log.warn("체류 보정값을 못 읽어 기억해 둔 값을 쓴다: {}", exception.getMessage());
			Snapshot kept = new Snapshot((held == null) ? Map.of() : held.minutes(), now);
			this.snapshot = kept;
			return kept;
		}
		Snapshot fresh = new Snapshot(Map.copyOf(minutes), now);
		this.snapshot = fresh;
		return fresh;
	}

	private record Snapshot(Map<String, Integer> minutes, Instant readAt) {
	}
}
