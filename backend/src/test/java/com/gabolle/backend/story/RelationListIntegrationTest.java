package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** 팔로워·팔로잉·차단 목록. 커서 동작 자체는 다른 테스트가 보므로 여기서는 세 목록에만 있는 규칙을 본다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class RelationListIntegrationTest {

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

	private UUID a;

	private UUID b;

	private UUID c;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.handler).build();
		this.me = StoryFixture.insertUser(this.jdbc, "나");
		this.a = StoryFixture.insertUser(this.jdbc, "A");
		this.b = StoryFixture.insertUser(this.jdbc, "B");
		this.c = StoryFixture.insertUser(this.jdbc, "C");
	}

	@Test
	@DisplayName("🔴 팔로워 목록은 최근에 맺은 순으로 오고, limit 을 넘으면 nextCursor 로 이어 읽는다")
	void followersOrderedByMostRecentWithCursor() throws Exception {
		Instant t0 = Instant.now().minus(3, ChronoUnit.HOURS);
		StoryFixture.insertFollow(this.jdbc, this.a, this.me, t0);
		StoryFixture.insertFollow(this.jdbc, this.b, this.me, t0.plusSeconds(60));
		StoryFixture.insertFollow(this.jdbc, this.c, this.me, t0.plusSeconds(120));

		String firstPage = this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.me).param("limit", "2")
						.principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].displayName").value("C"))
				.andExpect(jsonPath("$.data.items[1].displayName").value("B"))
				.andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String cursor = com.jayway.jsonpath.JsonPath.read(firstPage, "$.data.nextCursor");

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.me).param("limit", "2").param("cursor", cursor)
						.principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].displayName").value("A"))
				.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	@DisplayName("팔로잉 목록도 같은 모양이다 — 내가 팔로우한 사람들, 최근 순")
	void followingOrderedByMostRecent() throws Exception {
		Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS);
		StoryFixture.insertFollow(this.jdbc, this.me, this.a, t0);
		StoryFixture.insertFollow(this.jdbc, this.me, this.b, t0.plusSeconds(60));

		this.mockMvc.perform(get("/api/v1/users/{id}/following", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].displayName").value("B"))
				.andExpect(jsonPath("$.data.items[1].displayName").value("A"));
	}

	@Test
	@DisplayName("🔴 계약 — 팔로워 목록의 following은 목록 주인이 아니라 보는 사람 기준이다")
	void followingFlagReflectsViewerNotListOwner() throws Exception {
		StoryFixture.insertFollow(this.jdbc, this.a, this.me);
		StoryFixture.insertFollow(this.jdbc, this.b, this.me);
		StoryFixture.insertFollow(this.jdbc, this.c, this.me);
		// 나는 팔로워 중 b만 맞팔한다.
		StoryFixture.insertFollow(this.jdbc, this.me, this.b);

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.displayName=='B')].following").value(true))
				.andExpect(jsonPath("$.data.items[?(@.displayName=='A')].following").value(false))
				.andExpect(jsonPath("$.data.items[?(@.displayName=='C')].following").value(false));
	}

	@Test
	@DisplayName("차단 목록의 following은 언제나 false다 — 차단하면 팔로우가 함께 끊긴다")
	void blockListFollowingIsAlwaysFalse() throws Exception {
		StoryFixture.insertFollow(this.jdbc, this.me, this.a);
		StoryFixture.insertBlock(this.jdbc, this.me, this.a);
		this.jdbc.update("DELETE FROM user_follow WHERE follower_user_id = ? AND followee_user_id = ?", this.me, this.a);

		this.mockMvc.perform(get("/api/v1/users/{id}/blocks", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].following").value(false));
	}

	@Test
	@DisplayName("🔴 티켓 완료 기준 — 그 사람이 나를 차단했으면 팔로워·팔로잉 목록도 403 이다(기록과 같은 규칙)")
	void blockedByTargetForbidsRelationLists() throws Exception {
		StoryFixture.insertBlock(this.jdbc, this.a, this.me);

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.a).principal(StoryFixture.as(this.me)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("BLOCKED_BY_USER"));
		this.mockMvc.perform(get("/api/v1/users/{id}/following", this.a).principal(StoryFixture.as(this.me)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("BLOCKED_BY_USER"));
	}

	@Test
	@DisplayName("🔴 티켓 완료 기준 — 차단 목록은 본인만 볼 수 있다, 남이 물으면 403")
	void blockListIsPrivate() throws Exception {
		Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS);
		StoryFixture.insertBlock(this.jdbc, this.me, this.a, t0);
		StoryFixture.insertBlock(this.jdbc, this.me, this.b, t0.plusSeconds(60));

		this.mockMvc.perform(get("/api/v1/users/{id}/blocks", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].displayName").value("B"))
				.andExpect(jsonPath("$.data.items[1].displayName").value("A"));

		this.mockMvc.perform(get("/api/v1/users/{id}/blocks", this.me).principal(StoryFixture.as(this.a)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("BLOCK_LIST_FORBIDDEN"));
	}

	@Test
	@DisplayName("탈퇴한 사람은 팔로워 목록에서 빠진다")
	void deletedUserIsExcluded() throws Exception {
		StoryFixture.insertFollow(this.jdbc, this.a, this.me);
		this.jdbc.update("UPDATE app_user SET deleted_at = now() WHERE user_id = ?", this.a);

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	// ── 줄마다 기록 수 ──────────────────────────────────────────────────────

	/** 세는 범위가 사람마다 다르다 — 목록의 숫자는 그 사람 프로필의 숫자와 같아야 한다. */
	@Test
	@DisplayName("🔴 티켓 완료 기준 — 줄마다 기록 수가 오고, 세는 범위는 보는 사람과의 관계를 따른다")
	void storyCountFollowsWhatTheViewerCanSee() throws Exception {
		Instant published = Instant.now().minus(1, ChronoUnit.HOURS);
		StoryFixture.insertFollow(this.jdbc, this.a, this.me);
		StoryFixture.insertFollow(this.jdbc, this.b, this.me);
		StoryFixture.insertFollow(this.jdbc, this.c, this.me);
		// 나는 팔로워 중 B 만 맞팔한다 — 그래서 B 의 「팔로워 공개」 글만 나에게 보인다.
		StoryFixture.insertFollow(this.jdbc, this.me, this.b);

		StoryFixture.insertStory(this.jdbc, this.a, "A 공개", "PUBLIC", published);
		StoryFixture.insertStory(this.jdbc, this.a, "A 팔로워공개", "FOLLOWERS", published);
		StoryFixture.insertStory(this.jdbc, this.b, "B 공개", "PUBLIC", published);
		StoryFixture.insertStory(this.jdbc, this.b, "B 팔로워공개", "FOLLOWERS", published);
		StoryFixture.insertStory(this.jdbc, this.b, "B 비공개", "PRIVATE", published);

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				// 안 맞팔한 A — 전체 공개만 보인다.
				.andExpect(jsonPath("$.data.items[?(@.displayName=='A')].storyCount").value(1))
				// 맞팔한 B — 팔로워 공개까지 보인다. 비공개는 누구에게도 안 보인다.
				.andExpect(jsonPath("$.data.items[?(@.displayName=='B')].storyCount").value(2))
				// 한 글도 안 쓴 C 는 세는 질의 결과에 안 나온다. 「모른다」가 아니라 0 이다.
				.andExpect(jsonPath("$.data.items[?(@.displayName=='C')].storyCount").value(0));
	}

	/** 남의 팔로워 목록에 내가 들어 있으면 그 줄은 내 비공개 기록까지 세야 한다. */
	@Test
	@DisplayName("🔴 목록에 나 자신이 있으면 그 줄은 내 비공개 기록까지 센다")
	void ownRowCountsPrivateStories() throws Exception {
		Instant published = Instant.now().minus(1, ChronoUnit.HOURS);
		StoryFixture.insertFollow(this.jdbc, this.me, this.a);
		StoryFixture.insertStory(this.jdbc, this.me, "내 공개", "PUBLIC", published);
		StoryFixture.insertStory(this.jdbc, this.me, "내 비공개", "PRIVATE", published);

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.a).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.displayName=='나')].storyCount").value(2));
	}

	/** {@code null} 은 0 이 아니다. 차단 목록은 이 숫자를 안 그리므로 세지 않는다. */
	@Test
	@DisplayName("🔴 차단 목록은 기록 수를 안 센다 — 0 이 아니라 null 이다")
	void blockListDoesNotCountStories() throws Exception {
		StoryFixture.insertStory(this.jdbc, this.a, "A 공개", "PUBLIC", Instant.now().minus(1, ChronoUnit.HOURS));
		StoryFixture.insertBlock(this.jdbc, this.me, this.a);

		this.mockMvc.perform(get("/api/v1/users/{id}/blocks", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].storyCount").doesNotExist());
	}

	/** 아직 공개 시각이 안 된 예약 글은 아무에게도 안 보인다 — 세는 쪽도 같은 규칙을 쓴다. */
	@Test
	@DisplayName("🔴 예약된 글은 아직 안 센다 — 프로필의 숫자와 같은 규칙이다")
	void scheduledStoriesAreNotCountedYet() throws Exception {
		StoryFixture.insertFollow(this.jdbc, this.a, this.me);
		StoryFixture.insertStory(this.jdbc, this.a, "A 공개", "PUBLIC", Instant.now().minus(1, ChronoUnit.HOURS));
		StoryFixture.insertStory(this.jdbc, this.a, "A 예약", "PUBLIC", Instant.now().plus(3, ChronoUnit.HOURS));

		this.mockMvc.perform(get("/api/v1/users/{id}/followers", this.me).principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.displayName=='A')].storyCount").value(1));
	}
}
