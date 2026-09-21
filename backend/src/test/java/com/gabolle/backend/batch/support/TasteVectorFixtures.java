package com.gabolle.backend.batch.support;

import com.gabolle.backend.event.domain.EventType;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 배치 테스트가 쓰는 밑자료.
 *
 * <p>시각을 인자로 받는다 — {@code now()} 를 쓰지 않는다. 이 배치의 검사는 대부분 어느
 * 구간을 봤나에 관한 것이라, 밑자료의 시각을 테스트가 정하지 못하면 검사할 수 없다.
 */
public final class TasteVectorFixtures {

	private final JdbcTemplate jdbc;

	public TasteVectorFixtures(JdbcTemplate jdbc) {
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

	/** 탈퇴한 사용자. 배치가 이 사람을 고르면 안 된다. */
	public UUID newDeletedUser() {
		UUID userId = newUser();
		this.jdbc.update("UPDATE app_user SET deleted_at = now() WHERE user_id = ?", userId);
		return userId;
	}

	/**
	 * 계정 기본 설문 한 판.
	 *
	 * <p>판 번호를 1 로 박지 않는다. 설문을 다시 내는 검사가 있고,
	 * {@code preference_snapshot} 은 {@code (user_id, version)} 이 겹치는 것을 허용하지 않는다.
	 */
	public UUID newUserScopeSnapshot(UUID userId, OffsetDateTime createdAt) {
		UUID snapshotId = UUID.randomUUID();
		Integer nextVersion = this.jdbc.queryForObject(
				"SELECT COALESCE(MAX(version), 0) + 1 FROM preference_snapshot "
						+ "WHERE user_id = ? AND trip_id IS NULL",
				Integer.class, userId);
		this.jdbc.update("""
				INSERT INTO preference_snapshot
				  (preference_snapshot_id, user_id, trip_id, version, scope, survey_version, created_at)
				VALUES (?, ?, NULL, ?, 'USER', 'test-survey-v1', ?)
				""", snapshotId, userId, nextVersion, createdAt);
		return snapshotId;
	}

	/**
	 * 고른 태그형 답 — 앱이 실제로 보내는 모양인 맨 배열이다. 예: {@code ["CAFE","MARKET"]}.
	 *
	 * <p>감싼 모양({@code {"codes":[...]}})은 {@link #selectedCodesWrapped} 로 남긴다 — 이미
	 * 저장된 답이 있어 그쪽도 아직 받아야 한다.
	 */
	public void selectedCodes(UUID snapshotId, String dimension, String... codes) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < codes.length; i++) {
			json.append(i == 0 ? "" : ",").append('"').append(codes[i]).append('"');
		}
		json.append(']');
		insertAnswer(snapshotId, dimension, json.toString(), "SELECTED");
	}

	/** 감싼 태그형 답 {@code {"codes":[...]}} — 옛 모양. 이미 저장된 답이 이렇게 생겼다. */
	public void selectedCodesWrapped(UUID snapshotId, String dimension, String... codes) {
		StringBuilder json = new StringBuilder("{\"codes\":[");
		for (int i = 0; i < codes.length; i++) {
			json.append(i == 0 ? "" : ",").append('"').append(codes[i]).append('"');
		}
		json.append("]}");
		insertAnswer(snapshotId, dimension, json.toString(), "SELECTED");
	}

	/**
	 * 고른 점수형 답 — 앱이 실제로 보내는 모양인 맨 정수 1~5(화면의 5단계 슬라이더).
	 *
	 * <p>{@code PreferenceJson} 이 {@code (raw-1)/4} 로 0~1 에 맞춘다. 그래서 1 은 0.0,
	 * 3 은 0.5, 5 는 1.0 이고, 접기의 {@code toWeight} 를 지나면 각각 -1.0 · 0.0 · +1.0 이 된다.
	 */
	public void selectedLikert(UUID snapshotId, String dimension, int level) {
		insertAnswer(snapshotId, dimension, Integer.toString(level), "SELECTED");
	}

	/** 감싼 점수형 답 {@code {"score":0~1}} — 이 엔진이 정한 옛 계약. 눈금을 다시 안 바꾼다. */
	public void selectedScore(UUID snapshotId, String dimension, double score) {
		insertAnswer(snapshotId, dimension, "{\"score\":" + score + "}", "SELECTED");
	}

	/**
	 * 건너뛴 답.
	 *
	 * <p>값을 실을 수 없다 — {@code ck_preference_answer_value_matches_status} 가 DB 에서
	 * 막는다. 이 배치가 이 행을 성분으로 만들지 않는지를 검사하는 데 쓴다.
	 */
	public void skipped(UUID snapshotId, String dimension) {
		insertAnswer(snapshotId, dimension, null, "SKIPPED");
	}

	private void insertAnswer(UUID snapshotId, String dimension, String valueJson, String status) {
		this.jdbc.update("""
				INSERT INTO preference_answer
				  (preference_answer_id, preference_snapshot_id, dimension, value, answer_status, created_at)
				VALUES (?, ?, ?, CAST(? AS jsonb), ?, now())
				""", UUID.randomUUID(), snapshotId, dimension, valueJson, status);
	}

	/**
	 * 취향 신호 이벤트 하나를 심는다.
	 *
	 * <p>{@code occurred_at} 과 {@code received_at} 을 따로 받는다. 이 배치가 도착 시각으로
	 * 구간을 세는지(늦게 온 이벤트를 안 빠뜨리는지)를 검사해야 하고, 둘이 같으면 그 검사가
	 * 불가능하다.
	 *
	 * <p>종류는 문자열이 아니라 {@link EventType} 으로 받는다. 표에 실제로 들어가는 값은
	 * {@link EventType#wireName()} 이 만드는 소문자이고, 픽스처가 대문자를 심으면 배치가
	 * 한 건도 못 맞히는 동안에도 검사는 초록이 된다.
	 */
	public void tasteSignal(UUID userId, EventType eventType, OffsetDateTime occurredAt, OffsetDateTime receivedAt) {
		this.jdbc.update("""
				INSERT INTO event_outbox
				  (event_id, event_type, event_version, aggregate_type, aggregate_id, partition_key,
				   payload, occurred_at, received_at, user_id, producer)
				VALUES (?, ?, 1, 'trip', ?, ?, '{}'::jsonb, ?, ?, ?, 'SERVER')
				""", UUID.randomUUID(), eventType.wireName(), userId, userId.toString(), occurredAt, receivedAt, userId);
	}
}
