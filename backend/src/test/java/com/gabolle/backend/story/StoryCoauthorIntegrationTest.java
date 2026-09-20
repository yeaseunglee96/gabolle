package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryCoauthorController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.testslice.StorySliceApplication;

/** 기록 공동 작성. 실제 PostgreSQL 위에서 돈다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryCoauthorIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private StoryController storyController;

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
	private UUID other;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.storyCoauthorController)
				.setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "만든 사람");
		this.invitee = StoryFixture.insertUser(this.jdbc, "초대받은 사람");
		this.other = StoryFixture.insertUser(this.jdbc, "관계없는 사람");
	}

	private UUID insertStoryWithTrip(UUID authorUserId, UUID tripId, String body, String visibility,
			Instant publishAt) {
		UUID storyId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO story (story_id, author_user_id, trip_id, body, visibility, publish_at, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				storyId, authorUserId, tripId, body, visibility, publishAt.atOffset(ZoneOffset.UTC), now, now);
		return storyId;
	}

	private void insertTripMember(UUID tripId, UUID userId, String role) {
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, OffsetDateTime.now(ZoneOffset.UTC));
	}

	private String body(Map<String, Object> fields) throws Exception {
		return this.json.writeValueAsString(fields);
	}

	private JsonNode issueInvite(UUID storyId, UUID asUser) throws Exception {
		MvcResult result = this.mockMvc
				.perform(post("/api/v1/stories/{id}/invites", storyId).principal(StoryFixture.as(asUser)))
				.andExpect(status().isCreated()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	/** 이미 만료된 초대를 직접 심는다. 발급 경로는 7일 뒤 만료라 이 상태를 만들 수 없다. */
	private String insertExpiredInvite(UUID storyId, UUID createdBy) {
		String token = "expired-" + UUID.randomUUID();
		Instant createdAt = Instant.now().minus(Duration.ofDays(8));
		Instant expiresAt = Instant.now().minus(Duration.ofDays(1));
		this.jdbc.update(
				"INSERT INTO story_invite (story_invite_id, story_id, token, created_by, created_at, expires_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), storyId, token, createdBy, createdAt.atOffset(ZoneOffset.UTC),
				expiresAt.atOffset(ZoneOffset.UTC));
		return token;
	}

	private Integer coauthorRowCount(UUID storyId, UUID userId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM story_coauthor WHERE story_id = ? AND user_id = ?",
				Integer.class, storyId, userId);
	}

	@Test
	@DisplayName("초대 링크를 받은 사람이 수락하면 그 기록의 본문과 사진을 고칠 수 있다")
	void inviteeCanEditBodyAfterAccepting() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
		JsonNode invite = issueInvite(storyId, this.author);
		String token = invite.get("token").asText();

		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.alreadyJoined").value(false));

		// 사진 교체는 StoryUpdateRequest 가 아예 받지 않으므로(만든 사람도 못 고친다) 본문 수정만 잰다.
		this.mockMvc.perform(patch("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.invitee))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "초대받은 사람이 고침"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("초대받은 사람이 고침"));
		assertThat(this.jdbc.queryForObject("SELECT body FROM story WHERE story_id = ?", String.class, storyId))
				.isEqualTo("초대받은 사람이 고침");
	}

	@Test
	@DisplayName("같은 링크를 두 번 눌러도 오류가 아니다 (두 번째는 alreadyJoined=true, 참여자 줄은 하나)")
	void acceptingTwiceIsIdempotent() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
		String token = issueInvite(storyId, this.author).get("token").asText();

		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.alreadyJoined").value(false));
		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.alreadyJoined").value(true));

		assertThat(coauthorRowCount(storyId, this.invitee)).isEqualTo(1);
	}

	@Test
	@DisplayName("공동 작성자는 비공개 기록도 볼 수 있다")
	void coauthorCanViewPrivateStory() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "비공개 기록", "PRIVATE",
				Instant.now().minus(Duration.ofHours(1)));
		String token = issueInvite(storyId, this.author).get("token").asText();
		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mine").value(false));

		this.mockMvc.perform(get("/api/v1/stories/{id}/coauthors", storyId).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.coauthors[?(@.userId=='" + this.invitee + "')].isAuthor").value(false));

		// 관계없는 사람은 여전히 404 — 비공개 기록이 아무에게나 열린 것이 아니다.
		this.mockMvc.perform(get("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.other)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("공동 작성자가 공개 범위를 바꾸거나 기록을 지우려 하면 거부된다 (공개 범위 변경 403, 삭제 403)")
	void coauthorCannotChangeVisibilityOrDelete() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "비공개 기록", "PRIVATE",
				Instant.now().minus(Duration.ofHours(1)));
		String token = issueInvite(storyId, this.author).get("token").asText();
		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk());

		// 본문만 고치는 것은 되지만, 공개 범위를 같이 실으면 요청 전체가 거부된다.
		this.mockMvc.perform(patch("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.invitee))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("body", "그래도 고쳐본다", "visibility", "PUBLIC"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("STORY_FORBIDDEN"));
		assertThat(this.jdbc.queryForObject("SELECT visibility FROM story WHERE story_id = ?", String.class, storyId))
				.isEqualTo("PRIVATE");

		this.mockMvc.perform(delete("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("STORY_FORBIDDEN"));
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM story WHERE story_id = ?", Boolean.class,
				storyId)).isTrue();
	}

	@Test
	@DisplayName("그 여행의 동행자가 아닌 사람을 골라 넣기로 추가하려 하면 거부된다")
	void addingNonTripMemberIsRejected() throws Exception {
		UUID tripId = StoryFixture.insertTrip(this.jdbc, this.author, LocalDate.of(2026, 9, 10),
				LocalDate.of(2026, 9, 12), "Asia/Seoul");
		insertTripMember(tripId, this.invitee, "EDITOR"); // 이 사람은 동행자다 — 거절 대상이 아니다.
		UUID storyId = insertStoryWithTrip(this.author, tripId, "여행 기록", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));

		// this.other 는 trip_member 행이 없다 — 동행자가 아니다.
		this.mockMvc.perform(post("/api/v1/stories/{id}/coauthors", storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("userIds", List.of(this.other.toString())))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_COAUTHOR_NOT_TRIP_MEMBER"));

		assertThat(coauthorRowCount(storyId, this.other)).isZero();
	}

	@Test
	@DisplayName("기존 기록의 동작이 하나도 바뀌지 않는다 (공동 작성자가 없는 기록에서 만든 사람은 그대로 고치고 지우고, 남은 여전히 못 고친다)")
	void unaffectedStoryBehavesAsBefore() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));

		// story_coauthor 가 비어 있어야 아래 결과가 예전 그대로임을 뜻한다.
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM story_coauthor WHERE story_id = ?", Integer.class,
				storyId)).isZero();

		this.mockMvc.perform(patch("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.other))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "남이 고침"))))
				.andExpect(status().isForbidden());

		this.mockMvc.perform(patch("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "만든 사람이 고침"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("만든 사람이 고침"));

		this.mockMvc.perform(delete("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("나만 보기 기록에 초대받은 사람이 수락할 수 있다")
	void inviteeWithNoPriorAccessCanAcceptIntoPrivateStory() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "우리끼리만 쓰는 기록", "PRIVATE",
				Instant.now().minus(Duration.ofHours(1)));

		// 수락 전에는 이 기록에 대한 권한이 전혀 없다.
		this.mockMvc.perform(get("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isNotFound());

		String token = issueInvite(storyId, this.author).get("token").asText();

		// 표만으로 수락된다 — 열람 권한을 먼저 요구했다면 여기서 404 가 났을 것이다.
		this.mockMvc.perform(post("/api/v1/story-invites/{token}/accept", token).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.alreadyJoined").value(false))
				.andExpect(jsonPath("$.data.joinedAt").exists());

		this.mockMvc.perform(get("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("만료된 링크로는 수락되지 않고, 참여자 줄도 안 생긴다. 만료는 410 이다")
	void expiredInviteIsRejectedWithGoneAndNoRowIsCreated() throws Exception {
		UUID storyId = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC",
				Instant.now().minus(Duration.ofHours(1)));
		String expiredToken = insertExpiredInvite(storyId, this.author);

		this.mockMvc.perform(
						post("/api/v1/story-invites/{token}/accept", expiredToken).principal(StoryFixture.as(this.invitee)))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.error.code").value("STORY_INVITE_EXPIRED"));

		assertThat(coauthorRowCount(storyId, this.invitee)).as("만료된 링크는 참여자 행을 만들지 않는다").isZero();
	}
}
