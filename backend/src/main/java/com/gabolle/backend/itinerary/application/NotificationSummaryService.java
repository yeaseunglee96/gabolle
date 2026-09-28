package com.gabolle.backend.itinerary.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.presentation.dto.NotificationSummaryResponse;

/**
 * 종 점 — 내 모든 여행의 활동에 새것이 있나 (S15P21E201-1699).
 *
 * <p>활동은 여행 활동 조회({@link TripActivityService})와 같은 것이다 — 그 여행 일정들의 판. 내가 한 변경도 센다
 * (앱의 종 점이 지금 그렇게 센다). 여행은 여행 목록과 같은 규칙으로 고른다 — 참여 행({@code trip_member})이 있고 지우지
 * 않은 여행. 주인·편집자·열람자를 가리지 않는다(열람자도 활동을 본다).
 *
 * <p>🔴 질의는 여행 수와 무관하게 한 번이다. 앱이 여행마다 활동을 불러 보던 것(최근 셋만)을 대신하는 자리라, 여기서
 * 여행마다 읽으면 옮겨 온 의미가 없다. 가장 최근 시각 하나만 뽑는다 — 「그보다 나중 것이 있나」는 「가장 최근 것이
 * 그보다 나중인가」와 같다.
 *
 * <p>본 시각은 서버가 기억하지 않는다. 앱이 기기에 둔 값을 {@code since} 로 준다.
 */
@Service
@Profile({ "db", "dev" })
public class NotificationSummaryService {

	private static final String LATEST_ACTIVITY = """
			SELECT max(v.created_at) AS latest_at
			  FROM trip_member m
			  JOIN trip t ON t.trip_id = m.trip_id AND t.deleted_at IS NULL
			  JOIN itineraries i ON i.trip_id = t.trip_id
			  JOIN itinerary_versions v ON v.itinerary_id = i.itinerary_id
			 WHERE m.user_id = ?
			""";

	private final JdbcTemplate jdbc;

	public NotificationSummaryService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param since 앱이 마지막으로 본 시각. 한 번도 안 봤으면 {@code null}
	 */
	@Transactional(readOnly = true)
	public NotificationSummaryResponse summarize(UUID userId, Instant since) {
		OffsetDateTime latest = this.jdbc.queryForObject(LATEST_ACTIVITY,
				(row, n) -> row.getObject("latest_at", OffsetDateTime.class), userId);
		if (latest == null) {
			return new NotificationSummaryResponse(false, null);
		}
		Instant latestAt = latest.toInstant();
		return new NotificationSummaryResponse(since == null || latestAt.isAfter(since), latestAt.toString());
	}
}
