package com.gabolle.backend.story;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.gabolle.backend.story.presentation.StoryCoauthorController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 공동 작성 경로 다섯의 응답 본문 칸 이름만 못 박는다. 권한·판정 로직은
 * {@code StoryCoauthorIntegrationTest} 의 몫이다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryCoauthorResponseBodyTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryCoauthorController storyCoauthorController;

	@Autowired
	private StoryExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID author;
	private UUID invitee;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyCoauthorController)
				.setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "만든 사람");
		this.invitee = StoryFixture.insertUser(this.jdbc, "초대받은 사람");
	}

	private void insertTripMember(UUID tripId, UUID userId, String role) {
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, OffsetDateTime.now(ZoneOffset.UTC));
	}

	private UUID insertStoryWithTrip(UUID authorUserId, UUID tripId) {
		UUID storyId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO story (story_id, author_user_id, trip_id, body, visibility, publish_at, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, 'PUBLIC', ?, ?, ?)",
				storyId, authorUserId, tripId, "본문", Instant.now().minus(Duration.ofHours(1)).atOffset(ZoneOffset.UTC),
				now, now);
		return storyId;
	}

	private String body(Map<String, Object> fields) throws Exception {
		return this.json.writeValueAsString(fields);
	}

	@Test
	@DisplayName("초대 발급 응답은 inviteId·storyId·token·expiresAt·acceptPath 다섯 칸이다")
	void inviteResponseShape() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));

		this.mockMvc.perform(post("/api/v1/stories/{id}/invites", storyId).principal(StoryFixture.as(this.author)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.inviteId").exists())
				.andExpect(jsonPath("$.data.storyId").value(storyId.toString()))
				.andExpect(jsonPath("$.data.token").exists())
				.andExpect(jsonPath("$.data.expiresAt").exists())
				.andExpect(jsonPath("$.data.acceptPath").value(org.hamcrest.Matchers.startsWith("/api/v1/story-invites/")))
				// 앱이 이 칸으로 딥링크를 만든다.
				.andExpect(jsonPath("$.data.acceptPath", org.hamcrest.Matchers.endsWith("/accept")));
	}

	@Test
	@DisplayName("초대 수락 응답은 storyId·alreadyJoined·joinedAt 세 칸이다")
	void acceptResponseShape() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
		JsonNode invite = this.json.readTree(this.mockMvc
				.perform(post("/api/v1/stories/{id}/invites", storyId).principal(StoryFixture.as(this.author)))
				.andReturn().getResponse().getContentAsString()).get("data");
		String token = invite.get("token").asText();

		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.storyId").value(storyId.toString()))
				.andExpect(jsonPath("$.data.alreadyJoined").value(false))
				.andExpect(jsonPath("$.data.joinedAt").exists());
	}

	@Test
	@DisplayName("참여자 목록 응답은 coauthors 배열이고, 그 줄마다 userId·displayName·isAuthor·joinedAt 이 있다")
	void coauthorsListResponseShape() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));

		this.mockMvc.perform(get("/api/v1/stories/{id}/coauthors", storyId).principal(StoryFixture.as(this.author)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.coauthors").isArray())
				// 공동 작성자가 하나도 없어도 만든 사람 한 줄은 항상 있다.
				.andExpect(jsonPath("$.data.coauthors.length()").value(1))
				.andExpect(jsonPath("$.data.coauthors[0].userId").value(this.author.toString()))
				.andExpect(jsonPath("$.data.coauthors[0].displayName").value("만든 사람"))
				.andExpect(jsonPath("$.data.coauthors[0].isAuthor").value(true))
				.andExpect(jsonPath("$.data.coauthors[0].joinedAt").exists())
				// coauthors 안에 또 coauthors 가 들어가는 이중 포장이 없다.
				.andExpect(jsonPath("$.data.coauthors[0].coauthors").doesNotExist());
	}

	@Test
	@DisplayName("여행 동행자 추가는 본문 없이 204 다")
	void addCoauthorsResponseShape() throws Exception {
		UUID tripId = StoryFixture.insertTrip(this.jdbc, this.author, LocalDate.of(2026, 9, 10),
				LocalDate.of(2026, 9, 12), "Asia/Seoul");
		insertTripMember(tripId, this.invitee, "EDITOR");
		UUID storyId = insertStoryWithTrip(this.author, tripId);

		MvcResult result = this.mockMvc
				.perform(post("/api/v1/stories/{id}/coauthors", storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("userIds", List.of(this.invitee.toString())))))
				.andExpect(status().isNoContent()).andReturn();

		// 본문이 비어 있어야 앱이 JSON 으로 파싱하지 않는다.
		org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEmpty();
	}

	@Test
	@DisplayName("참여자 제거는 본문 없이 204 다")
	void removeCoauthorResponseShape() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
		JsonNode invite = this.json.readTree(this.mockMvc
				.perform(post("/api/v1/stories/{id}/invites", storyId).principal(StoryFixture.as(this.author)))
				.andReturn().getResponse().getContentAsString()).get("data");
		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", invite.get("token").asText())
				.principal(StoryFixture.as(this.invitee))).andExpect(status().isOk());

		MvcResult result = this.mockMvc
				.perform(delete("/api/v1/stories/{id}/coauthors/{userId}", storyId, this.invitee)
						.principal(StoryFixture.as(this.author)))
				.andExpect(status().isNoContent()).andReturn();
		org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEmpty();
	}
}
