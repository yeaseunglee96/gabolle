package com.gabolle.backend.story;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
import com.gabolle.backend.story.application.StoryService;
import com.gabolle.testslice.StorySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기록을 지우면 동영상 파일과 썸네일 파일이 실제로 사라지는지 본다. 행만 보면 파일이 저장소에
 * 남아 있어도 초록이라, 저장 루트를 임시 폴더로 돌리고 진짜 파일을 만들어 둔 뒤 확인한다.
 *
 * <p>시드가 조용히 실패하면 지운 뒤에도 0건이라 초록이 되므로, 모든 시험이 먼저
 * {@code exists} 를 단언하고 그다음에 지운다.
 *
 * <p>썸네일이 핵심이다 — {@code story_image} 에 없으므로 {@code story_video} 를 따로 걸지
 * 않으면 영영 안 지워진다.
 *
 * <p>DB 가 없으면 건너뛴 채 초록이다 — 진짜 판정은 CI 다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryVideoDeletionIntegrationTest {

	/** 저장 루트를 여기로 돌린다 — 진짜 파일을 만들고 지워지는지 보기 위해서다. */
	private static final Path STORAGE_ROOT;

	static {
		try {
			STORAGE_ROOT = Files.createTempDirectory("gabolle-video-delete-test");
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
	private StoryService storyService;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID author;

	private UUID storyId;

	private UUID videoUploadId;

	private UUID thumbnailUploadId;

	private String videoKey;

	private String thumbnailKey;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "동영상 올린 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "동영상이 붙은 글", "PUBLIC",
				Instant.now().minusSeconds(3600));

		this.videoKey = "story-video/2026/09/" + UUID.randomUUID() + ".mp4";
		this.thumbnailKey = "story/2026/09/" + UUID.randomUUID() + ".jpg";
		this.videoUploadId = insertUploadedVideo(this.videoKey);
		this.thumbnailUploadId = insertUploadedImage(this.thumbnailKey);
		insertStoryVideo();

		writeRealFile(this.videoKey);
		writeRealFile(this.thumbnailKey);
	}

	@AfterEach
	void tearDown() {
		// 표를 비우지 않는다. 내가 만든 것만 지운다 — 같은 DB 를 여러 검사가 함께 쓴다.
		this.jdbc.update("DELETE FROM story_video WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM uploaded_video WHERE uploaded_video_id = ?", this.videoUploadId);
		this.jdbc.update("DELETE FROM uploaded_image WHERE uploaded_image_id = ?", this.thumbnailUploadId);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", this.author);
	}

	@Test
	@DisplayName("🔴 시드가 실제로 들어갔다 — 이걸 먼저 보지 않으면 지운 뒤 0건인 것이 무엇 때문인지 모른다")
	void seedActuallyLanded() {
		assertThat(fileOf(this.videoKey)).exists();
		assertThat(fileOf(this.thumbnailKey)).exists();
		assertThat(countVideoRows()).isEqualTo(1);
		assertThat(countStoryVideoRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 기록을 지우면 동영상 파일이 저장소에서 실제로 사라진다")
	void deletingStoryRemovesVideoFile() {
		assertThat(fileOf(this.videoKey)).exists();

		this.storyService.delete(this.storyId, this.author);

		assertThat(fileOf(this.videoKey)).doesNotExist();
	}

	@Test
	@DisplayName("🔴 썸네일도 같이 사라진다 — story_image 에 없어서 사진 정리가 못 보는 파일이다")
	void deletingStoryRemovesThumbnailFile() {
		assertThat(fileOf(this.thumbnailKey)).exists();

		this.storyService.delete(this.storyId, this.author);

		assertThat(fileOf(this.thumbnailKey)).doesNotExist();
	}

	@Test
	@DisplayName("지운 시각이 두 업로드 행에 다 찍힌다 — 「파일은 지웠는데 행은 살아 있는」 상태를 안 만든다")
	void deletingStoryMarksBothUploadsDeleted() {
		assertThat(deletedAtOfVideo()).isNull();
		assertThat(deletedAtOfThumbnail()).isNull();

		this.storyService.delete(this.storyId, this.author);

		assertThat(deletedAtOfVideo()).isNotNull();
		assertThat(deletedAtOfThumbnail()).isNotNull();
	}

	@Test
	@DisplayName("동영상이 안 붙은 기록을 지워도 깨지지 않는다 — 대부분의 기록이 그렇다")
	void deletingStoryWithoutVideoIsFine() {
		UUID plainStory = StoryFixture.insertStory(this.jdbc, this.author, "동영상 없는 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
		try {
			this.storyService.delete(plainStory, this.author);

			assertThat(this.jdbc.queryForObject(
					"SELECT count(*) FROM story WHERE story_id = ? AND deleted_at IS NOT NULL", Integer.class,
					plainStory)).isEqualTo(1);
		}
		finally {
			this.jdbc.update("DELETE FROM story WHERE story_id = ?", plainStory);
		}
	}

	@Test
	@DisplayName("🔴 썸네일 없는 동영상이 붙은 기록도 지워진다 — 앱이 썸네일을 못 만들 수 있다")
	void deletingStoryWithVideoButNoThumbnail() {
		UUID storyWithoutThumb = StoryFixture.insertStory(this.jdbc, this.author, "썸네일 없는 동영상 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
		String videoOnlyKey = "story-video/2026/09/" + UUID.randomUUID() + ".mp4";
		UUID videoOnlyUpload = insertUploadedVideo(videoOnlyKey);
		insertStoryVideoWithoutThumbnail(storyWithoutThumb, videoOnlyUpload);
		writeRealFile(videoOnlyKey);
		try {
			// 먼저 「넣었는가」.
			assertThat(fileOf(videoOnlyKey)).exists();

			this.storyService.delete(storyWithoutThumb, this.author);

			assertThat(fileOf(videoOnlyKey)).doesNotExist();
		}
		finally {
			this.jdbc.update("DELETE FROM story_video WHERE story_id = ?", storyWithoutThumb);
			this.jdbc.update("DELETE FROM uploaded_video WHERE uploaded_video_id = ?", videoOnlyUpload);
			this.jdbc.update("DELETE FROM story WHERE story_id = ?", storyWithoutThumb);
		}
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private UUID insertUploadedVideo(String key) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO uploaded_video
				    (uploaded_video_id, uploader_user_id, storage_key, video_url, content_type,
				     byte_size, duration_sec, created_at)
				VALUES (?, ?, ?, ?, 'video/mp4', 4321, 12, now())
				""", id, this.author, key, "/photos/" + key);
		return id;
	}

	private UUID insertUploadedImage(String key) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO uploaded_image
				    (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at)
				VALUES (?, ?, ?, ?, 'image/jpeg', 1234, now())
				""", id, this.author, key, StoryFixture.IMAGE_BASE + "/" + key);
		return id;
	}

	/** 썸네일 없이 동영상만 붙인다 — 앱이 썸네일을 못 만든 경우다. */
	private void insertStoryVideoWithoutThumbnail(UUID storyId, UUID uploadedVideoId) {
		this.jdbc.update("""
				INSERT INTO story_video
				    (story_video_id, story_id, uploaded_video_id, thumbnail_upload_id, created_at)
				VALUES (?, ?, ?, NULL, now())
				""", UUID.randomUUID(), storyId, uploadedVideoId);
	}

	private void insertStoryVideo() {
		this.jdbc.update("""
				INSERT INTO story_video
				    (story_video_id, story_id, uploaded_video_id, thumbnail_upload_id, created_at)
				VALUES (?, ?, ?, ?, now())
				""", UUID.randomUUID(), this.storyId, this.videoUploadId, this.thumbnailUploadId);
	}

	/** 저장소에 진짜 파일을 만든다. 내용은 아무래도 좋다 — 있는가 없는가만 본다. */
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

	private Integer countVideoRows() {
		return this.jdbc.queryForObject("SELECT count(*) FROM uploaded_video WHERE uploaded_video_id = ?",
				Integer.class, this.videoUploadId);
	}

	private Integer countStoryVideoRows() {
		return this.jdbc.queryForObject("SELECT count(*) FROM story_video WHERE story_id = ?", Integer.class,
				this.storyId);
	}

	private Instant deletedAtOfVideo() {
		return this.jdbc.queryForObject("SELECT deleted_at FROM uploaded_video WHERE uploaded_video_id = ?",
				Instant.class, this.videoUploadId);
	}

	private Instant deletedAtOfThumbnail() {
		return this.jdbc.queryForObject("SELECT deleted_at FROM uploaded_image WHERE uploaded_image_id = ?",
				Instant.class, this.thumbnailUploadId);
	}
}
