package com.gabolle.backend.story;

import java.time.Instant;
import java.util.List;
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
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.presentation.dto.StoryCreateRequest;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.testslice.StorySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기록에 동영상을 붙이는 길. 주제는 모양이 아니라 권한이다 — 주소만 알면 남이 올린 동영상을
 * 내 기록에 붙일 수 있는 자리라 그것을 막는지 본다.
 *
 * <p>같은 동영상이 기록 둘에 붙는 것은 {@code uq_story_video_upload} 가 DB 에서 막는다. 응용이
 * 먼저 보는 것은 오류 모양 때문이다 — 제약에 부딪히면 500 에 제약 이름만 남는다.
 *
 * <p>DB 가 없으면 건너뛴 채 초록이다 — 진짜 판정은 CI 다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryVideoAttachIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryService storyService;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID author;

	private UUID stranger;

	private UUID createdStoryId;

	@BeforeEach
	void setUp() {
		this.author = StoryFixture.insertUser(this.jdbc, "글쓴이");
		this.stranger = StoryFixture.insertUser(this.jdbc, "남");
	}

	@AfterEach
	void tearDown() {
		// 표를 비우지 않는다. 내가 만든 것만 지운다.
		if (this.createdStoryId != null) {
			this.jdbc.update("DELETE FROM story_video WHERE story_id = ?", this.createdStoryId);
			this.jdbc.update("DELETE FROM story_image WHERE story_id = ?", this.createdStoryId);
			this.jdbc.update("DELETE FROM story_view WHERE story_id = ?", this.createdStoryId);
			this.jdbc.update("DELETE FROM story WHERE story_id = ?", this.createdStoryId);
		}
		this.jdbc.update("DELETE FROM story_video WHERE uploaded_video_id IN "
				+ "(SELECT uploaded_video_id FROM uploaded_video WHERE uploader_user_id IN (?, ?))",
				this.author, this.stranger);
		this.jdbc.update("DELETE FROM uploaded_video WHERE uploader_user_id IN (?, ?)", this.author, this.stranger);
		this.jdbc.update("DELETE FROM uploaded_image WHERE uploader_user_id IN (?, ?)", this.author, this.stranger);
		this.jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", this.author, this.stranger);
	}

	@Test
	@DisplayName("동영상을 붙이면 응답의 media 에 실린다 — 여기부터 media 가 실제로 채워진다")
	void attachesVideo() {
		String videoUrl = insertUploadedVideo(this.author, 12);
		String thumbnailUrl = insertUploadedImage(this.author);

		StoryResponse response = create(videoUrl, thumbnailUrl, List.of());

		assertThat(response.media()).hasSize(1);
		assertThat(response.media().get(0).url()).isEqualTo(videoUrl);
		assertThat(response.media().get(0).thumbnailUrl()).isEqualTo(thumbnailUrl);
		assertThat(response.media().get(0).durationSec()).isEqualTo(12);
	}

	@Test
	@DisplayName("🔴 남이 올린 동영상은 못 붙인다 — 주소를 알아도 안 된다")
	void rejectsSomeoneElsesVideo() {
		String stolen = insertUploadedVideo(this.stranger, 9);

		assertThatThrownBy(() -> create(stolen, null, List.of()))
				.hasMessageContaining("내가 올린 동영상만");
	}

	@Test
	@DisplayName("🔴 남이 올린 사진은 썸네일로도 못 쓴다 — 동영상만 막고 썸네일을 열어 두면 구멍이다")
	void rejectsSomeoneElsesThumbnail() {
		String videoUrl = insertUploadedVideo(this.author, 9);
		String stolenThumbnail = insertUploadedImage(this.stranger);

		assertThatThrownBy(() -> create(videoUrl, stolenThumbnail, List.of()))
				.hasMessageContaining("내가 올린 사진만");
	}

	@Test
	@DisplayName("🔴 이미 다른 기록에 붙은 동영상은 못 붙인다 — 한쪽을 지우면 다른 쪽이 깨진다")
	void rejectsVideoAlreadyAttached() {
		String videoUrl = insertUploadedVideo(this.author, 9);
		create(videoUrl, null, List.of());

		assertThatThrownBy(() -> create(videoUrl, null, List.of()))
				.hasMessageContaining("이미 다른 기록에 붙은 동영상");
	}

	@Test
	@DisplayName("올라가 있지 않은 주소는 거절한다 — 지어낸 주소로 행을 만들 수 없다")
	void rejectsUnknownVideoUrl() {
		assertThatThrownBy(() -> create("/photos/story-video/2026/09/" + UUID.randomUUID() + ".mp4", null, List.of()))
				.hasMessageContaining("올라가 있지 않은 동영상 주소");
	}

	@Test
	@DisplayName("🔴 동영상 없이 썸네일만 보내면 거절한다 — 붙일 동영상이 없는 썸네일은 뜻이 없다")
	void rejectsThumbnailWithoutVideo() {
		String thumbnailUrl = insertUploadedImage(this.author);

		assertThatThrownBy(() -> create(null, thumbnailUrl, List.of()))
				.hasMessageContaining("동영상 없이 썸네일만");
	}

	@Test
	@DisplayName("썸네일 없이 동영상만 붙일 수 있다 — 앱이 썸네일을 못 만들 수 있다")
	void attachesVideoWithoutThumbnail() {
		String videoUrl = insertUploadedVideo(this.author, null);

		StoryResponse response = create(videoUrl, null, List.of());

		assertThat(response.media()).hasSize(1);
		assertThat(response.media().get(0).thumbnailUrl()).isNull();
		assertThat(response.media().get(0).durationSec()).isNull();
	}

	@Test
	@DisplayName("🔴 동영상을 붙여도 사진은 3장 그대로 붙는다 — 썸네일이 자리를 안 먹는다")
	void videoDoesNotConsumePhotoSlots() {
		String videoUrl = insertUploadedVideo(this.author, 5);
		String thumbnailUrl = insertUploadedImage(this.author);
		List<String> photos = List.of(insertUploadedImage(this.author), insertUploadedImage(this.author),
				insertUploadedImage(this.author));

		StoryResponse response = create(videoUrl, thumbnailUrl, photos);

		assertThat(response.images()).as("썸네일이 자리를 먹으면 여기가 2가 된다").hasSize(3);
		assertThat(response.media()).hasSize(1);
	}

	@Test
	@DisplayName("동영상을 안 보내면 media 는 빈 배열이다 — 대부분의 기록이 그렇다")
	void noVideoMeansEmptyMedia() {
		StoryResponse response = create(null, null, List.of());

		assertThat(response.media()).isNotNull().isEmpty();
	}

	// ── 시드 ────────────────────────────────────────────────────────────────

	private StoryResponse create(String videoUrl, String thumbnailUrl, List<String> imageUrls) {
		StoryCreateRequest request = new StoryCreateRequest("동영상 붙은 글", imageUrls, null, null, "부산광역시 해운대구",
				StoryVisibility.PUBLIC, Instant.now().minusSeconds(3600), null, videoUrl, thumbnailUrl);
		StoryResponse response = this.storyService.create(this.author, request);
		this.createdStoryId = UUID.fromString(response.id());
		return response;
	}

	private String insertUploadedVideo(UUID uploader, Integer durationSec) {
		String key = "story-video/2026/09/" + UUID.randomUUID() + ".mp4";
		String url = "/photos/" + key;
		this.jdbc.update("""
				INSERT INTO uploaded_video
				    (uploaded_video_id, uploader_user_id, storage_key, video_url, content_type,
				     byte_size, duration_sec, created_at)
				VALUES (?, ?, ?, ?, 'video/mp4', 4321, ?, now())
				""", UUID.randomUUID(), uploader, key, url, durationSec);
		return url;
	}

	private String insertUploadedImage(UUID uploader) {
		String key = "story/2026/09/" + UUID.randomUUID() + ".jpg";
		String url = StoryFixture.IMAGE_BASE + "/" + key;
		this.jdbc.update("""
				INSERT INTO uploaded_image
				    (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at)
				VALUES (?, ?, ?, ?, 'image/jpeg', 1234, now())
				""", UUID.randomUUID(), uploader, key, url);
		return url;
	}
}
