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
 * 인기순({@code sort=POPULAR})이 좋아요 많은 순으로 나오고, 동점일 때 순서가 흔들리지 않는지 본다.
 *
 * <p>동점 확인이 핵심이다. 실서버는 기록 39건에 반응 6건이라 대부분이 0 으로 동점인데, 동점의
 * 순서를 정해 두지 않으면 새로고침할 때마다 목록이 뒤바뀌어 사용자가 고장으로 읽는다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryPopularFeedIntegrationTest {

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

	private UUID me;

	private final Instant now = Instant.now();

	/** 좋아요 2 · 1 · 0 · 0. 뒤의 둘이 동점이고, 그중 newer 가 older 보다 나중에 올라왔다. */
	private UUID twoLikes;
	private UUID oneLike;
	private UUID newerZero;
	private UUID olderZero;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController)
				.setControllerAdvice(this.handler).build();
		this.me = StoryFixture.insertUser(this.jdbc, "나");

		// 공개 시각을 일부러 「좋아요 순서와 반대로」 준다 — 인기순이 최신순을 덮는지 보려면
		// 두 순서가 어긋나 있어야 한다.
		this.twoLikes = story("좋아요 둘", this.now.minus(Duration.ofHours(4)));
		this.oneLike = story("좋아요 하나", this.now.minus(Duration.ofHours(3)));
		this.olderZero = story("좋아요 없음 - 예전", this.now.minus(Duration.ofHours(2)));
		this.newerZero = story("좋아요 없음 - 최근", this.now.minus(Duration.ofHours(1)));

		like(this.twoLikes, StoryFixture.insertUser(this.jdbc, "손님1"));
		like(this.twoLikes, StoryFixture.insertUser(this.jdbc, "손님2"));
		like(this.oneLike, StoryFixture.insertUser(this.jdbc, "손님3"));
	}

	@Test
	@DisplayName("인기순은 좋아요 많은 순이다 — 최신순과 순서가 반대여도")
	void ordersByLikeCount() throws Exception {
		assertThat(walkMine("POPULAR")).containsSubsequence(this.twoLikes.toString(), this.oneLike.toString());
	}

	@Test
	@DisplayName("좋아요가 같으면 최신순으로 내려간다 — 동점의 순서를 DB 에 맡기지 않는다")
	void breaksTiesByRecency() throws Exception {
		assertThat(walkMine("POPULAR")).containsSubsequence(this.newerZero.toString(), this.olderZero.toString());
	}

	@Test
	@DisplayName("같은 요청을 두 번 해도 순서가 같다")
	void isStableAcrossCalls() throws Exception {
		assertThat(walkMine("POPULAR")).isEqualTo(walkMine("POPULAR"));
	}

	@Test
	@DisplayName("기본값은 최신순이다 — sort 를 안 주면 지금 동작이 그대로다")
	void defaultsToRecent() throws Exception {
		assertThat(walkMine(null)).containsSubsequence(this.newerZero.toString(), this.olderZero.toString(),
				this.oneLike.toString(), this.twoLikes.toString());
	}

	@Test
	@DisplayName("적용된 정렬을 응답 머리에 싣는다 — 화면이 사용자에게 무엇을 보여주는지 말할 근거")
	void reportsAppliedSort() throws Exception {
		this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(this.me)).param("sort", "POPULAR"))
				.andExpect(status().isOk())
				.andExpect(header().string(StoryController.FEED_APPLIED_HEADER, "POPULAR"));

		this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(this.me)))
				.andExpect(status().isOk())
				.andExpect(header().string(StoryController.FEED_APPLIED_HEADER, "RECENT"));
	}

	@Test
	@DisplayName("인기순도 커서로 끊긴다 — 쪽을 넘겨도 겹치거나 건너뛰지 않는다")
	void pagesWithoutGapsOrRepeats() throws Exception {
		List<String> seen = walkMine("POPULAR");

		assertThat(seen).doesNotHaveDuplicates().containsAll(mine());
		assertThat(seen).containsSubsequence(this.twoLikes.toString(), this.oneLike.toString());
	}

	@Test
	@DisplayName("최신순 커서를 인기순에 보내면 400 이다 — 조용히 첫 쪽으로 되돌리지 않는다")
	void rejectsCursorFromTheOtherSort() throws Exception {
		JsonNode recentPage = feed(null, null, 1);
		String recentCursor = recentPage.get("nextCursor").asText();

		this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(this.me))
						.param("sort", "POPULAR").param("cursor", recentCursor))
				.andExpect(status().isBadRequest());
	}

	private UUID story(String body, Instant publishAt) {
		return StoryFixture.insertStory(this.jdbc, this.me, body, "PUBLIC", publishAt);
	}

	private void like(UUID storyId, UUID userId) {
		OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO story_reaction (story_id, user_id, reaction, created_at, updated_at,"
				+ " reacted_at, like_recorded, dislike_recorded) VALUES (?, ?, 'LIKE', ?, ?, ?, true, false)",
				storyId, userId, at, at, at);
	}

	/** 이 테스트가 만든 기록 넷. */
	private List<String> mine() {
		return List.of(this.twoLikes.toString(), this.oneLike.toString(),
				this.newerZero.toString(), this.olderZero.toString());
	}

	/**
	 * 쪽을 끝까지 걷어 이 테스트가 만든 기록만 «순서대로» 남긴다.
	 *
	 * <p>한 쪽만 보면 안 된다. 같은 DB 를 다른 테스트 클래스도 쓰므로 기록이 수백 건이고, 첫 쪽
	 * 50개 안에 이 테스트의 것이 안 들어온다 — 그래서 처음에 이 검사가 CI 에서만 빨개졌다.
	 */
	private List<String> walkMine(String sort) throws Exception {
		List<String> out = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			JsonNode page = feed(sort, cursor, 50);
			for (String id : ids(page)) {
				if (mine().contains(id)) {
					out.add(id);
				}
			}
			JsonNode next = page.get("nextCursor");
			cursor = (next == null || next.isNull()) ? null : next.asText();
		}
		while (cursor != null && ++pages < 200);
		return out;
	}

	private JsonNode feed(String sort, String cursor) throws Exception {
		return feed(sort, cursor, 50);
	}

	private JsonNode feed(String sort, String cursor, int limit) throws Exception {
		var request = get("/api/v1/stories").principal(StoryFixture.as(this.me))
				.param("limit", String.valueOf(limit));
		if (sort != null) {
			request = request.param("sort", sort);
		}
		if (cursor != null) {
			request = request.param("cursor", cursor);
		}
		MvcResult result = this.mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	/** 같은 DB 를 다른 테스트도 쓰므로 단정은 이 테스트가 만든 것들끼리의 «순서»로만 한다. */
	private static List<String> ids(JsonNode page) {
		List<String> out = new ArrayList<>();
		page.get("items").forEach((n) -> out.add(n.get("id").asText()));
		return out;
	}
}
