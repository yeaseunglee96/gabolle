package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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

/** 프로필의 기록 수와 목록 범위는 보는 사람과의 관계(본인·팔로워·제삼자)에 따라 달라진다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class FollowIntegrationTest {

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

	private UUID me;
	private UUID target;
	private UUID third;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.handler).build();
		this.me = StoryFixture.insertUser(this.jdbc, "나");
		this.target = StoryFixture.insertUser(this.jdbc, "상대");
		this.third = StoryFixture.insertUser(this.jdbc, "제삼자");
	}

	private int followRows() {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM user_follow WHERE follower_user_id = ? AND followee_user_id = ?", Integer.class,
				this.me, this.target);
	}

	@Test
	@DisplayName("🔴 팔로우를 두 번 눌러도 한 줄이고 두 번 다 200 following=true 다")
	void followIsIdempotent() throws Exception {
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.following").value(true))
				.andExpect(jsonPath("$.data.followerCount").value(1));
		java.sql.Timestamp firstAt = this.jdbc.queryForObject(
				"SELECT created_at FROM user_follow WHERE follower_user_id = ? AND followee_user_id = ?",
				java.sql.Timestamp.class, this.me, this.target);

		Thread.sleep(20);
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.following").value(true))
				.andExpect(jsonPath("$.data.followerCount").value(1));

		assertThat(followRows()).isEqualTo(1);
		// 두 번째 요청이 기존 줄을 덮어쓰면 팔로우한 시각이 바뀐다. 멱등은 건드리지 않는 것이다.
		java.sql.Timestamp secondAt = this.jdbc.queryForObject(
				"SELECT created_at FROM user_follow WHERE follower_user_id = ? AND followee_user_id = ?",
				java.sql.Timestamp.class, this.me, this.target);
		assertThat(secondAt).isEqualTo(firstAt);
	}

	@Test
	@DisplayName("🔴 자기 자신 팔로우는 400 FOLLOW_SELF, 없는 사용자는 404 USER_NOT_FOUND")
	void selfAndUnknownTargets() throws Exception {
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("FOLLOW_SELF"));
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", UUID.randomUUID()).principal(StoryFixture.as(this.me)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM user_follow WHERE follower_user_id = ?", Integer.class, this.me))
				.isZero();
	}

	@Test
	@DisplayName("🔴 해제하면 팔로잉 피드에서 그 사람 기록이 사라지고, 다시 해제해도 200 이다")
	void unfollowRemovesFromFollowingFeed() throws Exception {
		UUID story = StoryFixture.insertStory(this.jdbc, this.target, "상대의 글", "PUBLIC", Instant.now().minus(Duration.ofHours(1)));
		this.mockMvc.perform(put("/api/v1/users/{id}/follow", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk());
		this.mockMvc.perform(get("/api/v1/stories").param("scope", "FOLLOWING").principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(story.toString()));

		this.mockMvc.perform(delete("/api/v1/users/{id}/follow", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.following").value(false))
				.andExpect(jsonPath("$.data.followerCount").value(0));
		this.mockMvc.perform(get("/api/v1/stories").param("scope", "FOLLOWING").principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
		this.mockMvc.perform(delete("/api/v1/users/{id}/follow", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk());
		assertThat(followRows()).isZero();
	}

	@Test
	@DisplayName("프로필과 그 사람의 기록은 보는 사람과의 관계에 따라 범위가 다르다")
	void profileAndAuthorFeedRespectRelationship() throws Exception {
		Instant published = Instant.now().minus(Duration.ofHours(1));
		UUID pub = StoryFixture.insertStory(this.jdbc, this.target, "공개", "PUBLIC", published);
		UUID onlyFollowers = StoryFixture.insertStory(this.jdbc, this.target, "팔로워만", "FOLLOWERS", published.minusSeconds(60));
		StoryFixture.insertStory(this.jdbc, this.target, "비공개", "PRIVATE", published.minusSeconds(120));
		StoryFixture.insertFollow(this.jdbc, this.me, this.target);
		StoryFixture.insertFollow(this.jdbc, this.target, this.third);

		// 팔로워인 나: 공개 + 팔로워 전용 = 2
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.displayName").value("상대"))
				.andExpect(jsonPath("$.data.followerCount").value(1))
				.andExpect(jsonPath("$.data.followingCount").value(1))
				.andExpect(jsonPath("$.data.storyCount").value(2))
				.andExpect(jsonPath("$.data.following").value(true))
				.andExpect(jsonPath("$.data.me").value(false));
		this.mockMvc.perform(get("/api/v1/users/{id}/stories", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].id").value(pub.toString()))
				.andExpect(jsonPath("$.data.items[1].id").value(onlyFollowers.toString()));

		// 팔로우하지 않은 제삼자: 공개만 = 1
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.target).principal(StoryFixture.as(this.third)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.storyCount").value(1))
				.andExpect(jsonPath("$.data.following").value(false));
		this.mockMvc.perform(get("/api/v1/users/{id}/stories", this.target).principal(StoryFixture.as(this.third)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1));

		// 본인: 비공개까지 = 3, me=true
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.target).principal(StoryFixture.as(this.target)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.storyCount").value(3))
				.andExpect(jsonPath("$.data.me").value(true));
	}

	@Test
	@DisplayName("🔴 남의 프로필에도 그 사람이 고른 배경 사진이 온다 — 안 고른 사람과 나를 차단한 사람은 null")
	void profileCarriesCoverPhoto() throws Exception {
		String cover = StoryFixture.IMAGE_BASE + "/2026/09/cover.webp";
		this.jdbc.update("UPDATE app_user SET cover_url = ? WHERE user_id = ?", cover, this.target);

		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.coverUrl").value(cover));
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.third).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.coverUrl").value(nullValue()));

		// 상대가 나를 차단하면 숫자처럼 사진 주소도 비워 보낸다
		this.mockMvc.perform(put("/api/v1/users/{id}/block", this.me).principal(StoryFixture.as(this.target)))
				.andExpect(status().isOk());
		this.mockMvc.perform(get("/api/v1/users/{id}/profile", this.target).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.blockedByUser").value(true))
				.andExpect(jsonPath("$.data.coverUrl").value(nullValue()));
	}
}
