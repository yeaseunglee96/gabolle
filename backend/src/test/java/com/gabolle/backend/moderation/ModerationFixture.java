package com.gabolle.backend.moderation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 신고·검토 통합 테스트의 시드 SQL. 기록·사용자 시드는
 * {@code com.gabolle.backend.story.StoryFixture} 를 그대로 쓴다.
 */
public final class ModerationFixture {

	private ModerationFixture() {
	}

	/** 운영자로 승격한다. 가입 경로로는 만들 수 없는 값이라 시드에서 직접 올린다. */
	public static void promoteToAdmin(JdbcTemplate jdbc, UUID userId) {
		jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE user_id = ?", userId);
	}

	/** 신고 한 건을 직접 심는다 — 검토 목록 정렬·묶음을 보려면 접수 시각을 마음대로 벌려야 한다. */
	public static UUID insertReport(JdbcTemplate jdbc, UUID storyId, UUID reporterUserId, String reason,
			Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO story_report (story_report_id, story_id, reporter_user_id, reason, created_at) "
						+ "VALUES (?, ?, ?, ?, ?)",
				id, storyId, reporterUserId, reason, createdAt.atOffset(ZoneOffset.UTC));
		return id;
	}

	public static boolean isPending(JdbcTemplate jdbc, UUID reportId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT resolved_at IS NULL FROM story_report WHERE story_report_id = ?", Boolean.class, reportId));
	}

	public static String moderationState(JdbcTemplate jdbc, UUID storyId) {
		return jdbc.queryForObject("SELECT moderation_state FROM story WHERE story_id = ?", String.class, storyId);
	}

	public static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}
}
