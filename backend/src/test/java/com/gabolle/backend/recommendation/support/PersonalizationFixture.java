package com.gabolle.backend.recommendation.support;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 추천 Job 이 가리켜야 하는 실제 행을 만든다 — 사용자 · 여행 · 입력 스냅샷.
 * {@code recommendation_job} 에 외래키가 있어 임의 UUID 로는 안 되고, 만드는 자리를 한 곳에
 * 모아야 표가 하나 늘 때 여러 곳이 어긋나지 않는다.
 *
 * JPA 엔티티가 아니라 원시 SQL 로 넣는다 — 이 표들의 엔티티가 아직 없고, 있더라도 DB 제약
 * 자체를 확인하려면 자바 쪽 검사를 건너뛰어야 한다.
 */
public final class PersonalizationFixture {

	/** 한 추천 요청이 가리킬 참조 묶음. */
	public record Ids(UUID userId, UUID tripId, int tripVersion, UUID preferenceSnapshotId,
			UUID constraintSnapshotId) {
	}

	private PersonalizationFixture() {
	}

	/**
	 * 사용자 → 여행 → 취향·제약 스냅샷을 순서대로 넣고 그 ID 를 돌려준다.
	 *
	 * <p>순서가 중요하다. 외래키가 있으므로 가리켜지는 쪽이 먼저 있어야 한다.
	 */
	public static Ids insert(JdbcTemplate jdbc) {
		UUID userId = insertUser(jdbc);
		UUID tripId = insertTrip(jdbc, userId);
		return new Ids(userId, tripId, 1, insertPreferenceSnapshot(jdbc, userId, tripId),
				insertConstraintSnapshot(jdbc, userId, tripId));
	}

	public static UUID insertUser(JdbcTemplate jdbc) {
		UUID userId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO app_user (
				    user_id, display_name, language, personalization_mode, status, created_at, updated_at)
				VALUES (?, '테스트 사용자', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', now(), now())
				""", userId);
		return userId;
	}

	public static UUID insertTrip(JdbcTemplate jdbc, UUID ownerUserId) {
		UUID tripId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO trip (
				    trip_id, owner_user_id, version, start_date, end_date,
				    party_size, budget_krw, travel_modes, created_at, updated_at)
				VALUES (?, ?, 1, ?, ?, 2, 500000, ARRAY['WALK', 'SUBWAY']::VARCHAR(30)[], now(), now())
				""", tripId, ownerUserId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
		return tripId;
	}

	public static UUID insertPreferenceSnapshot(JdbcTemplate jdbc, UUID userId, UUID tripId) {
		UUID snapshotId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO preference_snapshot (
				    preference_snapshot_id, user_id, trip_id, version, scope, survey_version, created_at)
				VALUES (?, ?, ?, 1, 'TRIP', 'survey-2026-09', now())
				""", snapshotId, userId, tripId);
		return snapshotId;
	}

	public static UUID insertConstraintSnapshot(JdbcTemplate jdbc, UUID userId, UUID tripId) {
		UUID snapshotId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO constraint_snapshot (
				    constraint_snapshot_id, user_id, trip_id, version, scope, created_at)
				VALUES (?, ?, ?, 1, 'TRIP', now())
				""", snapshotId, userId, tripId);
		return snapshotId;
	}
}
