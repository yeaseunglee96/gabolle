package com.gabolle.backend.feed.support;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 피드 테스트가 필요로 하는 앞선 행들을 넣는다.
 *
 * <p>🔴 엔티티가 아니라 <b>SQL 로</b> 넣는다. {@code app_user} 는 다른 도메인의 엔티티라
 * 이 슬라이스가 스캔하지 않고, {@code constraint_snapshot} 은 <b>엔티티가 아예 없다</b> —
 * S15P21E201-554 가 "표만 만들고 엔티티는 그 표의 주인이 만든다" 는 원칙으로 표만 만들었다.
 * 여기서 엔티티를 만들면 같은 표에 주인이 둘이 된다.
 *
 * <p>🔴 테스트마다 <b>새 사용자</b>를 만든다. 이 테스트들은 트랜잭션으로 되돌리지 않기
 * 때문이다 — 되돌리면 DB 제약(조건부 UNIQUE 색인 등)이 실제로 걸리는지 볼 수 없는 경우가
 * 생긴다. 대신 서로 안 겹치는 사용자를 써서 테스트끼리 영향을 안 준다.
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
	 * 제약 스냅샷 하나.
	 *
	 * <p>홈 피드 세대는 이것 없이 READY 가 될 수 없다 —
	 * {@code ck_feed_build_home_needs_constraints}. 장소 추천에는 알레르기·휠체어 같은
	 * 안전 판정이 걸리므로, 무엇을 기준으로 걸렀는지 모르는 홈 피드는 내보낼 수 없다.
	 *
	 * <p>🔴 판 번호를 1 로 고정하면 안 된다. {@code constraint_snapshot} 에는 계정 기본값
	 * ({@code trip_id IS NULL})에 대해 {@code (user_id, version)} 조건부 UNIQUE 색인이
	 * 걸려 있어서, 한 사용자에게 두 번째 스냅샷을 넣는 순간 거부당한다. 세대를 갈아타는
	 * 테스트가 정확히 그 경우다 — 새 세대는 새 제약 스냅샷을 가리켜야 하기 때문이다.
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

	/** 앱이 그릴 카드 값의 사본. 실제 모양은 화면이 확정한 뒤 정해진다. */
	public static String payload(String name) {
		return "{\"name\":\"" + name + "\"}";
	}

	public static OffsetDateTime now() {
		return OffsetDateTime.now();
	}
}
