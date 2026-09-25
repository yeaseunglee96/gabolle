package com.gabolle.backend.calibration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 한 작업(job)의 「열쇠마다 가장 최근 PASSED」를 읽어 {@link #REFRESH} 동안 기억한다 (S15P21E201-1692 · -1700).
 * 체류 · 이동 두 창구가 같이 쓴다 — 둘이 따로 들면 기억 시간·되돌림 규칙이 한쪽만 고쳐진다.
 *
 * <p>PASSED 만 본다. 그래서 그 판을 {@code REVOKED} 로 바꾸면 다음 읽기부터 그 열쇠의 직전 PASSED 가 나온다(되돌리기).
 * 읽다가 DB 오류가 나면 기억해 둔 값(처음이면 빈 값 = 부르는 쪽의 기본값)을 그대로 쓴다.
 */
final class LatestPassedValues {

	static final Duration REFRESH = Duration.ofMinutes(10);

	private static final Logger log = LoggerFactory.getLogger(LatestPassedValues.class);

	private static final String LATEST_PASSED = """
			SELECT DISTINCT ON (key) key, value
			  FROM calibration_value
			 WHERE job = ? AND basis = 'MEASURED' AND status = 'PASSED'
			 ORDER BY key, version DESC
			""";

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final String job;

	private volatile Snapshot snapshot;

	LatestPassedValues(JdbcTemplate jdbc, Clock clock, String job) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.job = job;
	}

	/** @return 그 열쇠의 가장 최근 PASSED 값. 없거나 열쇠가 {@code null} 이면 {@code null} */
	Double valueFor(String key) {
		if (key == null) {
			return null;
		}
		return current().values().get(key.trim().toUpperCase(Locale.ROOT));
	}

	private Snapshot current() {
		Instant now = this.clock.instant();
		Snapshot held = this.snapshot;
		if (held != null && now.isBefore(held.readAt().plus(REFRESH))) {
			return held;
		}
		Map<String, Double> values = new HashMap<>();
		try {
			this.jdbc.query(LATEST_PASSED, (row) -> {
				values.put(row.getString("key"), row.getDouble("value"));
			}, this.job);
		}
		catch (DataAccessException exception) {
			log.warn("보정값({})을 못 읽어 기억해 둔 값을 쓴다: {}", this.job, exception.getMessage());
			Snapshot kept = new Snapshot((held == null) ? Map.of() : held.values(), now);
			this.snapshot = kept;
			return kept;
		}
		Snapshot fresh = new Snapshot(Map.copyOf(values), now);
		this.snapshot = fresh;
		return fresh;
	}

	private record Snapshot(Map<String, Double> values, Instant readAt) {
	}
}
