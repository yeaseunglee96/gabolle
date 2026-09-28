package com.gabolle.backend.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.share.application.ShareCloneService;
import com.gabolle.backend.share.presentation.ShareCloneController;
import com.gabolle.backend.share.presentation.ShareCloneExceptionHandler;
import com.gabolle.testslice.CollaborationSliceApplication;

/**
 * 공유 일정 복제가 실제 PostgreSQL 에서 「장소만 가져오고 조건은 내 것」 으로 동작하는지 본다.
 * 새 여행의 소유자가 요청자인가, 인원·예산·기간이 내가 넣은 값인가, 원본이 그대로인가,
 * 만료된 공유 주소가 거절되는가, 씨앗이 원본 순서대로 남는가를 함께 확인한다.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ShareCloneIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ShareCloneController controller;

	@Autowired
	private ShareCloneExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID owner;
	private UUID cloner;
	private UUID sourceTripId;
	private UUID itineraryId;
	private UUID placeA;
	private UUID placeB;
	private String token;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller).setControllerAdvice(this.handler).build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.owner = insertUser("원본 주인");
		this.cloner = insertUser("복제하는 사람");
		this.sourceTripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.placeA = insertPlace("A 해운대해수욕장");
		this.placeB = insertPlace("B 광안리해변");

		// 원본: 4인 · 3일 · 예산 90만 — 복제한 사람의 조건과 전부 다르게 둔다.
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, budget_krw, origin_lat, origin_lng, "
						+ "created_at, updated_at) VALUES (?, ?, '2026-09-10', '2026-09-12', 4, 900000, 35.16, 129.16, ?, ?)",
				this.sourceTripId, this.owner, now, now);
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), this.sourceTripId, this.owner, now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, this.sourceTripId, now);
		UUID v1 = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, ?, now())",
				v1, this.itineraryId, this.owner, "req_seed_" + UUID.randomUUID());
		// 둘째 날 B 가 먼저(sequence 1), 첫째 날 A 가 뒤(sequence 2) — 날짜 순이 우선인지 본다.
		insertItem(v1, 1, 2, this.placeB);
		insertItem(v1, 0, 1, this.placeA);

		this.token = insertShareLink(this.sourceTripId, "now()", "now() + interval '30 days'");
	}

	@Test
	@DisplayName("🔴 복제하면 새 여행은 내 조건이고 씨앗은 원본 장소 순서이며 원본은 그대로다")
	void cloneCreatesMyTripWithSeedsAndLeavesSourceUntouched() throws Exception {
		int sourceVersionsBefore = count("SELECT count(*) FROM itinerary_versions WHERE itinerary_id = ?", this.itineraryId);
		int tripsBefore = count("SELECT count(*) FROM trip");

		MvcResult result = this.mockMvc.perform(post("/api/v1/shares/{token}/clone", this.token)
				.principal(as(this.cloner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(1, 100000, List.of())))
				// 제약이 비어 있어 Job 은 못 접수한다 — 여행은 만들어졌으니 201 이다(알려진 한계).
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.created").value(true))
				.andExpect(jsonPath("$.data.jobId").doesNotExist())
				.andExpect(jsonPath("$.data.seedPlaceCount").value(2))
				.andExpect(jsonPath("$.data.sourceTripId").value(this.sourceTripId.toString()))
				.andExpect(jsonPath("$.data.warningCodes[0]").value(ShareCloneService.WARNING_NO_CONSTRAINTS))
				.andReturn();

		UUID newTripId = UUID.fromString(this.json.readTree(result.getResponse().getContentAsString())
				.path("data").path("tripId").asText());
		assertThat(newTripId).isNotEqualTo(this.sourceTripId);

		// 새 여행 — 소유자는 요청자, 조건은 내가 넣은 값
		Map<String, Object> trip = this.jdbc.queryForMap(
				"SELECT owner_user_id, party_size, budget_krw, start_date, end_date FROM trip WHERE trip_id = ?", newTripId);
		assertThat(trip.get("owner_user_id")).isEqualTo(this.cloner);
		assertThat(((Number) trip.get("party_size")).intValue()).isEqualTo(1);
		assertThat(((Number) trip.get("budget_krw")).longValue()).isEqualTo(100000L);
		assertThat(trip.get("start_date").toString()).isEqualTo("2026-10-01");
		assertThat(trip.get("end_date").toString()).isEqualTo("2026-10-02");
		assertThat(count("SELECT count(*) FROM trip_member WHERE trip_id = ? AND user_id = ? AND role = 'OWNER'",
				newTripId, this.cloner)).isEqualTo(1);

		// 씨앗 — 원본 방문 순서(첫째 날 A, 둘째 날 B)로, 출처가 남는다
		List<Map<String, Object>> seeds = this.jdbc.queryForList(
				"SELECT place_id, sequence, source_trip_id FROM trip_seed_place WHERE trip_id = ? ORDER BY sequence", newTripId);
		assertThat(seeds).hasSize(2);
		assertThat(seeds.get(0).get("place_id")).isEqualTo(this.placeA);
		assertThat(seeds.get(1).get("place_id")).isEqualTo(this.placeB);
		assertThat(seeds.get(0).get("source_trip_id")).isEqualTo(this.sourceTripId);

		// 원본은 그대로 — 판이 늘지 않았고 조건도 그대로
		assertThat(count("SELECT count(*) FROM itinerary_versions WHERE itinerary_id = ?", this.itineraryId))
				.isEqualTo(sourceVersionsBefore);
		assertThat(count("SELECT party_size FROM trip WHERE trip_id = ?", this.sourceTripId)).isEqualTo(4);
		assertThat(count("SELECT count(*) FROM trip")).isEqualTo(tripsBefore + 1);
	}

	@Test
	@DisplayName("🔴 제약을 하나라도 답하면 Job 이 접수되고 202 와 jobId 가 온다")
	void withConstraintsJobIsEnqueued() throws Exception {
		String constraint = """
				{ "type": "MOBILITY", "constraintKey": "MAX_WALKING_METERS", "severity": "HARD",
				  "operator": "LTE", "threshold": 5000.0, "answerStatus": "SELECTED" }
				""";
		MvcResult result = this.mockMvc.perform(post("/api/v1/shares/{token}/clone", this.token)
				.principal(as(this.cloner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(2, 300000, List.of(constraint))))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.jobId").exists())
				.andExpect(jsonPath("$.data.pollPath").exists())
				.andExpect(jsonPath("$.data.warningCodes.length()").value(0))
				.andReturn();

		JsonNode data = this.json.readTree(result.getResponse().getContentAsString()).path("data");
		UUID newTripId = UUID.fromString(data.path("tripId").asText());
		UUID jobId = UUID.fromString(data.path("jobId").asText());
		assertThat(data.path("pollPath").asText()).isEqualTo("/api/v1/jobs/" + jobId);
		assertThat(count("SELECT count(*) FROM recommendation_job WHERE job_id = ? AND trip_id = ?", jobId, newTripId))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 만료된 공유 주소로는 복제가 거절되고(410) 여행이 생기지 않는다")
	void expiredLinkIsRejected() throws Exception {
		// CHECK (expires_at > created_at) 를 지키려고 발급 시각도 함께 과거로 둔다 — 31일 전 발급, 1분 전 만료.
		String expired = insertShareLink(this.sourceTripId, "now() - interval '31 days'", "now() - interval '1 minute'");
		int tripsBefore = count("SELECT count(*) FROM trip");

		this.mockMvc.perform(post("/api/v1/shares/{token}/clone", expired)
				.principal(as(this.cloner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(1, 100000, List.of())))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.error.code").value("SHARE_LINK_EXPIRED"));

		assertThat(count("SELECT count(*) FROM trip")).isEqualTo(tripsBefore);
	}

	@Test
	@DisplayName("원본 여행이 지워졌으면 404, 없는 표는 404, 장소가 없는 원본은 422")
	void goneSourceUnknownTokenAndEmptySource() throws Exception {
		this.mockMvc.perform(post("/api/v1/shares/{token}/clone", "no-such-token-" + UUID.randomUUID())
				.principal(as(this.cloner)).contentType(MediaType.APPLICATION_JSON).content(body(1, 100000, List.of())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("SHARE_LINK_NOT_FOUND"));

		// 장소가 없는 원본 — 일정 없는 여행의 공유 주소
		UUID emptyTrip = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 2, ?, ?)",
				emptyTrip, this.owner, now, now);
		String emptyToken = insertShareLink(emptyTrip, "now()", "now() + interval '30 days'");
		this.mockMvc.perform(post("/api/v1/shares/{token}/clone", emptyToken)
				.principal(as(this.cloner)).contentType(MediaType.APPLICATION_JSON).content(body(1, 100000, List.of())))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("SHARED_ITINERARY_EMPTY"));

		// 원본이 지워진 뒤
		this.jdbc.update("UPDATE trip SET deleted_at = now() WHERE trip_id = ?", this.sourceTripId);
		this.mockMvc.perform(post("/api/v1/shares/{token}/clone", this.token)
				.principal(as(this.cloner)).contentType(MediaType.APPLICATION_JSON).content(body(1, 100000, List.of())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("SHARED_TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("본문이 여행 생성 규칙을 어기면 400 TRIP_VALIDATION_FAILED — 종료일이 시작일보다 앞")
	void invalidBodyIs400() throws Exception {
		String bad = """
				{ "startDate": "2026-10-05", "finishDate": "2026-10-01", "partySize": 1,
				  "originLat": 35.1, "originLng": 129.0, "preferences": [], "constraints": [] }
				""";
		this.mockMvc.perform(post("/api/v1/shares/{token}/clone", this.token)
				.principal(as(this.cloner)).contentType(MediaType.APPLICATION_JSON).content(bad))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_VALIDATION_FAILED"));
	}

	// ---- 시드 도우미 ----

	private static String body(int partySize, int budgetKrw, List<String> constraints) {
		return """
				{ "startDate": "2026-10-01", "finishDate": "2026-10-02", "accommodationArea": "HAEUNDAE",
				  "originLat": 35.10, "originLng": 129.04, "budgetKrw": %d, "partySize": %d,
				  "timeWindow": "09:00-18:00", "timezone": "Asia/Seoul",
				  "preferences": [], "constraints": [%s] }
				""".formatted(budgetKrw, partySize, String.join(",", constraints));
	}

	private UUID insertUser(String displayName) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, displayName, now, now);
		return id;
	}

	private UUID insertPlace(String nameKo) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, 35.16, 129.16, now())",
				id, nameKo + " " + UUID.randomUUID());
		return id;
	}

	private void insertItem(UUID versionId, int dayIndex, int sequence, UUID placeId) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, start_time, end_time, stay_minutes, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, DATE '2026-09-10' + ?, ?, ?, '10:00'::time, '12:00'::time, 120, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, UUID.randomUUID(), dayIndex, dayIndex, sequence, placeId);
	}

	private String insertShareLink(UUID tripId, String createdSql, String expiresSql) {
		String token = "t_" + UUID.randomUUID().toString().replace("-", "");
		this.jdbc.update(
				"INSERT INTO trip_share_link (trip_share_link_id, trip_id, token, created_by, created_at, expires_at, view_count) "
						+ "VALUES (?, ?, ?, ?, " + createdSql + ", " + expiresSql + ", 0)",
				UUID.randomUUID(), tripId, token, this.owner);
		return token;
	}

	private int count(String sql, Object... args) {
		Integer n = this.jdbc.queryForObject(sql, Integer.class, args);
		return n == null ? 0 : n;
	}

	private static Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
