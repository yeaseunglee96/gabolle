package com.gabolle.backend.recommendation.support;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 추천 Job 이 가리켜야 하는 실제 행을 만든다 — 사용자 · 여행 · 입력 스냅샷.
 *
 * <p>🔴 <b>왜 이 클래스가 생겼는가.</b> S15P21E201-543 의 테스트들은 스냅샷 ID 자리에
 * {@code UUID.randomUUID()} 를 넣고 있었다. 그때는 아무것도 그 값을 검사하지 않았으므로
 * 전부 통과했지만, <b>그 초록은 "참조가 맞다" 를 전혀 뜻하지 않았다.</b> 가리키는 행이
 * 하나도 없는 ID 였다.
 *
 * <p>S15P21E201-554 가 {@code recommendation_job} 에 외래키를 붙이면서 그 사실이 드러났다.
 * 이제는 실제 행이 있어야 하고, 그 행을 만드는 자리가 여기다. 테스트마다 흩어 놓으면
 * 표가 하나 늘 때 어느 한 곳만 고쳐진다.
 *
 * <p>🔴 JPA 엔티티가 아니라 원시 SQL 로 넣는다. 이유가 둘이다 — 이 표들의 엔티티는
 * S15P21E201-461(모진성) 자리라서 아직 없고, 있더라도 <b>DB 제약 자체</b>를 확인하려면
 * 자바 쪽 검사를 건너뛰어야 한다.
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
