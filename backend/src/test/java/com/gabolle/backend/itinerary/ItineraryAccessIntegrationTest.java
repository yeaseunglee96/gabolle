package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryEditController;
import com.gabolle.backend.itinerary.presentation.ItineraryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 일정 접근 권한 판정. {@code trip_member.role} 이 조회·편집에서 서로 다른 문턱으로
 * 쓰이는가를 실제 PostgreSQL 위에서 확인한다. 특히 비회원의 404 와 VIEWER 의 403 을
 * 뭉뚱그리지 않는지를 한 메서드에서 나란히 단정한다
 * ({@link #viewerEditIsForbiddenButStrangerEditIsNotFound()}).
 *
 * <p>{@link ItineraryLockPersistenceIntegrationTest} 와 같은 방식 — standalone MockMvc 에
 * 컨트롤러 빈만 올리고 서비스·저장소는 컨텍스트가 주입한 진짜 구현을 쓴다. 인증 필터
 * 체인만 안 태우고 {@code Authentication} 을 직접 준다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryAccessIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryEditController editController;

	@Autowired
	private ItineraryQueryController queryController;

	@Autowired
	private ItineraryExceptionHandler editExceptionHandler;

	@Autowired
	private ItineraryQueryExceptionHandler queryExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID ownerId;
	private UUID editorId;
	private UUID viewerId;
	private UUID strangerId;

	private UUID itineraryId;
	private UUID itemKey;

	@BeforeEach
	void seedTripWithThreeRolesAndOneItem() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		this.ownerId = UUID.randomUUID();
		this.editorId = UUID.randomUUID();
		this.viewerId = UUID.randomUUID();
		this.strangerId = UUID.randomUUID();

		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		this.itemKey = UUID.randomUUID();

		createUser(this.ownerId, now);
		createUser(this.editorId, now);
		createUser(this.viewerId, now);
		createUser(this.strangerId, now);
		// 비회원(strangerId)은 app_user 에는 있지만 trip_member 에는 없다 — 가입은 했지만
		// 이 여행의 회원은 아닌 상태를 재현한다.

		jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				tripId, this.ownerId, now, now);

		insertMember(tripId, this.ownerId, "OWNER", now);
		insertMember(tripId, this.editorId, "EDITOR", now);
		insertMember(tripId, this.viewerId, "VIEWER", now);

		jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);
		jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				versionId, this.itineraryId, this.ownerId, now);

		UUID placeId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, ?, ?)",
				placeId, "해운대해수욕장", now);
		jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, '2026-09-10', 1, ?, FALSE, 'UNKNOWN', ?)",
				UUID.randomUUID(), versionId, this.itemKey, placeId, now);
	}

	private void createUser(UUID userId, OffsetDateTime now) {
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				userId, now, now);
	}

	private void insertMember(UUID tripId, UUID userId, String role, OffsetDateTime now) {
		jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, now);
	}

	private Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private int latestVersionInDatabase() {
		return jdbcTemplate.queryForObject(
				"SELECT latest_version FROM itineraries WHERE itinerary_id = ?", Integer.class, this.itineraryId);
	}

	private int versionRowCount() {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_versions WHERE itinerary_id = ?", Integer.class, this.itineraryId);
	}

	@Test
	@DisplayName("OWNER 가 고정하면 201 이고 myRole=OWNER · canEdit=true")
	void ownerCanLockAndSeesOwnerRole() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.myRole").value("OWNER"))
				.andExpect(jsonPath("$.data.canEdit").value(true));
	}

	@Test
	@DisplayName("EDITOR 가 고정하면 201 이고 myRole=EDITOR · canEdit=true")
	void editorCanLockAndSeesEditorRole() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.editorId))
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.myRole").value("EDITOR"))
				.andExpect(jsonPath("$.data.canEdit").value(true));
	}

	@Test
	@DisplayName("VIEWER 는 조회할 수 있다 — myRole=VIEWER · canEdit=false")
	void viewerCanReadButCannotEdit() throws Exception {
		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(as(this.viewerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myRole").value("VIEWER"))
				.andExpect(jsonPath("$.data.canEdit").value(false));
	}

	@Test
	@DisplayName("VIEWER 가 고정을 시도하면 403 ITINERARY_FORBIDDEN — 일정은 바뀌지 않는다")
	void viewerLockAttemptIsForbiddenAndDoesNotChangeItinerary() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.viewerId))
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_FORBIDDEN"))
				.andExpect(jsonPath("$.error.fields").value(org.hamcrest.Matchers.hasItem("role=VIEWER")));

		// 거부되고 일정이 바뀌지 않는다 — 새 판이 안 생겼다.
		assertThat(latestVersionInDatabase()).isEqualTo(1);
		assertThat(versionRowCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("비회원은 조회하면 404 ITINERARY_NOT_FOUND 이고 data 가 안 내려온다")
	void strangerGetIsNotFoundAndCarriesNoData() throws Exception {
		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(as(this.strangerId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	/**
	 * VIEWER 의 편집 거부(403)와 비회원의 편집 거부(404)를 한 메서드에서 나란히 단정한다.
	 * 둘을 같은 404 로 묶는 구현을 이 단정이 막는다.
	 */
	@Test
	@DisplayName("VIEWER 편집은 403, 비회원 편집은 404 — 서로 다른 코드다")
	void viewerEditIsForbiddenButStrangerEditIsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.viewerId))
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_FORBIDDEN"));

		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.strangerId))
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));

		// 둘 다 거부됐으니 판은 그대로 1개다.
		assertThat(versionRowCount()).isEqualTo(1);
	}
}
