package com.gabolle.backend.story;

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
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.testslice.StorySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답이 {@code media} 를 실제로 싣는지 본다. 칸을 더해 놓고 값을 안 옮기는 실수는 컴파일도
 * 기존 검사도 통과한다.
 *
 * <p>썸네일이 없을 때 {@code null} 로 나가는지, 그리고 썸네일이 사진 3장 한 칸을 먹지 않는지가
 * 이 파일의 핵심이다.
 *
 * <p>DB 가 없으면 건너뛴 채 초록이다 — 진짜 판정은 CI 다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryMediaResponseIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryService storyService;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID author;

	private UUID viewer;

	private UUID storyId;

	private UUID videoUploadId;

	private UUID thumbnailUploadId;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "동영상 올린 사람");
		this.viewer = StoryFixture.insertUser(this.jdbc, "보는 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "동영상이 붙은 글", "PUBLIC",
				Instant.now().minusSeconds(3600));
	}

	@AfterEach
	void tearDown() {
		// 표를 비우지 않는다. 내가 만든 것만 지운다.
		this.jdbc.update("DELETE FROM story_video WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story_image WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM story_view WHERE story_id = ?", this.storyId);
		if (this.videoUploadId != null) {
			this.jdbc.update("DELETE FROM uploaded_video WHERE uploaded_video_id = ?", this.videoUploadId);
		}
		this.jdbc.update("DELETE FROM uploaded_image WHERE uploader_user_id = ?", this.author);
		this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.storyId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", this.author, this.viewer);
	}

	@Test
	@DisplayName("🔴 동영상이 붙은 글의 응답에 media 가 실린다 — 칸을 더해 놓고 값을 안 옮기는 실수를 막는다")
	void responseCarriesMedia() {
		String videoUrl = seedVideo(12);
		String thumbnailUrl = seedThumbnail();
		linkVideo(this.videoUploadId, this.thumbnailUploadId);

		StoryResponse response = this.storyService.get(this.storyId, this.viewer, null);

		assertThat(response.media()).hasSize(1);
		StoryResponse.Media media = response.media().get(0);
		assertThat(media.kind()).isEqualTo("VIDEO");
		assertThat(media.url()).isEqualTo(videoUrl);
		assertThat(media.thumbnailUrl()).isEqualTo(thumbnailUrl);
		assertThat(media.durationSec()).isEqualTo(12);
	}

	@Test
	@DisplayName("🔴 썸네일이 없으면 null 이다 — 빈 문자열이나 자리표시 주소를 넣지 않는다")
	void thumbnailIsNullWhenAbsent() {
		seedVideo(8);
		linkVideo(this.videoUploadId, null);

		StoryResponse response = this.storyService.get(this.storyId, this.viewer, null);

		assertThat(response.media()).hasSize(1);
		// 「없다」와 「빈 문자열」은 다르다. 빈 문자열이면 화면이 있다고 읽고 깨진 그림을 그린다.
		assertThat(response.media().get(0).thumbnailUrl()).isNull();
		assertThat(response.media().get(0).url()).isNotBlank();
	}

	@Test
	@DisplayName("길이를 못 받았으면 null 이다 — 서버가 지어내지 않는다. 동영상 파일을 열지 않기 때문이다")
	void durationIsNullWhenAppDidNotMeasure() {
		seedVideo(null);
		linkVideo(this.videoUploadId, null);

		StoryResponse response = this.storyService.get(this.storyId, this.viewer, null);

		assertThat(response.media().get(0).durationSec()).isNull();
	}

	@Test
	@DisplayName("동영상이 없으면 media 는 빈 배열이다 — null 이 아니다")
	void mediaIsEmptyNotNullWhenNoVideo() {
		StoryResponse response = this.storyService.get(this.storyId, this.viewer, null);

		assertThat(response.media()).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("🔴 썸네일이 사진 3장 중 한 칸을 안 먹는다 — 동영상을 넣어도 사진은 3장 그대로다")
	void thumbnailDoesNotConsumeAPhotoSlot() {
		for (int position = 1; position <= 3; position++) {
			attachPhoto(position);
		}
		seedVideo(5);
		seedThumbnail();
		linkVideo(this.videoUploadId, this.thumbnailUploadId);

		StoryResponse response = this.storyService.get(this.storyId, this.viewer, null);

		assertThat(response.images()).as("썸네일이 자리를 먹으면 여기가 2가 된다").hasSize(3);
		assertThat(response.media()).hasSize(1);
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private String seedVideo(Integer durationSec) {
		this.videoUploadId = UUID.randomUUID();
		String key = "story-video/2026/09/" + UUID.randomUUID() + ".mp4";
		String url = "/photos/" + key;
		this.jdbc.update("""
				INSERT INTO uploaded_video
				    (uploaded_video_id, uploader_user_id, storage_key, video_url, content_type,
				     byte_size, duration_sec, created_at)
				VALUES (?, ?, ?, ?, 'video/mp4', 4321, ?, now())
				""", this.videoUploadId, this.author, key, url, durationSec);
		return url;
	}

	private String seedThumbnail() {
		this.thumbnailUploadId = UUID.randomUUID();
		String key = "story/2026/09/" + UUID.randomUUID() + ".jpg";
		String url = StoryFixture.IMAGE_BASE + "/" + key;
		this.jdbc.update("""
				INSERT INTO uploaded_image
				    (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at)
				VALUES (?, ?, ?, ?, 'image/jpeg', 1234, now())
				""", this.thumbnailUploadId, this.author, key, url);
		return url;
	}

	private void linkVideo(UUID uploadedVideoId, UUID thumbnailId) {
		this.jdbc.update("""
				INSERT INTO story_video
				    (story_video_id, story_id, uploaded_video_id, thumbnail_upload_id, created_at)
				VALUES (?, ?, ?, ?, now())
				""", UUID.randomUUID(), this.storyId, uploadedVideoId, thumbnailId);
	}

	/** 사진 한 장을 기록에 붙인다 — 썸네일과 달리 story_image 를 지난다. */
	private void attachPhoto(int position) {
		UUID uploadId = UUID.randomUUID();
		String key = "story/2026/09/" + UUID.randomUUID() + ".jpg";
		this.jdbc.update("""
				INSERT INTO uploaded_image
				    (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at)
				VALUES (?, ?, ?, ?, 'image/jpeg', 1234, now())
				""", uploadId, this.author, key, StoryFixture.IMAGE_BASE + "/" + key);
		this.jdbc.update("""
				INSERT INTO story_image (story_image_id, story_id, uploaded_image_id, position, created_at)
				VALUES (?, ?, ?, ?, now())
				""", UUID.randomUUID(), this.storyId, uploadId, position);
	}
}
