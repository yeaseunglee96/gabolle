package com.gabolle.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.UserSocialController;
import com.gabolle.backend.moderation.presentation.ModerationExceptionHandler;
import com.gabolle.backend.moderation.presentation.StoryReportController;
import com.gabolle.testslice.StorySliceApplication;

/** 신고 접수, 즉시 비노출, 중복·자기 신고 처리. */
@SpringBootTest(classes = StorySliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@ExtendWith(PostgresAvailableCondition.class)
class StoryReportFilingIntegrationTest {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", () -> storageRoot.toString());
		registry.add("gabolle.storage.public-base-path", () -> StoryFixture.IMAGE_BASE);
	}

	@Autowired
	private StoryController storyController;

	@Autowired
	private UserSocialController userSocialController;

	@Autowired
	private StoryReportController storyReportController;

	@Autowired
	private StoryExceptionHandler storyExceptionHandler;

	@Autowired
	private ModerationExceptionHandler moderationExceptionHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID author;

	private UUID follower; // 팔로잉 피드를 이 사람으로 본다

	private UUID reporter1;

	private UUID reporter2;

	private UUID stranger; // 전체 피드·프로필 피드·상세를 이 사람으로 본다

	private final Instant now = Instant.now();

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.storyController, this.userSocialController, this.storyReportController)
				.setControllerAdvice(this.storyExceptionHandler, this.moderationExceptionHandler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.follower = StoryFixture.insertUser(this.jdbc, "팔로워");
		this.reporter1 = StoryFixture.insertUser(this.jdbc, "신고자1");
		this.reporter2 = StoryFixture.insertUser(this.jdbc, "신고자2");
		this.stranger = StoryFixture.insertUser(this.jdbc, "모르는 사람");
		StoryFixture.insertFollow(this.jdbc, this.follower, this.author);
	}

	private void report(UUID storyId, UUID reporter, String reason) throws Exception {
		String body = detail(reason, null);
		this.mockMvc
				.perform(post("/api/v1/stories/{id}/reports", storyId).principal(StoryFixture.as(reporter))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isNoContent());
	}

	private static String detail(String reason, String detail) {
		String detailJson = detail == null ? "null" : "\"" + detail + "\"";
		return "{\"reason\":\"" + reason + "\",\"detail\":" + detailJson + "}";
	}

	private boolean visibleInFullFeed(UUID storyId, UUID viewer) throws Exception {
		return FeedProbe.containsStory(this.mockMvc, "/api/v1/stories", new Object[0], StoryFixture.as(viewer), "ALL",
				storyId);
	}

	private boolean visibleInFollowingFeed(UUID storyId, UUID viewer) throws Exception {
		return FeedProbe.containsStory(this.mockMvc, "/api/v1/stories", new Object[0], StoryFixture.as(viewer),
				"FOLLOWING", storyId);
	}

	private boolean visibleInProfileFeed(UUID storyId, UUID authorId, UUID viewer) throws Exception {
		return FeedProbe.containsStory(this.mockMvc, "/api/v1/users/{id}/stories", new Object[] { authorId },
				StoryFixture.as(viewer), null, storyId);
	}

	private boolean visibleInDetail(UUID storyId, UUID viewer) throws Exception {
		var result = this.mockMvc.perform(get("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(viewer)))
				.andReturn();
		return result.getResponse().getStatus() == 200;
	}

	@Test
	@DisplayName("🔴 신고당한 기록은 전체 피드·팔로잉 피드·프로필 피드·상세 넷 다에서 사라진다 (신고 전엔 넷 다 보였다)")
	void reportedStoryDisappearsFromAllFourSurfaces() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "지워질 뻔한 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));

		// 신고 전에 보였다는 것을 먼저 확인해야 "원래 안 보였다" 와 구분된다.
		assertThat(visibleInFullFeed(storyId, this.stranger)).as("신고 전 전체 피드").isTrue();
		assertThat(visibleInFollowingFeed(storyId, this.follower)).as("신고 전 팔로잉 피드").isTrue();
		assertThat(visibleInProfileFeed(storyId, this.author, this.stranger)).as("신고 전 프로필 피드").isTrue();
		assertThat(visibleInDetail(storyId, this.stranger)).as("신고 전 상세").isTrue();

		report(storyId, this.reporter1, "PRIVACY");

		assertThat(visibleInFullFeed(storyId, this.stranger)).as("신고 뒤 전체 피드").isFalse();
		assertThat(visibleInFollowingFeed(storyId, this.follower)).as("신고 뒤 팔로잉 피드").isFalse();
		assertThat(visibleInProfileFeed(storyId, this.author, this.stranger)).as("신고 뒤 프로필 피드").isFalse();
		assertThat(visibleInDetail(storyId, this.stranger)).as("신고 뒤 상세").isFalse();
	}

	@Test
	@DisplayName("두 사람이 신고하면 신고 수가 2 다")
	void twoReportersMakeTwoReports() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "여러 명이 신고", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));

		report(storyId, this.reporter1, "PRIVACY");
		report(storyId, this.reporter2, "SPAM");

		Long count = this.jdbc.queryForObject("SELECT count(*) FROM story_report WHERE story_id = ?", Long.class,
				storyId);
		assertThat(count).isEqualTo(2L);
	}

	@Test
	@DisplayName("같은 사람이 두 번 신고해도 신고 수가 1 이고, 응답은 여전히 204 다")
	void sameReporterTwiceStaysAtOne() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "중복 신고", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));

		report(storyId, this.reporter1, "PRIVACY");
		report(storyId, this.reporter1, "SPAM"); // 같은 사람, 다른 사유를 골라도 늘지 않는다

		Long count = this.jdbc.queryForObject("SELECT count(*) FROM story_report WHERE story_id = ?", Long.class,
				storyId);
		assertThat(count).isEqualTo(1L);
	}

	@Test
	@DisplayName("자기 기록을 신고해도 접수된다 — 막지 않기로 했다")
	void selfReportIsAllowed() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "내가 신고하는 내 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));

		report(storyId, this.author, "OTHER");

		Long count = this.jdbc.queryForObject("SELECT count(*) FROM story_report WHERE story_id = ?", Long.class,
				storyId);
		assertThat(count).isEqualTo(1L);
		assertThat(ModerationFixture.moderationState(this.jdbc, storyId)).isEqualTo("UNDER_REVIEW");
	}

	@Test
	@DisplayName("없는 기록을 신고하면 404")
	void reportingMissingStoryIsNotFound() throws Exception {
		this.mockMvc
				.perform(post("/api/v1/stories/{id}/reports", UUID.randomUUID()).principal(StoryFixture.as(this.reporter1))
						.contentType(MediaType.APPLICATION_JSON).content(detail("PRIVACY", null)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("STORY_NOT_FOUND"));
	}

	@Test
	@DisplayName("볼 수 없는(비공개) 기록을 신고해도 404 — 존재를 감춘다")
	void reportingInvisibleStoryIsNotFound() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "비공개 기록", "PRIVATE",
				this.now.minus(Duration.ofHours(1)));

		this.mockMvc
				.perform(post("/api/v1/stories/{id}/reports", storyId).principal(StoryFixture.as(this.stranger))
						.contentType(MediaType.APPLICATION_JSON).content(detail("PRIVACY", null)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("STORY_NOT_FOUND"));
	}

	@Test
	@DisplayName("모르는 사유는 400")
	void unknownReasonIsBadRequest() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "사유 오류", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));

		this.mockMvc
				.perform(post("/api/v1/stories/{id}/reports", storyId).principal(StoryFixture.as(this.reporter1))
						.contentType(MediaType.APPLICATION_JSON).content(detail("NOT_A_REASON", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_REPORT_REASON_INVALID"));
	}
}
