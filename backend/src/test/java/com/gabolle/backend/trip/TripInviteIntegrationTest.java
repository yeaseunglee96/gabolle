package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.backend.trip.presentation.TripCollaborationController;
import com.gabolle.backend.trip.presentation.TripCollaborationExceptionHandler;
import com.gabolle.testslice.CollaborationSliceApplication;

/** 동행자 초대의 발급과 수락을 실제 PostgreSQL 위에서 잰다. */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripInviteIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripCollaborationController controller;

	@Autowired
	private TripCollaborationExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID owner;

	private UUID tripId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller).setControllerAdvice(this.handler).build();
		this.owner = StoryFixture.insertUser(this.jdbc, "소유자");
		this.tripId = StoryFixture.insertTrip(this.jdbc, this.owner, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				"Asia/Seoul");
		insertMember(this.owner, "OWNER");
	}

	// ---- 시드 도우미 ----

	private void insertMember(UUID userId, String role) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, now())",
				UUID.randomUUID(), this.tripId, userId, role);
	}

	// ---- 호출 도우미 ----

	private JsonNode createInvite(UUID as, String role) throws Exception {
		MvcResult result = this.mockMvc.perform(post("/api/v1/trips/{tripId}/invites", this.tripId)
						.principal(StoryFixture.as(as))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"" + role + "\"}"))
				.andReturn();
		return this.json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode accept(UUID as, String token) throws Exception {
		MvcResult result = this.mockMvc
				.perform(post("/api/v1/trip-invites/{token}/accept", token).principal(StoryFixture.as(as)))
				.andReturn();
		return this.json.readTree(result.getResponse().getContentAsString());
	}

	// ---- 조회 도우미 ----

	private int inviteRowCount() {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM trip_invite WHERE trip_id = ?", Integer.class,
				this.tripId);
		return count == null ? 0 : count;
	}

	private int memberRowCount(UUID userId) {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM trip_member WHERE trip_id = ? AND user_id = ?",
				Integer.class, this.tripId, userId);
		return count == null ? 0 : count;
	}

	private Map<String, Object> memberRow(UUID userId) {
		return this.jdbc.queryForMap(
				"SELECT role, trip_invite_id::text AS trip_invite_id, invited_by::text AS invited_by, invited_at "
						+ "FROM trip_member WHERE trip_id = ? AND user_id = ?",
				this.tripId, userId);
	}

	// ---- 테스트 ----

	@Test
	@DisplayName("소유자가 초대를 만들면 201, token 43글자, expiresAt 은 지금+7일(±1분), 행이 하나 생긴다")
	void ownerCanIssueInvite() throws Exception {
		Instant before = Instant.now();

		JsonNode response = createInvite(this.owner, "EDITOR");
		JsonNode data = response.get("data");

		assertThat(response.get("error").isNull()).isTrue();
		assertThat(data.get("token").asText()).hasSize(43);
		assertThat(data.get("role").asText()).isEqualTo("EDITOR");
		assertThat(data.get("tripId").asText()).isEqualTo(this.tripId.toString());
		assertThat(data.get("acceptPath").asText())
				.isEqualTo("/api/v1/trip-invites/" + data.get("token").asText() + "/accept");

		Instant expiresAt = Instant.parse(data.get("expiresAt").asText());
		Instant expected = before.plus(7, ChronoUnit.DAYS);
		assertThat(Math.abs(ChronoUnit.MINUTES.between(expected, expiresAt))).isLessThanOrEqualTo(1);

		assertThat(inviteRowCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("편집자가 초대를 만들면 403, 비회원이 만들면 404, 표는 하나도 생기지 않는다")
	void nonOwnerCannotIssueInvite() throws Exception {
		UUID editor = StoryFixture.insertUser(this.jdbc, "편집자");
		insertMember(editor, "EDITOR");
		UUID stranger = StoryFixture.insertUser(this.jdbc, "비회원");

		this.mockMvc.perform(post("/api/v1/trips/{tripId}/invites", this.tripId)
						.principal(StoryFixture.as(editor))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"VIEWER\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("TRIP_FORBIDDEN"));

		this.mockMvc.perform(post("/api/v1/trips/{tripId}/invites", this.tripId)
						.principal(StoryFixture.as(stranger))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"VIEWER\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));

		assertThat(inviteRowCount()).isZero();
	}

	@Test
	@DisplayName("OWNER 역할로 초대를 만들면 400 TRIP_INVITE_ROLE_INVALID")
	void ownerRoleIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/trips/{tripId}/invites", this.tripId)
						.principal(StoryFixture.as(this.owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"OWNER\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_INVITE_ROLE_INVALID"));

		assertThat(inviteRowCount()).isZero();
	}

	@Test
	@DisplayName("다른 사용자가 표로 수락하면 200 alreadyMember=false 이고 초대 흔적이 참여자 행에 남는다")
	void anotherUserAcceptsInvite() throws Exception {
		JsonNode invite = createInvite(this.owner, "EDITOR").get("data");
		String token = invite.get("token").asText();
		UUID invitee = StoryFixture.insertUser(this.jdbc, "초대받은 사람");

		JsonNode response = accept(invitee, token);
		JsonNode data = response.get("data");

		assertThat(response.get("error").isNull()).isTrue();
		assertThat(data.get("tripId").asText()).isEqualTo(this.tripId.toString());
		assertThat(data.get("role").asText()).isEqualTo("EDITOR");
		assertThat(data.get("alreadyMember").asBoolean()).isFalse();

		Map<String, Object> row = memberRow(invitee);
		assertThat(row.get("role")).isEqualTo("EDITOR");
		assertThat(row.get("trip_invite_id")).isEqualTo(invite.get("inviteId").asText());
		assertThat(row.get("invited_by")).isEqualTo(this.owner.toString());
		assertThat(row.get("invited_at")).isNotNull();
	}

	@Test
	@DisplayName("VIEWER 초대로 들어온 사람은 role=VIEWER 로 저장된다")
	void viewerInviteStoresViewerRole() throws Exception {
		JsonNode invite = createInvite(this.owner, "VIEWER").get("data");
		String token = invite.get("token").asText();
		UUID invitee = StoryFixture.insertUser(this.jdbc, "열람자");

		accept(invitee, token);

		assertThat(memberRow(invitee).get("role")).isEqualTo("VIEWER");
	}

	@Test
	@DisplayName("만료된 표로 수락하면 410 TRIP_INVITE_EXPIRED 이고 회원 행이 생기지 않는다")
	void expiredInviteIsRejected() throws Exception {
		JsonNode invite = createInvite(this.owner, "EDITOR").get("data");
		String token = invite.get("token").asText();
		// ck_trip_invite_expiry 가 expires_at > created_at 을 요구한다 — expires_at 만 과거로
		// 옮기면 제약에 걸리므로 created_at 도 함께 민다.
		this.jdbc.update("UPDATE trip_invite SET created_at = ?, expires_at = ? WHERE token = ?",
				OffsetDateTime.now().minusDays(8), OffsetDateTime.now().minusDays(1), token);
		UUID invitee = StoryFixture.insertUser(this.jdbc, "늦은 사람");

		this.mockMvc.perform(post("/api/v1/trip-invites/{token}/accept", token).principal(StoryFixture.as(invitee)))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.error.code").value("TRIP_INVITE_EXPIRED"));

		assertThat(memberRowCount(invitee)).isZero();
	}

	@Test
	@DisplayName("같은 표로 두 번 수락하면 두 번째도 200 alreadyMember=true 이고 회원 행은 하나다")
	void acceptingTwiceIsIdempotent() throws Exception {
		JsonNode invite = createInvite(this.owner, "EDITOR").get("data");
		String token = invite.get("token").asText();
		UUID invitee = StoryFixture.insertUser(this.jdbc, "두번 누른 사람");

		JsonNode first = accept(invitee, token).get("data");
		assertThat(first.get("alreadyMember").asBoolean()).isFalse();

		JsonNode second = accept(invitee, token).get("data");
		assertThat(second.get("alreadyMember").asBoolean()).isTrue();
		assertThat(second.get("role").asText()).isEqualTo("EDITOR");

		assertThat(memberRowCount(invitee)).isEqualTo(1);
	}

	@Test
	@DisplayName("소유자가 자기 초대를 누르면 200 alreadyMember=true role=OWNER")
	void ownerAcceptingOwnInviteIsAlreadyMember() throws Exception {
		JsonNode invite = createInvite(this.owner, "EDITOR").get("data");
		String token = invite.get("token").asText();

		JsonNode response = accept(this.owner, token).get("data");

		assertThat(response.get("alreadyMember").asBoolean()).isTrue();
		assertThat(response.get("role").asText()).isEqualTo("OWNER");
		assertThat(memberRowCount(this.owner)).isEqualTo(1);
	}

	@Test
	@DisplayName("없는 표는 404 TRIP_INVITE_NOT_FOUND")
	void unknownTokenIsNotFound() throws Exception {
		UUID stranger = StoryFixture.insertUser(this.jdbc, "낯선 사람");

		this.mockMvc.perform(post("/api/v1/trip-invites/{token}/accept", "no-such-token")
						.principal(StoryFixture.as(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_INVITE_NOT_FOUND"));
	}
}
