package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.UserSocialController;
import com.gabolle.testslice.StorySliceApplication;

/** 피드가 실제 PostgreSQL 에서 커서로 끊기고, 공개 범위와 공개 시각을 지키는지 본다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryFeedIntegrationTest {

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

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID me;
	private UUID followed;   // 내가 팔로우한 사람
	private UUID stranger;   // 팔로우하지 않은 사람

	private final Instant now = Instant.now();

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.handler).build();
		this.me = StoryFixture.insertUser(this.jdbc, "나");
		this.followed = StoryFixture.insertUser(this.jdbc, "팔로우한 사람");
		this.stranger = StoryFixture.insertUser(this.jdbc, "모르는 사람");
		StoryFixture.insertFollow(this.jdbc, this.me, this.followed);
	}

	private JsonNode feed(String scope, String cursor, Integer limit) throws Exception {
		var request = get("/api/v1/stories").principal(StoryFixture.as(this.me)).param("scope", scope);
		if (cursor != null) {
			request = request.param("cursor", cursor);
		}
		if (limit != null) {
			request = request.param("limit", String.valueOf(limit));
		}
		MvcResult result = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	/**
	 * 익명 출입증만 든 사람. {@code AnonymousSessionAuthenticationFilter} 가 심는 모양 그대로다.
	 * principal 이 UUID 가 아닌 문자열이라는 점이 핵심이다.
	 */
	private static Authentication anonymous() {
		return new UsernamePasswordAuthenticationToken("anon:" + UUID.randomUUID(), null,
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
	}

	private JsonNode feedAs(Authentication who, String scope, String cursor) throws Exception {
		var request = get("/api/v1/stories").principal(who).param("scope", scope).param("limit", "50");
		if (cursor != null) {
			request = request.param("cursor", cursor);
		}
		MvcResult result = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private static List<String> ids(JsonNode page) {
		List<String> out = new ArrayList<>();
		page.get("items").forEach((n) -> out.add(n.get("id").asText()));
		return out;
	}

	/**
	 * 같은 DB 를 다른 테스트 클래스도 쓰므로 전체 피드 단정은 이 테스트가 만든 것들끼리의 순서로 한다.
	 * 페이지를 끝까지 걷어 모은 뒤 관심 있는 id 만 남긴다.
	 */
	private List<JsonNode> allItemsFiltered(String scope, Set<String> interesting) throws Exception {
		List<JsonNode> out = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			JsonNode page = feed(scope, cursor, 50);
			for (JsonNode n : page.get("items")) {
				if (interesting.contains(n.get("id").asText())) {
					out.add(n);
				}
			}
			cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
		}
		while (cursor != null && ++pages < 200);
		return out;
	}

	private List<String> allIdsFiltered(String scope, Set<String> interesting) throws Exception {
		List<String> out = new ArrayList<>();
		for (JsonNode n : allItemsFiltered(scope, interesting)) {
			out.add(n.get("id").asText());
		}
		return out;
	}

	private static JsonNode itemById(List<JsonNode> items, String id) {
		for (JsonNode n : items) {
			if (n.get("id").asText().equals(id)) {
				return n;
			}
		}
		throw new AssertionError("피드에 없다: " + id);
	}

	@Test
	@DisplayName("🔴 익명 출입증만 든 사람의 전체 피드에는 PUBLIC 만 나온다 — FOLLOWERS·PRIVATE 이 새면 사고다")
	void anonymousFeedShowsOnlyPublicStories() throws Exception {
		UUID openToAll = StoryFixture.insertStory(this.jdbc, this.stranger, "익명도 보는 공개", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));
		UUID followersOnly = StoryFixture.insertStory(this.jdbc, this.followed, "팔로워 전용", "FOLLOWERS",
				this.now.minus(Duration.ofHours(2)));
		UUID strangerPrivate = StoryFixture.insertStory(this.jdbc, this.stranger, "남 비공개", "PRIVATE",
				this.now.minus(Duration.ofHours(3)));
		UUID minePrivate = StoryFixture.insertStory(this.jdbc, this.me, "내 비공개", "PRIVATE",
				this.now.minus(Duration.ofHours(4)));
		UUID notYetPublished = StoryFixture.insertStory(this.jdbc, this.stranger, "아직 공개 전", "PUBLIC",
				this.now.plus(Duration.ofDays(1)));

		Set<String> seen = new HashSet<>();
		String cursor = null;
		int pages = 0;
		do {
			JsonNode page = feedAs(anonymous(), "ALL", cursor);
			seen.addAll(ids(page));
			cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
		}
		while (cursor != null && ++pages < 200);

		assertThat(seen).contains(openToAll.toString());
		assertThat(seen).doesNotContain(followersOnly.toString(), strangerPrivate.toString(), minePrivate.toString(),
				notYetPublished.toString());
	}

	@Test
	@DisplayName("🔴 익명이 팔로잉 피드를 부르면 400 이다 — 401 이면 앱이 출입증 만료로 보고 다시 받아 재시도해 고리가 된다")
	void anonymousFollowingFeedIsRejectedWithBadRequest() throws Exception {
		this.mockMvc.perform(get("/api/v1/stories").principal(anonymous()).param("scope", "FOLLOWING"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_FEED_LOGIN_REQUIRED"));
	}

	@Test
	@DisplayName("🔴 전체 피드는 공개 시각이 지난 PUBLIC 기록과 내 기록만, 최신순으로 낸다")
	void publicFeedShowsPublishedPublicStoriesNewestFirst() throws Exception {
		UUID followedPublic = StoryFixture.insertStory(this.jdbc, this.followed, "팔로우 공개", "PUBLIC", this.now.minus(Duration.ofHours(3)));
		StoryFixture.insertStory(this.jdbc, this.followed, "팔로워 전용", "FOLLOWERS", this.now.minus(Duration.ofHours(2)));
		UUID strangerPublic = StoryFixture.insertStory(this.jdbc, this.stranger, "남 공개", "PUBLIC", this.now.minus(Duration.ofHours(1)));
		StoryFixture.insertStory(this.jdbc, this.stranger, "남 비공개", "PRIVATE", this.now.minus(Duration.ofHours(1)));
		StoryFixture.insertStory(this.jdbc, this.followed, "아직 공개 전", "PUBLIC", this.now.plus(Duration.ofDays(1)));
		UUID minePrivate = StoryFixture.insertStory(this.jdbc, this.me, "내 비공개", "PRIVATE", this.now.minus(Duration.ofHours(4)));

		Set<String> mine = Set.of(strangerPublic.toString(), followedPublic.toString(), minePrivate.toString());
		List<JsonNode> first = allItemsFiltered("ALL", mine);

		assertThat(allIdsFiltered("ALL", mine)).containsExactly(strangerPublic.toString(), followedPublic.toString(),
				minePrivate.toString());
		List<String> everything = allIdsFiltered("ALL", new HashSet<>(this.jdbc.queryForList(
				"SELECT story_id::text FROM story WHERE author_user_id IN (?, ?, ?)", String.class, this.me, this.followed, this.stranger)));
		assertThat(everything).as("팔로워 전용·비공개·공개 전 기록은 전체 피드에 없다")
				.containsExactlyInAnyOrderElementsOf(mine);
		// 내 것은 mine=true, 남의 것은 false — 앱이 수정·삭제 버튼을 이걸로 켠다.
		assertThat(itemById(first, minePrivate.toString()).get("mine").asBoolean()).isTrue();
		assertThat(itemById(first, strangerPublic.toString()).get("mine").asBoolean()).isFalse();
		assertThat(itemById(first, strangerPublic.toString()).get("author").get("displayName").asText()).isEqualTo("모르는 사람");
	}

	@Test
	@DisplayName("🔴 팔로잉 피드는 팔로우한 사람의 PUBLIC·FOLLOWERS 기록만 낸다")
	void followingFeedShowsOnlyFollowedAuthors() throws Exception {
		UUID followedPublic = StoryFixture.insertStory(this.jdbc, this.followed, "팔로우 공개", "PUBLIC", this.now.minus(Duration.ofHours(3)));
		UUID followedFollowers = StoryFixture.insertStory(this.jdbc, this.followed, "팔로워 전용", "FOLLOWERS", this.now.minus(Duration.ofHours(2)));
		StoryFixture.insertStory(this.jdbc, this.followed, "팔로우 비공개", "PRIVATE", this.now.minus(Duration.ofHours(2)));
		StoryFixture.insertStory(this.jdbc, this.stranger, "남 공개", "PUBLIC", this.now.minus(Duration.ofHours(1)));
		StoryFixture.insertStory(this.jdbc, this.followed, "아직 공개 전", "PUBLIC", this.now.plus(Duration.ofMinutes(5)));

		JsonNode page = feed("FOLLOWING", null, null);

		assertThat(ids(page)).containsExactly(followedFollowers.toString(), followedPublic.toString());
	}

	/** 커서 조건이 시각만 비교하고 story_id 를 빼면 같은 시각 묶음이 페이지 경계에서 통째로 빠지거나 겹친다. */
	@Test
	@DisplayName("🔴 같은 시각 기록이 많고 중간에 새 글이 끼어도 커서 페이지는 빠짐도 중복도 없다")
	void cursorPagesAreExactEvenWithTiesAndInsertsMidway() throws Exception {
		Set<String> expected = new HashSet<>();
		Instant tie = this.now.minus(Duration.ofHours(1));
		for (int i = 0; i < 7; i++) {
			expected.add(StoryFixture.insertStory(this.jdbc, this.stranger, "동시각 " + i, "PUBLIC", tie).toString());
		}
		for (int i = 0; i < 5; i++) {
			expected.add(StoryFixture.insertStory(this.jdbc, this.followed, "다른 시각 " + i, "PUBLIC",
					this.now.minus(Duration.ofHours(2 + i))).toString());
		}

		Set<String> seen = new LinkedHashSet<>();
		String cursor = null;
		int pages = 0;
		do {
			JsonNode page = feed("ALL", cursor, 4);
			List<String> pageIds = ids(page);
			assertThat(pageIds.size()).isLessThanOrEqualTo(4);
			for (String id : pageIds) {
				// 다른 테스트가 남긴 기록도 함께 걷지만, 어느 것도 두 번 나오면 안 된다.
				assertThat(seen.add(id)).as("중복 없이 " + id).isTrue();
			}
			cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
			if (pages == 0) {
				// 첫 페이지를 받은 뒤 새 기록이 올라왔다 — 뒤 페이지들이 밀리면 안 된다.
				StoryFixture.insertStory(this.jdbc, this.stranger, "방금 올린 글", "PUBLIC", this.now);
			}
			pages++;
		}
		while (cursor != null && pages < 500);

		assertThat(seen).as("내가 만든 12건이 빠짐없이 한 번씩").containsAll(expected);
		assertThat(pages).isGreaterThanOrEqualTo(3);
	}

	@Test
	@DisplayName("🔴 공개 전 기록은 작성자 본인 프로필 목록·개수에는 있고, 남에게는 목록에도 개수에도 없다 — S15P21E201-1737")
	void unpublishedStoriesAppearOnlyOnOwnProfile() throws Exception {
		UUID published = StoryFixture.insertStory(this.jdbc, this.me, "이미 공개", "PUBLIC", this.now.minus(Duration.ofHours(1)));
		UUID pending = StoryFixture.insertStory(this.jdbc, this.me, "여행 끝나면 공개", "PUBLIC", this.now.plus(Duration.ofDays(2)));

		List<String> mine = profileStoryIds(StoryFixture.as(this.me), this.me);
		assertThat(mine).as("작성자는 공개 전 것까지 본다 — 안 보이면 찾지도 지우지도 못한다").contains(published.toString(), pending.toString());
		assertThat(profileStoryCount(StoryFixture.as(this.me), this.me)).isEqualTo(2);

		List<String> theirs = profileStoryIds(StoryFixture.as(this.stranger), this.me);
		assertThat(theirs).as("남에게 공개 전 기록은 없는 기록이다").contains(published.toString()).doesNotContain(pending.toString());
		assertThat(profileStoryCount(StoryFixture.as(this.stranger), this.me)).isEqualTo(1);
	}

	private List<String> profileStoryIds(java.security.Principal who, UUID author) throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/users/" + author + "/stories").principal(who).param("limit", "50"))
				.andExpect(status().isOk()).andReturn();
		List<String> out = new ArrayList<>();
		this.json.readTree(result.getResponse().getContentAsString()).get("data").get("items").forEach((n) -> out.add(n.get("id").asText()));
		return out;
	}

	private long profileStoryCount(java.security.Principal who, UUID author) throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/users/" + author + "/profile").principal(who))
				.andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data").get("storyCount").asLong();
	}

	@Test
	@DisplayName("깨진 커서는 400 FEED_CURSOR_INVALID 이고, limit 은 50 을 넘지 않는다")
	void invalidCursorAndLimitClamp() throws Exception {
		this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(this.me)).param("cursor", "not-a-cursor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("FEED_CURSOR_INVALID"));

		for (int i = 0; i < 60; i++) {
			StoryFixture.insertStory(this.jdbc, this.stranger, "많이 " + i, "PUBLIC", this.now.minus(Duration.ofMinutes(i + 1)));
		}
		JsonNode page = feed("ALL", null, 999);
		assertThat(page.get("items").size()).isEqualTo(50);
		assertThat(page.get("nextCursor").isNull()).isFalse();
	}
}
