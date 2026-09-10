package com.gabolle.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
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

import com.gabolle.backend.moderation.presentation.ModerationExceptionHandler;
import com.gabolle.backend.moderation.presentation.StoryReportController;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryCoauthorController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 감춰진 기록에 딸린 것이 함께 감춰지는가 — S15P21E201-137.
 *
 * <p>신고를 받으면 기록이 즉시 사라진다는 것은 {@link StoryReportFilingIntegrationTest} 가
 * 이미 네 경로에서 확인한다. 이 파일이 보는 것은 <b>그 뒤에 남아 있던 두 구멍</b>이다.
 *
 * <ol>
 *   <li>감춰진 기록의 <b>참여자 목록</b>이 아무 로그인 사용자에게나 표시 이름째로 나갔다.
 *       "사라졌다" 는 말은 그 글에 딸린 것도 함께 사라졌다는 뜻이어야 한다
 *   <li>감춰진 기록을 <b>작성자가 계속 고칠 수 있었다.</b> 검토는 그 시점의 내용을 두고
 *       판단하는 일이라, 그 사이 내용이 바뀌면 판단의 대상이 사라진다
 * </ol>
 *
 * <p>🔴 함께 지키는 것이 하나 더 있다. <b>지우는 것은 여전히 되어야 한다.</b> 신고당한 글을
 * 스스로 내리는 길까지 막으면 사용자가 할 수 있는 일이 없어진다. 구멍을 막다가 이쪽을
 * 함께 막아 버리는 것이 이 수정에서 가장 하기 쉬운 실수라 검사로 못 박는다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@ExtendWith(PostgresAvailableCondition.class)
class HiddenStoryLeakIntegrationTest {

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
	private StoryCoauthorController storyCoauthorController;

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

	private UUID reporter;

	private UUID stranger;

	private UUID storyId;

	/**
	 * 🔴 공개 시각을 <b>한 시간 전</b>으로 둔다. "지금" 으로 두면 서버가 볼 때 아직 공개 전으로
	 * 읽히는 순간이 생겨, 신고 접수가 404 를 돌려주고 검사가 이따금 빨개진다(전체 스위트에서만
	 * 재현됐다). 이 검사가 보려는 것은 감춤 규칙이지 공개 시각 경계가 아니라, 그 경계에서
	 * 비켜세운다.
	 */
	private final Instant now = Instant.now().minusSeconds(3600);

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.storyController, this.storyCoauthorController, this.storyReportController)
				.setControllerAdvice(this.storyExceptionHandler, this.moderationExceptionHandler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.reporter = StoryFixture.insertUser(this.jdbc, "신고자");
		this.stranger = StoryFixture.insertUser(this.jdbc, "아무 관계 없는 사람");
		this.storyId = StoryFixture.insertStory(this.jdbc, this.author, "감춰질 기록", "PUBLIC", this.now);
	}

	@Test
	@DisplayName("감춰지기 전에는 참여자 목록이 남에게도 보인다 — 이 검사가 재는 것이 원래 열려 있던 문임을 먼저 확인한다")
	void theCoauthorListIsOpenBeforeTheStoryIsHidden() throws Exception {
		this.mockMvc.perform(get("/api/v1/stories/{id}/coauthors", this.storyId)
				.principal(StoryFixture.as(this.stranger)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("신고로 감춰지면 참여자 목록도 남에게 안 보인다")
	void theCoauthorListDisappearsWithTheStory() throws Exception {
		report();

		this.mockMvc.perform(get("/api/v1/stories/{id}/coauthors", this.storyId)
				.principal(StoryFixture.as(this.stranger)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("감춰져도 참여자 본인은 참여자 목록을 본다 — 자기가 쓴 글의 상태는 감출 것이 아니다")
	void participantsStillSeeTheirOwnCoauthorList() throws Exception {
		report();

		this.mockMvc.perform(get("/api/v1/stories/{id}/coauthors", this.storyId)
				.principal(StoryFixture.as(this.author)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("검토 중인 기록은 작성자도 고칠 수 없다 — 409 이고 왜 안 되는지가 응답에 있다")
	void theAuthorCannotEditAStoryUnderReview() throws Exception {
		report();

		this.mockMvc
				.perform(patch("/api/v1/stories/{id}", this.storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"검토 중에 바꿔치기\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STORY_UNDER_MODERATION"));

		// 표에 실제로 안 들어갔는지 본다. 응답만 보면 저장된 뒤 오류가 난 경우를 못 가른다.
		String body = this.jdbc.queryForObject("SELECT body FROM story WHERE story_id = ?", String.class,
				this.storyId);
		assertThat(body).isEqualTo("감춰질 기록");
	}

	@Test
	@DisplayName("🔴 검토 중이어도 작성자는 자기 기록을 지울 수 있다 — 구멍을 막다가 이 길까지 막으면 안 된다")
	void theAuthorCanStillDeleteAStoryUnderReview() throws Exception {
		report();

		this.mockMvc.perform(delete("/api/v1/stories/{id}", this.storyId).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNoContent());

		Integer remaining = this.jdbc.queryForObject(
				"SELECT COUNT(*) FROM story WHERE story_id = ? AND deleted_at IS NULL", Integer.class, this.storyId);
		assertThat(remaining).isZero();
	}

	@Test
	@DisplayName("검토로 감춰진 것이 아니면 수정은 그대로 된다 — 이 수정이 멀쩡한 길을 막지 않았다")
	void editingAVisibleStoryStillWorks() throws Exception {
		this.mockMvc
				.perform(patch("/api/v1/stories/{id}", this.storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"멀쩡할 때 고친 글\"}"))
				.andExpect(status().isOk());

		String body = this.jdbc.queryForObject("SELECT body FROM story WHERE story_id = ?", String.class,
				this.storyId);
		assertThat(body).isEqualTo("멀쩡할 때 고친 글");
	}

	@Test
	@DisplayName("기각으로 되살아나면 다시 고칠 수 있다 — 막는 것은 검토 중인 동안뿐이다")
	void editingWorksAgainAfterTheReportIsDismissed() throws Exception {
		report();
		// 운영자 경로를 거치지 않고 상태만 되돌린다. 기각 경로 자체는
		// AdminModerationQueueIntegrationTest 가 본다 — 여기서 보는 것은 수정이 다시 열리는가다.
		this.jdbc.update("UPDATE story SET moderation_state = 'VISIBLE' WHERE story_id = ?", this.storyId);

		this.mockMvc
				.perform(patch("/api/v1/stories/{id}", this.storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"기각된 뒤 고친 글\"}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("운영자가 삭제로 처리한 기록도 고칠 수 없다 — 검토 중과 같다")
	void removedStoriesAreAlsoLockedForEditing() throws Exception {
		this.jdbc.update("UPDATE story SET moderation_state = 'REMOVED' WHERE story_id = ?", this.storyId);

		this.mockMvc
				.perform(patch("/api/v1/stories/{id}", this.storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"삭제된 뒤 바꿔치기\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("STORY_UNDER_MODERATION"));
	}

	/** 신고 한 건을 넣어 기록을 검토 상태로 만든다. */
	private void report() throws Exception {
		this.mockMvc
				.perform(post("/api/v1/stories/{id}/reports", this.storyId).principal(StoryFixture.as(this.reporter))
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\",\"detail\":null}"))
				.andExpect(status().isNoContent());
		assertThat(ModerationFixture.moderationState(this.jdbc, this.storyId)).isEqualTo("UNDER_REVIEW");
	}
}
