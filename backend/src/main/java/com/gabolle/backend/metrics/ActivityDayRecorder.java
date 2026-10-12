package com.gabolle.backend.metrics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 「이 사람이 오늘 왔다」를 하루 한 줄로 남긴다(S15P21E201-2010) — MAU·DAU 의 바탕.
 *
 * <p>같은 사람의 같은 날 두 번째 요청부터는 메모리에서 걸러 DB 를 건드리지 않는다. 서버를 다시 띄우면 메모리가 비지만
 * {@code ON CONFLICT DO NOTHING}(이미 있으면 조용히 넘어가기)이라 두 줄이 되지 않는다.
 *
 * <p>🔴 기록이 실패해도 요청은 그대로 간다 — 지표 때문에 앱이 멈추면 안 된다.
 */
@Component
@Profile({ "db", "dev" })
public class ActivityDayRecorder {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final Logger log = LoggerFactory.getLogger(ActivityDayRecorder.class);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final Map<String, LocalDate> seen = new ConcurrentHashMap<>();

	@Autowired
	public ActivityDayRecorder(JdbcTemplate jdbc) {
		this(jdbc, Clock.system(SEOUL));
	}

	ActivityDayRecorder(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public void user(UUID userId) {
		record("u:" + userId, "INSERT INTO user_activity_day (user_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING",
				userId);
	}

	public void anonymous(UUID sessionId) {
		record("a:" + sessionId,
				"INSERT INTO anonymous_activity_day (session_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING", sessionId);
	}

	private void record(String key, String sql, UUID id) {
		LocalDate today = LocalDate.now(this.clock.withZone(SEOUL));
		if (today.equals(this.seen.get(key))) {
			return;
		}
		try {
			this.jdbc.update(sql, id, today);
			this.seen.put(key, today);
			if (this.seen.size() > 200_000) {
				this.seen.values().removeIf(day -> !day.equals(today));
			}
		}
		catch (RuntimeException ex) {
			log.warn("activity day record failed: {}", ex.getClass().getSimpleName());
		}
	}
}
