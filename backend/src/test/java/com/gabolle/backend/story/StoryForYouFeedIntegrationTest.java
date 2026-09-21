package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import com.gabolle.testslice.StorySliceApplication;

/**
 * 맞춤({@code scope=FOR_YOU})이 팔로우한 사람의 기록을 내고, 낼 것이 없으면 전체 인기순으로
 * 대체하는지 본다. 그리고 <b>대체가 일어났을 때 응답 머리가 그 사실을 말하는지</b>.
 *
 * <p>머리가 핵심이다. 실서버의 팔로우 관계가 한 건뿐이라 거의 모든 요청이 대체로 가는데, 그때
 * 화면이 「맞춤 추천」이라고 써 놓으면 사용자에게 사실과 다른 말을 하게 된다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryForYouFeedIntegrationTest {

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
	private StoryExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	/** 아무도 팔로우하지 않은 사람 — 대체 경로로 간다. */
	private UUID loner;

	/** 한 사람을 팔로우한 사람. */
	private UUID follower;

	/** 팔로우당하는 사람. */
	private UUID followee;

	private UUID followeeStory;
	private UUID strangerStory;

	private final Instant now = Instant.now();

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController)
				.setControllerAdvice(this.handler).build();
		this.loner = StoryFixture.insertUser(this.jdbc, "혼자");
		this.follower = StoryFixture.insertUser(this.jdbc, "팔로우하는 사람");
		this.followee = StoryFixture.insertUser(this.jdbc, "팔로우당하는 사람");
		UUID stranger = StoryFixture.insertUser(this.jdbc, "모르는 사람");
		StoryFixture.insertFollow(this.jdbc, this.follower, this.followee);

		this.followeeStory = StoryFixture.insertStory(this.jdbc, this.followee, "팔로우한 사람의 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(2)));
		// 좋아요를 몰아 줘도 팔로잉 밖이면 맞춤 경로에는 안 나온다 — 대체와 구분하는 자리다.
		this.strangerStory = StoryFixture.insertStory(this.jdbc, stranger, "모르는 사람의 인기 기록", "PUBLIC",
				this.now.minus(Duration.ofHours(1)));
		like(this.strangerStory, StoryFixture.insertUser(this.jdbc, "손님1"));
		like(this.strangerStory, StoryFixture.insertUser(this.jdbc, "손님2"));
	}

	@Test
	@DisplayName("팔로우가 있으면 그 사람들의 기록을 낸다 — 머리는 FOR_YOU")
	void servesFollowedAuthorsWhenPersonalizable() throws Exception {
		this.mockMvc.perform(request(StoryFixture.as(this.follower)))
				.andExpect(status().isOk())
				.andExpect(header().string(StoryController.FEED_APPLIED_HEADER, "FOR_YOU"));

		List<String> ids = walk(StoryFixture.as(this.follower));
		assertThat(ids).contains(this.followeeStory.toString());
		assertThat(ids).doesNotContain(this.strangerStory.toString());
	}

	@Test
	@DisplayName("🔴 팔로우가 없으면 전체 인기순으로 대체하고, 머리가 그 사실을 말한다 — FOR_YOU 가 아니라 POPULAR")
	void fallsBackAndSaysSo() throws Exception {
		this.mockMvc.perform(request(StoryFixture.as(this.loner)))
				.andExpect(status().isOk())
				.andExpect(header().string(StoryController.FEED_APPLIED_HEADER, "POPULAR"));

		// 빈 목록이 아니라 인기순이 나온다. 빈 목록이면 화면이 「팔로우할 사람을 찾으세요」만 띄운다.
		assertThat(walk(StoryFixture.as(this.loner))).contains(this.strangerStory.toString());
	}

	@Test
	@DisplayName("익명도 거절하지 않는다 — 팔로우가 없는 사람일 뿐이라 대체로 간다")
	void anonymousFallsBackInsteadOfFailing() throws Exception {
		this.mockMvc.perform(request(anonymous()))
				.andExpect(status().isOk())
				.andExpect(header().string(StoryController.FEED_APPLIED_HEADER, "POPULAR"));
	}

	@Test
	@DisplayName("팔로잉(FOLLOWING)은 익명에게 400 그대로다 — 「팔로잉만 달라」와 「알아서 달라」는 다른 요청이다")
	void followingStillRejectsAnonymous() throws Exception {
		this.mockMvc.perform(get("/api/v1/stories").principal(anonymous()).param("scope", "FOLLOWING"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("sort=FOR_YOU 로는 부를 수 없다 — 맞춤은 scope 이지 정렬이 아니다")
	void forYouIsNotASortParameter() throws Exception {
		this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(this.loner)).param("sort", "FOR_YOU"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("맞춤 커서로 이어봐도 대체 여부가 중간에 바뀌지 않는다")
	void keepsTheSamePathWhilePaging() throws Exception {
		MvcResult first = this.mockMvc.perform(request(StoryFixture.as(this.loner)).param("limit", "1"))
				.andExpect(status().isOk()).andReturn();
		JsonNode page = body(first);
		JsonNode next = page.get("nextCursor");
		if (next == null || next.isNull()) {
			return; // 이 DB 에 공개 기록이 한 건뿐이면 이어볼 것이 없다
		}

		this.mockMvc.perform(request(StoryFixture.as(this.loner)).param("cursor", next.asText()))
				.andExpect(status().isOk())
				.andExpect(header().string(StoryController.FEED_APPLIED_HEADER, "POPULAR"));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
			Authentication who) {
		return get("/api/v1/stories").principal(who).param("scope", "FOR_YOU").param("limit", "50");
	}

	private static Authentication anonymous() {
		return new UsernamePasswordAuthenticationToken("anon:" + UUID.randomUUID(), null,
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
	}

	private JsonNode body(MvcResult result) throws Exception {
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	/**
	 * 쪽을 끝까지 걷어 id 를 모은다. 한 쪽만 보면 안 된다 — 같은 DB 를 다른 테스트도 쓰므로
	 * 기록이 수백 건이라 이 테스트가 만든 것이 첫 쪽에 없을 수 있다.
	 */
	private List<String> walk(Authentication who) throws Exception {
		List<String> out = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			var req = request(who);
			if (cursor != null) {
				req = req.param("cursor", cursor);
			}
			MvcResult result = this.mockMvc.perform(req).andExpect(status().isOk()).andReturn();
			JsonNode page = body(result);
			out.addAll(ids(page));
			JsonNode next = page.get("nextCursor");
			cursor = (next == null || next.isNull()) ? null : next.asText();
		}
		while (cursor != null && ++pages < 200);
		return out;
	}

	private void like(UUID storyId, UUID userId) {
		OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO story_reaction (story_id, user_id, reaction, created_at, updated_at,"
				+ " reacted_at, like_recorded, dislike_recorded) VALUES (?, ?, 'LIKE', ?, ?, ?, true, false)",
				storyId, userId, at, at, at);
	}

	private static List<String> ids(JsonNode page) {
		List<String> out = new ArrayList<>();
		page.get("items").forEach((n) -> out.add(n.get("id").asText()));
		return out;
	}
}
