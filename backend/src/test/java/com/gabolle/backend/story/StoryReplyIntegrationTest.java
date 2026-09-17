package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Map;
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

/**
 * 댓글 — S15P21E201-1183. <b>진짜 PostgreSQL</b> 이 있어야 하는 것만 여기서 본다.
 *
 * <p>DB 없이 판정할 수 있는 것(공개범위를 서버가 정한다, 세기가 0 아래로 안 간다, 손자를 안 센다)은
 * {@link StoryReplyTest} 가 이미 본다. 여기는 <b>같은 표에 두었기 때문에 생긴 위험</b>을 본다 —
 * 댓글이 피드에 새는가, 세기가 DB 를 오간 뒤에도 맞는가.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryReplyIntegrationTest {

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

	private UUID author;

	private UUID commenter;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.commenter = StoryFixture.insertUser(this.jdbc, "댓글쓴이");
	}

	private JsonNode create(UUID as, Map<String, Object> fields) throws Exception {
		MvcResult result = this.mockMvc
				.perform(post("/api/v1/stories").principal(StoryFixture.as(as))
						.contentType(MediaType.APPLICATION_JSON)
						.content(this.json.writeValueAsString(fields)))
				.andExpect(status().isCreated()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private String newPost(UUID as, String body) throws Exception {
		return create(as, Map.of("body", body, "visibility", "PUBLIC")).get("id").asString();
	}

	private JsonNode reply(UUID as, String parentId, String body) throws Exception {
		return create(as, Map.of("body", body, "parentStoryId", parentId));
	}

	private JsonNode fetch(String storyId, UUID as) throws Exception {
		MvcResult result = this.mockMvc
				.perform(get("/api/v1/stories/" + storyId).principal(StoryFixture.as(as)))
				.andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private JsonNode replies(String storyId, UUID as) throws Exception {
		MvcResult result = this.mockMvc
				.perform(get("/api/v1/stories/" + storyId + "/replies").principal(StoryFixture.as(as)))
				.andExpect(status().isOk()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	@Test
	@DisplayName("🔴 댓글은 피드에 안 나온다 — 같은 표에 두었기 때문에 생긴 가장 큰 위험이다")
	void repliesDoNotLeakIntoTheFeed() throws Exception {
		String storyId = newPost(this.author, "원글");
		reply(this.commenter, storyId, "댓글");

		MvcResult result = this.mockMvc
				.perform(get("/api/v1/stories").param("scope", "ALL").principal(StoryFixture.as(this.commenter)))
				.andExpect(status().isOk()).andReturn();
		JsonNode items = this.json.readTree(result.getResponse().getContentAsString()).get("data").get("items");

		// 원글 하나만. 댓글이 섞여 나오면 NOT_DELETED_AND_PUBLISHED 에 조건이 빠진 것이다.
		assertThat(items).hasSize(1);
		assertThat(items.get(0).get("id").asString()).isEqualTo(storyId);
		assertThat(items.get(0).get("parentId").isNull()).isTrue();
	}

	@Test
	@DisplayName("🔴 댓글을 달면 부모의 replyCount 가 오르고, 지우면 내려간다")
	void replyCountFollowsTheRealRows() throws Exception {
		String storyId = newPost(this.author, "원글");
		assertThat(fetch(storyId, this.author).get("replyCount").asInt()).isZero();

		String replyId = reply(this.commenter, storyId, "댓글").get("id").asString();
		assertThat(fetch(storyId, this.author).get("replyCount").asInt()).isEqualTo(1);

		this.mockMvc.perform(delete("/api/v1/stories/" + replyId).principal(StoryFixture.as(this.commenter)))
				.andExpect(status().isNoContent());

		assertThat(fetch(storyId, this.author).get("replyCount").asInt()).isZero();
	}

	@Test
	@DisplayName("🔴 댓글의 댓글이 달린다 — 깊이 제한이 없고 목록은 바로 아래만 준다")
	void repliesNestWithoutADepthLimit() throws Exception {
		String storyId = newPost(this.author, "원글");
		String depth1 = reply(this.commenter, storyId, "댓글").get("id").asString();
		String depth2 = reply(this.author, depth1, "답글").get("id").asString();
		reply(this.commenter, depth2, "답답글");

		// 원글의 목록에는 댓글 하나만. 손자는 안 딸려 온다 — 그 댓글의 id 로 다시 부른다.
		JsonNode first = replies(storyId, this.author);
		assertThat(first).hasSize(1);
		assertThat(first.get(0).get("id").asString()).isEqualTo(depth1);
		assertThat(first.get(0).get("parentId").asString()).isEqualTo(storyId);
		assertThat(first.get(0).get("replyCount").asInt()).isEqualTo(1);

		assertThat(replies(depth1, this.author)).hasSize(1);
		assertThat(replies(depth2, this.author)).hasSize(1);
	}

	@Test
	@DisplayName("🔴 댓글을 지워도 그 댓글의 답글은 남는다 — 남의 글을 지우지 않는다")
	void deletingAReplyKeepsItsChildren() throws Exception {
		String storyId = newPost(this.author, "원글");
		String parentReply = reply(this.commenter, storyId, "댓글").get("id").asString();
		String childReply = reply(this.author, parentReply, "답글").get("id").asString();

		this.mockMvc.perform(delete("/api/v1/stories/" + parentReply).principal(StoryFixture.as(this.commenter)))
				.andExpect(status().isNoContent());

		// 지워진 댓글은 목록에서 빠지지만, 자식 행은 DB 에 그대로 있고 부모를 계속 가리킨다.
		assertThat(replies(storyId, this.author)).isEmpty();
		String parentOfChild = this.jdbc.queryForObject(
				"SELECT parent_story_id::text FROM story WHERE story_id = ?::uuid", String.class, childReply);
		assertThat(parentOfChild).isEqualTo(parentReply);
		Integer deleted = this.jdbc.queryForObject(
				"SELECT count(*) FROM story WHERE story_id = ?::uuid AND deleted_at IS NULL", Integer.class,
				childReply);
		assertThat(deleted).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 볼 수 없는 글에는 댓글을 못 단다 — 404 로 존재를 안 알린다")
	void cannotReplyToAStoryYouCannotSee() throws Exception {
		String privateStory = create(this.author, Map.of("body", "나만 보기", "visibility", "PRIVATE"))
				.get("id").asString();

		this.mockMvc
				.perform(post("/api/v1/stories").principal(StoryFixture.as(this.commenter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(this.json.writeValueAsString(
								Map.of("body", "댓글", "parentStoryId", privateStory))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("댓글 행의 공개범위·공개시각은 서버가 채운다 — DB 에서 확인한다")
	void replyRowCarriesServerChosenColumns() throws Exception {
		String storyId = newPost(this.author, "원글");
		String replyId = reply(this.commenter, storyId, "댓글").get("id").asString();

		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT visibility, publish_at, created_at, trip_id, place_id FROM story WHERE story_id = ?::uuid",
				replyId);

		assertThat(row.get("visibility")).isEqualTo("PUBLIC");
		assertThat(row.get("publish_at")).isEqualTo(row.get("created_at"));
		assertThat(row.get("trip_id")).isNull();
		assertThat(row.get("place_id")).isNull();
	}
}
