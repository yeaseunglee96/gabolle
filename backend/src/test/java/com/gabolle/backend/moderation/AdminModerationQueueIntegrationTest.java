package com.gabolle.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gabolle.backend.moderation.presentation.AdminModerationController;
import com.gabolle.backend.moderation.presentation.ModerationExceptionHandler;
import com.gabolle.backend.moderation.presentation.StoryReportController;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.UserSocialController;
import com.gabolle.backend.story.storage.StoragePort;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 검토 목록의 정렬·묶음과 삭제·기각 처리. 컨트롤러를 직접 무는 standalone MockMvc 라 Spring
 * Security 필터를 거치지 않는다 — ADMIN 인가는
 * {@code AdminModerationAuthorizationIntegrationTest} 가 본다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@Import(AdminModerationQueueIntegrationTest.BrokenStorage.class)
@ExtendWith(PostgresAvailableCondition.class)
class AdminModerationQueueIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class BrokenStorage {

		@Bean
		@Primary
		StoragePort brokenStoragePort() {
			return new StoragePort() {

				@Override
				public String put(String key, String contentType, byte[] bytes) {
					throw new StorageException("저장소가 끊겼다(테스트)");
				}

				@Override
				public void delete(String key) {
					throw new StorageException("저장소가 끊겼다(테스트)");
				}

				@Override
				public Optional<StoredObject> get(String key) {
					return Optional.empty();
				}
			};
		}
	}

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
	private AdminModerationController adminModerationController;

	@Autowired
	private StoryExceptionHandler storyExceptionHandler;

	@Autowired
	private ModerationExceptionHandler moderationExceptionHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID admin;

	private UUID author;

	private UUID reporter1;

	private UUID reporter2;

	private final Instant now = Instant.now();

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.storyController, this.userSocialController, this.storyReportController,
						this.adminModerationController)
				.setControllerAdvice(this.storyExceptionHandler, this.moderationExceptionHandler).build();
		// 검토 큐 전체를 보는 검사다. 남은 미처리 신고가 DEFAULT_LIMIT 을 채우면 이 테스트가 넣은
		// 기록이 목록 밖으로 밀려 실패한다 — 같은 DB 로 두 번째 돌릴 때부터 드러난다.
		this.jdbc.update("DELETE FROM story_report");

		this.admin = StoryFixture.insertUser(this.jdbc, "운영자");
		ModerationFixture.promoteToAdmin(this.jdbc, this.admin);
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.reporter1 = StoryFixture.insertUser(this.jdbc, "신고자1");
		this.reporter2 = StoryFixture.insertUser(this.jdbc, "신고자2");
	}

	private JsonNode queue() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/admin/story-reports").principal(StoryFixture.as(this.admin)))
				.andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data").get("items");
	}

	private static int indexOfStory(JsonNode items, UUID storyId) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).get("storyId").asText().equals(storyId.toString())) {
				return i;
			}
		}
		return -1;
	}

	private static JsonNode itemOf(JsonNode items, UUID storyId) {
		int i = indexOfStory(items, storyId);
		assertThat(i).as("검토 목록에 있어야 한다: " + storyId).isNotNegative();
		return items.get(i);
	}

	@Test
	@DisplayName("🔴 검토 목록은 신고 접수가 오래된 순이다 — 최신순이 아니다")
	void queueIsOldestFirst() throws Exception {
		UUID oldest = StoryFixture.insertStory(this.jdbc, this.author, "가장 오래 기다린 신고", "PUBLIC",
				this.now.minus(Duration.ofHours(5)));
		UUID middle = StoryFixture.insertStory(this.jdbc, this.author, "중간", "PUBLIC",
				this.now.minus(Duration.ofHours(5)));
		UUID newest = StoryFixture.insertStory(this.jdbc, this.author, "가장 최근 신고", "PUBLIC",
				this.now.minus(Duration.ofHours(5)));
		ModerationFixture.insertReport(this.jdbc, oldest, this.reporter1, "SPAM", this.now.minus(Duration.ofHours(3)));
		ModerationFixture.insertReport(this.jdbc, middle, this.reporter1, "SPAM", this.now.minus(Duration.ofHours(2)));
		ModerationFixture.insertReport(this.jdbc, newest, this.reporter1, "SPAM", this.now.minus(Duration.ofHours(1)));

		JsonNode items = queue();

		int oldestIdx = indexOfStory(items, oldest);
		int middleIdx = indexOfStory(items, middle);
		int newestIdx = indexOfStory(items, newest);
		assertThat(oldestIdx).as("가장 오래된 신고가 목록에 있다").isNotNegative();
		assertThat(oldestIdx).as("오래된 신고가 중간 신고보다 앞선다").isLessThan(middleIdx);
		assertThat(middleIdx).as("중간 신고가 최신 신고보다 앞선다").isLessThan(newestIdx);
	}

	@Test
	@DisplayName("같은 기록의 신고 2건은 검토 목록에서 항목 하나로 묶이고, 사유를 모아 보여준다")
	void groupsReportsOfTheSameStory() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "여러 번 신고된 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(3)));
		ModerationFixture.insertReport(this.jdbc, storyId, this.reporter1, "PRIVACY",
				this.now.minus(Duration.ofHours(2)));
		ModerationFixture.insertReport(this.jdbc, storyId, this.reporter2, "SPAM", this.now.minus(Duration.ofHours(1)));

		JsonNode items = queue();
		JsonNode item = itemOf(items, storyId);

		assertThat(item.get("reportCount").asInt()).isEqualTo(2);
		List<String> reasons = new java.util.ArrayList<>();
		item.get("reasons").forEach((n) -> reasons.add(n.asText()));
		assertThat(reasons).containsExactlyInAnyOrder("PRIVACY", "SPAM");
		assertThat(item.has("reporterUserId")).isFalse();
		assertThat(item.toString()).doesNotContain(this.reporter1.toString());
		assertThat(item.toString()).doesNotContain(this.reporter2.toString());
	}

	@Test
	@DisplayName("경과 시간은 가장 오래된 신고 접수 시각부터 지금까지다")
	void elapsedSecondsFromOldestReport() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "경과 시간 확인", "PUBLIC",
				this.now.minus(Duration.ofHours(3)));
		ModerationFixture.insertReport(this.jdbc, storyId, this.reporter1, "SPAM",
				this.now.minus(Duration.ofMinutes(90)));

		JsonNode item = itemOf(queue(), storyId);

		assertThat(item.get("elapsedSeconds").asLong()).isGreaterThanOrEqualTo(Duration.ofMinutes(89).toSeconds());
	}

	@Test
	@DisplayName("🔴 삭제하면 기록이 사라지고, 딸린 사진의 저장소 정리 경로(StorageCleanupService)가 불린다")
	void removeDeletesStoryAndQueuesStorageCleanup() throws Exception {
		String key = StoryFixture.key("moderation-remove");
		StoryFixture.insertUploadedImage(this.jdbc, this.author, key);
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "삭제될 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));
		this.jdbc.update(
				"INSERT INTO story_image (story_image_id, story_id, uploaded_image_id, position, created_at) "
						+ "SELECT ?, ?, uploaded_image_id, 1, now() FROM uploaded_image WHERE storage_key = ?",
				UUID.randomUUID(), storyId, key);
		ModerationFixture.insertReport(this.jdbc, storyId, this.reporter1, "PRIVACY",
				this.now.minus(Duration.ofMinutes(30)));

		this.mockMvc
				.perform(post("/api/v1/admin/story-reports/{id}/remove", storyId).principal(StoryFixture.as(this.admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.resolvedReportCount").value(1));

		assertThat(FeedProbe.containsStory(this.mockMvc, "/api/v1/stories", new Object[0],
				StoryFixture.as(this.reporter1), "ALL", storyId)).as("삭제 뒤 전체 피드에 없다").isFalse();
		assertThat(ModerationFixture.moderationState(this.jdbc, storyId)).isEqualTo("REMOVED");
		assertThat(this.jdbc.queryForObject("SELECT reason FROM storage_cleanup_queue WHERE storage_key = ?",
				String.class, key)).isEqualTo("STORY_REMOVED_BY_MODERATOR");
	}

	@Test
	@DisplayName("🔴 기각하면 기록이 네 경로 전부에서 다시 보인다")
	void dismissRestoresVisibilityEverywhere() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "기각될 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));
		ModerationFixture.insertReport(this.jdbc, storyId, this.reporter1, "OFFENSIVE",
				this.now.minus(Duration.ofMinutes(30)));
		// markUnderReview() 는 신고 접수 경로에서만 불린다. 시드를 SQL 로 넣었으니 상태도 직접 맞춘다.
		this.jdbc.update("UPDATE story SET moderation_state = 'UNDER_REVIEW' WHERE story_id = ?", storyId);
		assertThat(FeedProbe.containsStory(this.mockMvc, "/api/v1/stories", new Object[0],
				StoryFixture.as(this.reporter1), "ALL", storyId)).as("기각 전에는 안 보인다").isFalse();

		this.mockMvc
				.perform(post("/api/v1/admin/story-reports/{id}/dismiss", storyId).principal(StoryFixture.as(this.admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.resolvedReportCount").value(1));

		assertThat(ModerationFixture.moderationState(this.jdbc, storyId)).isEqualTo("VISIBLE");
		assertThat(FeedProbe.containsStory(this.mockMvc, "/api/v1/stories", new Object[0],
				StoryFixture.as(this.reporter1), "ALL", storyId)).as("기각 뒤 전체 피드").isTrue();
		assertThat(FeedProbe.containsStory(this.mockMvc, "/api/v1/users/{id}/stories",
				new Object[] { this.author }, StoryFixture.as(this.reporter1), null, storyId))
				.as("기각 뒤 프로필 피드").isTrue();
	}

	@Test
	@DisplayName("같은 기록의 미처리 신고 둘을 한 번에 처리하면 둘 다 처리된 것으로 바뀐다")
	void resolvesAllPendingReportsOfTheStoryTogether() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "신고 두 건", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));
		UUID report1 = ModerationFixture.insertReport(this.jdbc, storyId, this.reporter1, "SPAM",
				this.now.minus(Duration.ofMinutes(40)));
		UUID report2 = ModerationFixture.insertReport(this.jdbc, storyId, this.reporter2, "OFFENSIVE",
				this.now.minus(Duration.ofMinutes(20)));

		this.mockMvc
				.perform(post("/api/v1/admin/story-reports/{id}/dismiss", storyId).principal(StoryFixture.as(this.admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.resolvedReportCount").value(2));

		assertThat(ModerationFixture.isPending(this.jdbc, report1)).isFalse();
		assertThat(ModerationFixture.isPending(this.jdbc, report2)).isFalse();
	}

	@Test
	@DisplayName("🔴 이미 처리된 기록을 다시 처리하면 409")
	void reprocessingAlreadyResolvedStoryIsConflict() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "두 번 처리 시도", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));
		ModerationFixture.insertReport(this.jdbc, storyId, this.reporter1, "SPAM",
				this.now.minus(Duration.ofMinutes(10)));

		this.mockMvc
				.perform(post("/api/v1/admin/story-reports/{id}/dismiss", storyId).principal(StoryFixture.as(this.admin)))
				.andExpect(status().isOk());

		this.mockMvc
				.perform(post("/api/v1/admin/story-reports/{id}/dismiss", storyId).principal(StoryFixture.as(this.admin)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STORY_REPORT_ALREADY_RESOLVED"));
	}

	@Test
	@DisplayName("신고가 없는 기록을 처리하려 하면 409")
	void processingUnreportedStoryIsConflict() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "신고 없음", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));

		this.mockMvc
				.perform(post("/api/v1/admin/story-reports/{id}/remove", storyId).principal(StoryFixture.as(this.admin)))
				.andExpect(status().isConflict());
	}
}
