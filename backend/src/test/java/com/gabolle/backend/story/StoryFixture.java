package com.gabolle.backend.story;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 기록·팔로우 통합 테스트의 시드 SQL. 표 모양은 {@code V20260906160000__story_feed_and_follow.sql} 그대로다.
 *
 * <p>사진 업로드 행은 SQL 로 직접 심는다 — 여기서 보는 것은 업로드 파이프라인이 아니라 "주소로 기록을
 * 만든다" 는 쪽이다. 업로드 자체는 {@code ImageUploadIntegrationTest} 가 본다.
 */
public final class StoryFixture {

	public static final String IMAGE_BASE = "/api/v1/uploads/images";

	private StoryFixture() {
	}

	public static UUID insertUser(JdbcTemplate jdbc, String displayName) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				userId, displayName, now, now);
		return userId;
	}

	public static UUID insertPlace(JdbcTemplate jdbc, String nameKo, String address) {
		UUID placeId = UUID.randomUUID();
		jdbc.update("INSERT INTO place (place_id, name_ko, address, lat, lng, created_at) VALUES (?, ?, ?, 35.16, 129.16, now())",
				placeId, nameKo, address);
		return placeId;
	}

	public static UUID insertTrip(JdbcTemplate jdbc, UUID ownerUserId, LocalDate start, LocalDate end, String timezone) {
		UUID tripId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, timezone, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, 1, ?, ?, ?)",
				tripId, ownerUserId, start, end, timezone, now, now);
		return tripId;
	}

	/**
	 * 테스트마다 다른 저장 키. 같은 DB 를 여러 테스트·여러 실행이 함께 쓰고 {@code uq_uploaded_image_key} 가 있어서,
	 * 고정 문자열을 쓰면 두 번째 실행부터 시드가 중복 키로 죽는다.
	 */
	public static String key(String tag) {
		return "story/2026/09/" + tag + "-" + UUID.randomUUID() + ".jpg";
	}

	/** 업로드 행 하나. 돌려주는 값은 기록 작성 요청에 실을 주소다. */
	public static String insertUploadedImage(JdbcTemplate jdbc, UUID uploaderUserId, String key) {
		String url = IMAGE_BASE + "/" + key;
		jdbc.update(
				"INSERT INTO uploaded_image (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at) "
						+ "VALUES (?, ?, ?, ?, 'image/jpeg', 1234, now())",
				UUID.randomUUID(), uploaderUserId, key, url);
		return url;
	}

	public static UUID insertStory(JdbcTemplate jdbc, UUID authorUserId, String body, String visibility,
			Instant publishAt) {
		UUID storyId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.update(
				"INSERT INTO story (story_id, author_user_id, body, visibility, publish_at, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?)",
				storyId, authorUserId, body, visibility, publishAt.atOffset(ZoneOffset.UTC), now, now);
		return storyId;
	}

	public static void insertFollow(JdbcTemplate jdbc, UUID follower, UUID followee) {
		jdbc.update("INSERT INTO user_follow (follower_user_id, followee_user_id, created_at) VALUES (?, ?, now())",
				follower, followee);
	}

	/**
	 * 커서 순서를 시험할 때 쓴다 — S15P21E201-1179. {@code now()} 는 같은 트랜잭션
	 * 안에서 매번 같은 값을 줄 수 있어(Postgres 는 트랜잭션 시작 시각을 고정한다), 맺은 시각이
	 * 갈리는 것을 보이려면 값을 직접 정해 넣어야 한다.
	 */
	public static void insertFollow(JdbcTemplate jdbc, UUID follower, UUID followee, Instant createdAt) {
		jdbc.update("INSERT INTO user_follow (follower_user_id, followee_user_id, created_at) VALUES (?, ?, ?)",
				follower, followee, createdAt.atOffset(ZoneOffset.UTC));
	}

	/** S15P21E201-1179. */
	public static void insertBlock(JdbcTemplate jdbc, UUID blocker, UUID blocked) {
		jdbc.update("INSERT INTO user_block (blocker_user_id, blocked_user_id, created_at) VALUES (?, ?, now())",
				blocker, blocked);
	}

	/** {@link #insertFollow(JdbcTemplate, UUID, UUID, Instant)} 와 같은 이유. */
	public static void insertBlock(JdbcTemplate jdbc, UUID blocker, UUID blocked, Instant createdAt) {
		jdbc.update("INSERT INTO user_block (blocker_user_id, blocked_user_id, created_at) VALUES (?, ?, ?)",
				blocker, blocked, createdAt.atOffset(ZoneOffset.UTC));
	}

	public static Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
