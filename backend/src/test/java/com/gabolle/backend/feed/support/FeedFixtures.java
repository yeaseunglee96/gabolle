package com.gabolle.backend.feed.support;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 피드 테스트가 필요로 하는 앞선 행들을 넣는다.
 *
 * <p>엔티티가 아니라 SQL 로 넣는다. {@code app_user} 는 이 슬라이스가 스캔하지 않는
 * 다른 도메인의 엔티티이고, {@code constraint_snapshot} 은 엔티티가 아예 없다 — 여기서
 * 만들면 같은 표에 주인이 둘이 된다.
 *
 * <p>테스트마다 새 사용자를 만든다. 이 테스트들은 트랜잭션으로 되돌리지 않는데, 되돌리면
 * DB 제약이 실제로 걸리는지 볼 수 없는 경우가 생기기 때문이다.
 */
public final class FeedFixtures {

	private final JdbcTemplate jdbc;

	public FeedFixtures(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 이 테스트 전용 사용자 하나. */
	public UUID newUser() {
		UUID userId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO app_user
				  (user_id, display_name, language, personalization_mode, status, created_at, updated_at)
				VALUES (?, ?, 'ko', 'BEHAVIOR_ENABLED', 'ACTIVE', now(), now())
				""", userId, "테스트" + userId.toString().substring(0, 8));
		return userId;
	}

	/**
	 * 제약 스냅샷 하나. 홈 피드 세대는 이것 없이 READY 가 될 수 없다
	 * ({@code ck_feed_build_home_needs_constraints}).
	 *
	 * <p>판 번호를 1 로 고정하면 안 된다. {@code constraint_snapshot} 에는 계정 기본값
	 * ({@code trip_id IS NULL})에 대해 {@code (user_id, version)} 조건부 UNIQUE 색인이 걸려
	 * 있어서, 한 사용자에게 두 번째 스냅샷을 넣는 순간 거부당한다.
	 */
	public UUID newConstraintSnapshot(UUID userId) {
		UUID snapshotId = UUID.randomUUID();
		Integer nextVersion = this.jdbc.queryForObject(
				"SELECT COALESCE(MAX(version), 0) + 1 FROM constraint_snapshot WHERE user_id = ? AND trip_id IS NULL",
				Integer.class, userId);
		this.jdbc.update("""
				INSERT INTO constraint_snapshot
				  (constraint_snapshot_id, user_id, trip_id, version, scope, created_at)
				VALUES (?, ?, NULL, ?, 'USER', now())
				""", snapshotId, userId, nextVersion);
		return snapshotId;
	}

	/** 실제 페이로드 모양은 화면이 확정한 뒤 정해진다. */
	public static String payload(String name) {
		return "{\"name\":\"" + name + "\"}";
	}

	public static OffsetDateTime now() {
		return OffsetDateTime.now();
	}
}
