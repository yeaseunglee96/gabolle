package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.TripActivityController;
import com.gabolle.backend.itinerary.presentation.TripActivityExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.CollaborationSliceApplication;

/**
 * 협업 화면의 "최근 변경" (2026-09-07, 진미리 FE 블로커) — 판이 곧 이력이라는 것을 실제 PostgreSQL 에서 본다.
 *
 * <p>판 셋을 서로 다른 사람이 서로 다른 시각에 만들어 심고, 최신이 먼저 오는지 · 사람 이름이 붙는지 ·
 * {@code isMe} 가 요청자에게만 참인지 · 열람자도 볼 수 있는지 · 비회원은 404 인지를 본다. 판 목록
 * (ITN-02)에 새로 붙은 {@code createdByName} 도 같은 시드로 확인한다.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripActivityIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripActivityController activityController;

	@Autowired
	private TripActivityExceptionHandler activityHandler;

	@Autowired
	private ItineraryQueryController queryController;

	@Autowired
	private ItineraryQueryExceptionHandler queryHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID owner;
	private UUID editor;
	private UUID viewer;
	private UUID outsider;
	private UUID tripId;
	private UUID itineraryId;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.activityController, this.queryController)
				.setControllerAdvice(this.activityHandler, this.queryHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.owner = insertUser("소유자");
		this.editor = insertUser("편집자");
		this.viewer = insertUser("열람자");
		this.outsider = insertUser("외부인");
		this.tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();

		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-11', 2, ?, ?)",
				this.tripId, this.owner, now, now);
		insertMember(this.owner, "OWNER");
		insertMember(this.editor, "EDITOR");
		insertMember(this.viewer, "VIEWER");

		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 3, ?)",
				this.itineraryId, this.tripId, now);
		// 만든 시각을 1초씩 벌려 순서가 우연이 아니게 한다.
		insertVersion(1, null, "CREATE", this.owner, 0);
		insertVersion(2, 1, "LOCK_ITEM", this.editor, 1);
		insertVersion(3, 2, "REGENERATE_DAY", this.owner, 2);
	}

	@Test
	@DisplayName("🔴 열람자가 최근 변경을 부르면 최신 판이 먼저 오고 사람 이름이 붙는다")
	void viewerSeesRecentChangesNewestFirstWithNames() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/activity", this.tripId).principal(as(this.viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tripId").value(this.tripId.toString()))
				.andExpect(jsonPath("$.data.myRole").value("VIEWER"))
				.andExpect(jsonPath("$.data.entries.length()").value(3))
				.andExpect(jsonPath("$.data.entries[0].version").value(3))
				.andExpect(jsonPath("$.data.entries[0].operation").value("REGENERATE_DAY"))
				.andExpect(jsonPath("$.data.entries[0].actorId").value(this.owner.toString()))
				.andExpect(jsonPath("$.data.entries[0].actorName").value("소유자"))
				.andExpect(jsonPath("$.data.entries[0].isMe").value(false))
				.andExpect(jsonPath("$.data.entries[1].version").value(2))
				.andExpect(jsonPath("$.data.entries[1].actorName").value("편집자"))
				.andExpect(jsonPath("$.data.entries[1].baseVersion").value(1))
				.andExpect(jsonPath("$.data.entries[2].version").value(1))
				.andExpect(jsonPath("$.data.entries[2].itineraryId").value(this.itineraryId.toString()));
	}

	@Test
	@DisplayName("isMe 는 요청자 자신의 변경에만 참이고 limit 이 개수를 자른다")
	void isMeAndLimit() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/activity", this.tripId).param("limit", "2")
				.principal(as(this.editor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.limit").value(2))
				.andExpect(jsonPath("$.data.entries.length()").value(2))
				.andExpect(jsonPath("$.data.entries[0].isMe").value(false))
				.andExpect(jsonPath("$.data.entries[1].isMe").value(true));
	}

	@Test
	@DisplayName("🔴 비회원은 404 TRIP_NOT_FOUND — 여행이 있다는 사실을 알려주지 않는다")
	void outsiderGets404() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/activity", this.tripId).principal(as(this.outsider)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("limit 범위 밖은 400 — 조용히 잘라 주지 않는다")
	void limitOutOfRangeIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/activity", this.tripId).param("limit", "0")
				.principal(as(this.owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_ACTIVITY_INVALID_REQUEST"));
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/activity", this.tripId).param("limit", "101")
				.principal(as(this.owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("판 목록(ITN-02)에도 createdByName 이 붙는다 — 되돌리기 화면이 '누가' 를 그릴 수 있다")
	void versionListingCarriesNames() throws Exception {
		this.mockMvc.perform(get("/api/v1/itineraries/{id}/versions", this.itineraryId).principal(as(this.viewer)))
				.andExpect(status().isOk())
				// 🔴 S15P21E201-1011 — 응답이 배열에서 봉투로 바뀌었다. 목록은 data.items 다.
				.andExpect(jsonPath("$.data.items[0].version").value(3))
				.andExpect(jsonPath("$.data.items[0].createdByName").value("소유자"))
				.andExpect(jsonPath("$.data.items[1].createdByName").value("편집자"));
	}

	// ---- 시드 도우미 ----

	private UUID insertUser(String displayName) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, displayName, now, now);
		return id;
	}

	private void insertMember(UUID userId, String role) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, now())",
				UUID.randomUUID(), this.tripId, userId, role);
	}

	private void insertVersion(int version, Integer baseVersion, String operation, UUID createdBy, int secondsLater) {
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))",
				UUID.randomUUID(), this.itineraryId, version, baseVersion, operation, createdBy,
				"req_seed_" + version + "_" + UUID.randomUUID(), secondsLater);
	}

	private static Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
