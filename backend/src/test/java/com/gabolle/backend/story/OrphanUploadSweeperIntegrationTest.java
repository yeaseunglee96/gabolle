package com.gabolle.backend.story;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.OrphanUploadSweeper;
import com.gabolle.backend.story.storage.StorageProperties;
import com.gabolle.testslice.StorySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15P21E201-1284 — 어디에도 안 붙은 업로드를 치운다. <b>그리고 붙어 있는 것은 안 건드린다.</b>
 *
 * <h2>🔴 이 파일이 진짜 막는 것은 「썸네일을 전부 지우는 것」이다</h2>
 *
 * 고아를 찾는 자연스러운 질의는 {@code story_image} 만 본다. 그런데 <b>동영상 썸네일은 거기
 * 안 들어간다</b> — 「한 기록에 사진 3장」 한 칸을 안 먹게 하려고 일부러 그렇게 뒀다
 * (S15P21E201-1275 · -1279). 그래서 {@code story_image} 만 보면 <b>붙어 있는 동영상의 썸네일이
 * 전부 고아로 보인다.</b>
 *
 * <p>그 실수는 <b>아무 오류도 안 낸다.</b> 글은 멀쩡히 살아 있고 화면에 <b>검은 칸</b>만 남는다.
 * 몇 주 뒤 사람이 눈으로 볼 때까지 아무도 모른다. 그래서 <b>「썸네일이 살아남는다」가 이
 * 파일에서 제일 중요한 시험</b>이다.
 *
 * <h2>행이 아니라 파일을 본다</h2>
 *
 * 저장 루트를 임시 폴더로 돌리고 <b>진짜 파일을 만들어 둔 뒤</b> 있는지 없는지를 본다.
 * 행만 보면 <b>파일이 저장소에 그대로 남아 있어도 초록</b>이다.
 *
 * <h2>DB 가 없으면 건너뛴다</h2>
 *
 * 🔴 도커가 꺼진 PC 에서는 건너뛴 채 초록이다. <b>진짜 판정은 CI 다.</b>
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class OrphanUploadSweeperIntegrationTest {

	private static final Path STORAGE_ROOT;

	static {
		try {
			STORAGE_ROOT = Files.createTempDirectory("gabolle-orphan-test");
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", STORAGE_ROOT::toString);
	}

	@Autowired
	private OrphanUploadSweeper sweeper;

	@Autowired
	private StorageProperties storageProperties;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID owner;

	private UUID storyId;

	private boolean originalEnabled;

	@BeforeEach
	void setUp() {
		this.originalEnabled = this.storageProperties.getOrphanCleanup().isEnabled();
		this.storageProperties.getOrphanCleanup().setEnabled(true);
		this.owner = StoryFixture.insertUser(this.jdbc, "올린 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.owner, "붙은 것이 있는 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
	}

	@AfterEach
	void tearDown() {
		this.storageProperties.getOrphanCleanup().setEnabled(this.originalEnabled);
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다.
		this.jdbc.update("DELETE FROM story_video WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story_image WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM uploaded_video WHERE uploader_user_id = ?", this.owner);
		this.jdbc.update("DELETE FROM uploaded_image WHERE uploader_user_id = ?", this.owner);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", this.owner);
	}

	@Test
	@DisplayName("🔴 붙어 있는 동영상의 썸네일은 살아남는다 — story_image 만 보면 여기가 깨진다")
	void thumbnailOfAttachedVideoSurvives() {
		String videoKey = insertOldVideo();
		String thumbnailKey = insertOldImage();
		attachVideo(videoKey, thumbnailKey);

		// 🔴 먼저 「넣었는가」.
		assertThat(fileOf(thumbnailKey)).exists();

		this.sweeper.sweep();

		assertThat(fileOf(thumbnailKey)).as("썸네일은 story_image 에 없다 — 그것만 보면 고아로 보인다").exists();
		assertThat(fileOf(videoKey)).exists();
		assertThat(deletedAtOfImage(thumbnailKey)).isNull();
	}

	@Test
	@DisplayName("🔴 계정 커버 사진은 살아남는다 — 기록 표 어디에도 없어서 고아로 보인다 (S15P21E201-1297)")
	void coverPhotoOfAccountSurvives() {
		String key = insertOldImage();
		attachToAccount("cover_url", key);

		assertThat(fileOf(key)).exists();

		this.sweeper.sweep();

		assertThat(fileOf(key)).as("커버 사진은 story_image 에도 story_video 에도 없다").exists();
		assertThat(deletedAtOfImage(key)).isNull();
	}

	@Test
	@DisplayName("🔴 계정 프로필 사진도 살아남는다 — 이 구멍은 커버보다 먼저 있었다 (S15P21E201-844)")
	void avatarPhotoOfAccountSurvives() {
		String key = insertOldImage();
		attachToAccount("avatar_url", key);

		assertThat(fileOf(key)).exists();

		this.sweeper.sweep();

		assertThat(fileOf(key)).as("청소기를 켜는 날 프로필 사진이 전부 사라졌을 자리다").exists();
		assertThat(deletedAtOfImage(key)).isNull();
	}

	@Test
	@DisplayName("기록에 붙은 사진은 살아남는다")
	void attachedPhotoSurvives() {
		String key = insertOldImage();
		attachPhoto(key);

		assertThat(fileOf(key)).exists();

		this.sweeper.sweep();

		assertThat(fileOf(key)).exists();
	}

	@Test
	@DisplayName("어디에도 안 붙은 오래된 사진은 파일까지 사라진다")
	void orphanImageIsRemoved() {
		String key = insertOldImage();

		assertThat(fileOf(key)).exists();

		OrphanUploadSweeper.SweepResult result = this.sweeper.sweep();

		assertThat(result.images()).isEqualTo(1);
		assertThat(result.deleted()).isTrue();
		assertThat(fileOf(key)).doesNotExist();
		assertThat(deletedAtOfImage(key)).isNotNull();
	}

	@Test
	@DisplayName("어디에도 안 붙은 오래된 동영상은 파일까지 사라진다 — 이 청소가 지키는 것은 디스크다")
	void orphanVideoIsRemoved() {
		String key = insertOldVideo();

		assertThat(fileOf(key)).exists();

		OrphanUploadSweeper.SweepResult result = this.sweeper.sweep();

		assertThat(result.videos()).isEqualTo(1);
		assertThat(fileOf(key)).doesNotExist();
	}

	@Test
	@DisplayName("🔴 방금 올린 것은 안 건드린다 — 사람이 아직 글을 쓰는 중일 수 있다")
	void freshUploadIsLeftAlone() {
		String key = insertImageAt(Instant.now().minus(1, ChronoUnit.HOURS));

		this.sweeper.sweep();

		assertThat(fileOf(key)).as("올린 지 한 시간이면 아직 글을 쓰는 중이다").exists();
	}

	@Test
	@DisplayName("🔴 세기만 하는 모드에서는 세기만 한다 — 파일이 그대로 있다")
	void countOnlyModeDeletesNothing() {
		this.storageProperties.getOrphanCleanup().setEnabled(false);
		String key = insertOldImage();

		OrphanUploadSweeper.SweepResult result = this.sweeper.sweep();

		assertThat(result.images()).as("세기는 한다").isEqualTo(1);
		assertThat(result.deleted()).as("지우지는 않았다는 표시").isFalse();
		assertThat(fileOf(key)).as("파일이 그대로 있어야 한다").exists();
		assertThat(deletedAtOfImage(key)).isNull();
	}

	@Test
	@DisplayName("이미 지운 것은 다시 안 센다 — 뒷정리 대기열을 더럽히지 않는다")
	void alreadyDeletedIsSkipped() {
		String key = insertOldImage();
		this.jdbc.update("UPDATE uploaded_image SET deleted_at = now() WHERE storage_key = ?", key);

		OrphanUploadSweeper.SweepResult result = this.sweeper.sweep();

		assertThat(result.images()).isZero();
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private String insertOldImage() {
		return insertImageAt(Instant.now().minus(3, ChronoUnit.DAYS));
	}

	private String insertImageAt(Instant createdAt) {
		String key = "story/2026/09/" + UUID.randomUUID() + ".jpg";
		this.jdbc.update("""
				INSERT INTO uploaded_image
				    (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at)
				VALUES (?, ?, ?, ?, 'image/jpeg', 1234, ?)
				""", UUID.randomUUID(), this.owner, key, StoryFixture.IMAGE_BASE + "/" + key,
				createdAt.atOffset(java.time.ZoneOffset.UTC));
		writeRealFile(key);
		return key;
	}

	private String insertOldVideo() {
		String key = "story-video/2026/09/" + UUID.randomUUID() + ".mp4";
		this.jdbc.update("""
				INSERT INTO uploaded_video
				    (uploaded_video_id, uploader_user_id, storage_key, video_url, content_type,
				     byte_size, duration_sec, created_at)
				VALUES (?, ?, ?, ?, 'video/mp4', 4321, 12, ?)
				""", UUID.randomUUID(), this.owner, key, "/photos/" + key,
				Instant.now().minus(3, ChronoUnit.DAYS).atOffset(java.time.ZoneOffset.UTC));
		writeRealFile(key);
		return key;
	}

	private void attachPhoto(String storageKey) {
		this.jdbc.update("""
				INSERT INTO story_image (story_image_id, story_id, uploaded_image_id, position, created_at)
				SELECT ?, ?, uploaded_image_id, 1, now() FROM uploaded_image WHERE storage_key = ?
				""", UUID.randomUUID(), this.storyId, storageKey);
	}

	/** 계정에 사진을 건다. 계정은 업로드 식별자가 아니라 <b>주소</b>를 들고 있다. */
	private void attachToAccount(String column, String storageKey) {
		this.jdbc.update("""
				UPDATE app_user SET %s =
				    (SELECT image_url FROM uploaded_image WHERE storage_key = ?)
				 WHERE user_id = ?
				""".formatted(column), storageKey, this.owner);
	}

	private void attachVideo(String videoKey, String thumbnailKey) {
		UUID videoId = this.jdbc.queryForObject(
				"SELECT uploaded_video_id FROM uploaded_video WHERE storage_key = ?", UUID.class, videoKey);
		UUID thumbnailId = this.jdbc.queryForObject(
				"SELECT uploaded_image_id FROM uploaded_image WHERE storage_key = ?", UUID.class, thumbnailKey);
		this.jdbc.update("""
				INSERT INTO story_video
				    (story_video_id, story_id, uploaded_video_id, thumbnail_upload_id, created_at)
				VALUES (?, ?, ?, ?, now())
				""", UUID.randomUUID(), this.storyId, videoId, thumbnailId);
	}

	private void writeRealFile(String key) {
		try {
			Path target = STORAGE_ROOT.resolve(key);
			Files.createDirectories(target.getParent());
			Files.write(target, "seed".getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ── 확인 ────────────────────────────────────────────────────────────────

	private java.io.File fileOf(String key) {
		return STORAGE_ROOT.resolve(key).toFile();
	}

	private Instant deletedAtOfImage(String key) {
		return this.jdbc.queryForObject("SELECT deleted_at FROM uploaded_image WHERE storage_key = ?", Instant.class,
				key);
	}
}
