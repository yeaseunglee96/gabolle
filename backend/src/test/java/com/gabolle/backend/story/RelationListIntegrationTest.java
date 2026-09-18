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

/**
 * 팔로워·팔로잉·차단 목록 — S15P21E201-1179.
 *
 * <p>커서·문턱 값 자체는 {@code FollowIntegrationTest}·{@code StoryRepository} 가 이미 확인했다.
 * 여기서는 이 세 목록에만 있는 것만 본다 — (1) 최근 맺은 순으로 오는가, (2) 「한 개 더 읽기」로
 * 다음 페이지가 있는가, (3) 차단 목록은 남이 못 보는가, (4) 그 사람이 나를 차단했으면 팔로워·팔로잉
 * 목록도 403 인가(기록과 같은 규칙).
 */
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

	/** S15P21E201-1179 계약 — 목록의 following은 목록 주인이 아니라 <b>보는 사람</b> 기준이다. */
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

	/** 차단은 팔로우를 양쪽 다 끊으므로(BlockService.block), 차단 목록의 following은 언제나 false다. */
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
}
