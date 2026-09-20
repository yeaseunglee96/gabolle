package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.UserSocialController;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 차단 방향이 흔한 것과 반대다. blocker 가 blocked 를 차단하면 blocked 가 blocker 를 못 본다.
 * 구현을 반대 방향으로 고치면 이 파일이 빨개진다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class BlockIntegrationTest {

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
	private StoryExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID blocker;

	private UUID blocked;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.handler).build();
		this.blocker = StoryFixture.insertUser(this.jdbc, "차단한사람");
		this.blocked = StoryFixture.insertUser(this.jdbc, "차단당한사람");
	}

	private int blockRows() {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM user_block WHERE blocker_user_id = ? AND blocked_user_id = ?", Integer.class,
				this.blocker, this.blocked);
	}

	private int followRows(UUID follower, UUID followee) {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM user_follow WHERE follower_user_id = ? AND followee_user_id = ?", Integer.class,
				follower, followee);
	}

	private void block() throws Exception {
		this.mockMvc.perform(put("/api/v1/users/{id}/block", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.blocked").value(true));
	}

	/** 시험 DB 를 공유하므로 limit 을 크게 준다. 기본값이면 다른 시험의 최신 기록에 밀려 차단과 무관하게 실패한다. */
	private org.springframework.test.web.servlet.ResultActions feedOf(UUID who) throws Exception {
		return this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(who)).param("limit", "50"));
	}

	/** 목록 맨 앞에 오도록 방금 시각으로 넣는다. */
	private UUID freshStory(UUID author, String body) {
		return StoryFixture.insertStory(this.jdbc, author, body, "PUBLIC", Instant.now().minus(Duration.ofSeconds(1)));
	}

	@Test
	@DisplayName("🔴 차단하면 차단당한 쪽의 전체 피드에서 내 글이 사라진다")
	void blockedUserLosesAuthorStoriesFromFeed() throws Exception {
		UUID story = freshStory(this.blocker, "차단한 사람의 글");

		// 차단 전에는 보인다 — 이 줄이 없으면 원래부터 안 보였을 가능성을 못 지운다.
		feedOf(this.blocked).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.id=='" + story + "')]").exists());

		block();

		feedOf(this.blocked).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.id=='" + story + "')]").doesNotExist());
	}

	@Test
	@DisplayName("🔴 차단한 쪽의 피드에서는 상대 글이 그대로 보인다 — 반대 방향은 막지 않는다")
	void blockerStillSeesBlockedUsersStories() throws Exception {
		UUID story = freshStory(this.blocked, "차단당한 사람의 글");

		block();

		feedOf(this.blocker).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.id=='" + story + "')]").exists());
	}

	@Test
	@DisplayName("🔴 차단당한 쪽이 프로필·기록을 열면 403 BLOCKED_BY_USER — 404 가 아니다")
	void blockedUserGetsForbiddenOnProfileAndStories() throws Exception {
		block();

		this.mockMvc.perform(get("/api/v1/users/{id}/stories", this.blocker).principal(StoryFixture.as(this.blocked)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("BLOCKED_BY_USER"));

		// 프로필은 200 이되 속이 비어 있고 blockedByUser 가 참이다 — 화면이 문구를 띄울 수 있어야 한다.
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.blocker).principal(StoryFixture.as(this.blocked)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.blockedByUser").value(true))
				.andExpect(jsonPath("$.data.followerCount").value(0))
				.andExpect(jsonPath("$.data.storyCount").value(0));
	}

	@Test
	@DisplayName("🔴 차단한 쪽이 상대 프로필을 보면 blocked=true, blockedByUser=false 다")
	void blockerSeesBlockedFlagOnProfile() throws Exception {
		block();

		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.blocked").value(true))
				.andExpect(jsonPath("$.data.blockedByUser").value(false));
	}

	@Test
	@DisplayName("🔴 차단하면 양쪽 팔로우가 끊기고, 차단을 풀어도 팔로우는 돌아오지 않는다")
	void blockDropsBothFollowsAndUnblockDoesNotRestoreThem() throws Exception {
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk());
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.blocker).principal(StoryFixture.as(this.blocked)))
				.andExpect(status().isOk());
		assertThat(followRows(this.blocker, this.blocked)).isEqualTo(1);
		assertThat(followRows(this.blocked, this.blocker)).isEqualTo(1);

		block();

		assertThat(followRows(this.blocker, this.blocked)).isZero();
		assertThat(followRows(this.blocked, this.blocker)).isZero();

		this.mockMvc.perform(delete("/api/v1/users/{id}/block", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.blocked").value(false));

		assertThat(followRows(this.blocker, this.blocked)).isZero();
		assertThat(followRows(this.blocked, this.blocker)).isZero();
	}

	@Test
	@DisplayName("🔴 차단을 풀면 차단당한 쪽 피드에 글이 다시 보인다")
	void unblockRestoresVisibility() throws Exception {
		UUID story = freshStory(this.blocker, "차단한 사람의 글");
		block();

		this.mockMvc.perform(delete("/api/v1/users/{id}/block", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk());

		feedOf(this.blocked).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.id=='" + story + "')]").exists());
	}

	@Test
	@DisplayName("🔴 차단을 두 번 눌러도 한 줄이고 두 번 다 200 blocked=true 다")
	void blockIsIdempotent() throws Exception {
		block();
		block();
		assertThat(blockRows()).isEqualTo(1);

		// 차단하지 않은 상태에서 해제해도 오류가 아니다 — 결과는 같다.
		this.mockMvc.perform(delete("/api/v1/users/{id}/block", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk());
		this.mockMvc.perform(delete("/api/v1/users/{id}/block", this.blocked).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.blocked").value(false));
		assertThat(blockRows()).isZero();
	}

	@Test
	@DisplayName("🔴 자기 자신 차단은 400 BLOCK_SELF, 없는 사용자는 404 USER_NOT_FOUND")
	void selfAndUnknownTargets() throws Exception {
		this.mockMvc.perform(put("/api/v1/users/{id}/block", this.blocker).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("BLOCK_SELF"));
		this.mockMvc.perform(put("/api/v1/users/{id}/block", UUID.randomUUID()).principal(StoryFixture.as(this.blocker)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM user_block WHERE blocker_user_id = ?", Integer.class,
				this.blocker)).isZero();
	}
}
