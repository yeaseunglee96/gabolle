package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** 참여자 목록·역할 변경·제거를 실제 PostgreSQL 위에서 본다. */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripMemberManagementIntegrationTest {

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
		insertMember(this.owner, "OWNER", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
	}

	// ---- 시드 도우미 ----

	private void insertMember(UUID userId, String role, OffsetDateTime joinedAt) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), this.tripId, userId, role, joinedAt);
	}

	// ---- 호출 도우미 ----

	private JsonNode listMembers(UUID as) throws Exception {
		MvcResult result = this.mockMvc
				.perform(get("/api/v1/trips/{tripId}/members", this.tripId).principal(StoryFixture.as(as)))
				.andReturn();
		return this.json.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode changeRole(UUID as, UUID target, String role) throws Exception {
		MvcResult result = this.mockMvc.perform(patch("/api/v1/trips/{tripId}/members/{userId}", this.tripId, target)
						.principal(StoryFixture.as(as))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"" + role + "\"}"))
				.andReturn();
		return this.json.readTree(result.getResponse().getContentAsString());
	}

	// ---- 조회 도우미 ----

	private String roleOf(UUID userId) {
		return this.jdbc.queryForObject("SELECT role FROM trip_member WHERE trip_id = ? AND user_id = ?",
				String.class, this.tripId, userId);
	}

	private int memberRowCount(UUID userId) {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM trip_member WHERE trip_id = ? AND user_id = ?",
				Integer.class, this.tripId, userId);
		return count == null ? 0 : count;
	}

	// ---- 테스트 ----

	@Test
	@DisplayName("소유자·편집자·열람자 셋을 조회하면 셋이 나오고 표시 이름이 같고 isMe·정렬이 맞다")
	void listReturnsAllMembersSortedWithOwnerFirst() throws Exception {
		UUID editor = StoryFixture.insertUser(this.jdbc, "편집자");
		UUID viewer = StoryFixture.insertUser(this.jdbc, "열람자");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		insertMember(editor, "EDITOR", now.minusMinutes(5));
		insertMember(viewer, "VIEWER", now.minusMinutes(1));

		JsonNode data = listMembers(editor).get("data");

		assertThat(data.get("members")).hasSize(3);
		assertThat(data.get("members").get(0).get("role").asText()).isEqualTo("OWNER");
		assertThat(data.get("members").get(0).get("userId").asText()).isEqualTo(this.owner.toString());
		assertThat(data.get("members").get(1).get("userId").asText()).isEqualTo(editor.toString());
		assertThat(data.get("members").get(2).get("userId").asText()).isEqualTo(viewer.toString());
		assertThat(data.get("members").get(1).get("displayName").asText()).isEqualTo("편집자");
		assertThat(data.get("members").get(1).get("isMe").asBoolean()).isTrue();
		assertThat(data.get("members").get(0).get("isMe").asBoolean()).isFalse();
		assertThat(data.get("members").get(2).get("isMe").asBoolean()).isFalse();
		assertThat(data.get("myRole").asText()).isEqualTo("EDITOR");
		assertThat(data.get("canEdit").asBoolean()).isTrue();
	}

	@Test
	@DisplayName("비회원이 목록을 부르면 404")
	void nonMemberCannotListMembers() throws Exception {
		UUID stranger = StoryFixture.insertUser(this.jdbc, "비회원");

		this.mockMvc.perform(get("/api/v1/trips/{tripId}/members", this.tripId).principal(StoryFixture.as(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("소유자가 편집자를 열람자로 바꾸면 200 이고 DB 도 VIEWER, 편집자가 남을 바꾸려 하면 403 이고 DB 는 그대로")
	void onlyOwnerCanChangeRole() throws Exception {
		UUID editor = StoryFixture.insertUser(this.jdbc, "편집자");
		insertMember(editor, "EDITOR", OffsetDateTime.now(ZoneOffset.UTC));

		JsonNode response = changeRole(this.owner, editor, "VIEWER");
		assertThat(response.get("data").get("role").asText()).isEqualTo("VIEWER");
		assertThat(roleOf(editor)).isEqualTo("VIEWER");

		UUID another = StoryFixture.insertUser(this.jdbc, "다른 편집자");
		insertMember(another, "EDITOR", OffsetDateTime.now(ZoneOffset.UTC));

		this.mockMvc.perform(patch("/api/v1/trips/{tripId}/members/{userId}", this.tripId, another)
						.principal(StoryFixture.as(editor))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"VIEWER\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("TRIP_FORBIDDEN"));
		assertThat(roleOf(another)).isEqualTo("EDITOR");
	}

	@Test
	@DisplayName("소유자 역할을 바꾸려 하면 400, 회원 아닌 대상은 404")
	void changeRoleRejectsOwnerTargetAndUnknownTarget() throws Exception {
		this.mockMvc.perform(patch("/api/v1/trips/{tripId}/members/{userId}", this.tripId, this.owner)
						.principal(StoryFixture.as(this.owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"EDITOR\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_MEMBER_ROLE_INVALID"));

		UUID stranger = StoryFixture.insertUser(this.jdbc, "회원 아님");
		this.mockMvc.perform(patch("/api/v1/trips/{tripId}/members/{userId}", this.tripId, stranger)
						.principal(StoryFixture.as(this.owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"EDITOR\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_MEMBER_NOT_FOUND"));
	}

	@Test
	@DisplayName("소유자가 참여자를 빼면 204 이고 행이 없다. 빠진 사람이 목록을 부르면 404. 소유자를 빼려 하면 400")
	void ownerCanRemoveMemberButNotSelf() throws Exception {
		UUID viewer = StoryFixture.insertUser(this.jdbc, "열람자");
		insertMember(viewer, "VIEWER", OffsetDateTime.now(ZoneOffset.UTC));

		this.mockMvc.perform(delete("/api/v1/trips/{tripId}/members/{userId}", this.tripId, viewer)
						.principal(StoryFixture.as(this.owner)))
				.andExpect(status().isNoContent());
		assertThat(memberRowCount(viewer)).isZero();

		this.mockMvc.perform(get("/api/v1/trips/{tripId}/members", this.tripId).principal(StoryFixture.as(viewer)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));

		this.mockMvc.perform(delete("/api/v1/trips/{tripId}/members/{userId}", this.tripId, this.owner)
						.principal(StoryFixture.as(this.owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_MEMBER_ROLE_INVALID"));
		assertThat(memberRowCount(this.owner)).isEqualTo(1);
	}
}
