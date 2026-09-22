package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryActualTimeController;
import com.gabolle.backend.itinerary.presentation.ItineraryActualTimeExceptionHandler;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 방문지 실제 도착·출발 시각. 실제 PostgreSQL 위에서 HTTP 로 본다.
 *
 * <p>"비어 있다" 를 {@code doesNotExist()} 로 재지 않는다. 그러면 칸이 없는 것과 칸이 있고
 * 값이 null 인 것을 구분하지 못해 응답에 칸을 아예 안 만들어도 초록이 된다. 대신
 * {@code value(nullValue())} 로 값이 null 임을 확인하고, 같은 항목의 다른 칸이 채워져
 * 있음도 함께 단정한다.
 *
 * <p>{@link ItineraryAccessIntegrationTest} 와 같은 방식 — standalone MockMvc 에 컨트롤러
 * 빈만 올리고 서비스·저장소는 컨텍스트가 주입한 진짜 구현을 쓴다. 인증 필터 체인만 안 태우고
 * {@code Authentication} 을 직접 준다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryActualTimeIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryActualTimeController actualTimeController;

	@Autowired
	private ItineraryQueryController queryController;

	@Autowired
	private ItineraryActualTimeExceptionHandler actualTimeExceptionHandler;

	@Autowired
	private ItineraryQueryExceptionHandler queryExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID ownerId;
	private UUID viewerId;
	private UUID strangerId;

	private UUID itineraryId;

	/** 첫날(2026-09-01, 지나간 날짜)의 방문지. */
	private UUID visitedItemKey;

	/** 마지막 날의 방문지 — 여기에는 기록을 남기지 않는다. */
	private UUID untouchedItemKey;

	@BeforeEach
	void seedPastTripWithTwoItems() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.actualTimeController, this.queryController)
				.setControllerAdvice(this.actualTimeExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		this.ownerId = UUID.randomUUID();
		this.viewerId = UUID.randomUUID();
		this.strangerId = UUID.randomUUID();

		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		this.visitedItemKey = UUID.randomUUID();
		this.untouchedItemKey = UUID.randomUUID();

		createUser(this.ownerId, now);
		createUser(this.viewerId, now);
		createUser(this.strangerId, now);

		// 여행 기간을 지나간 날짜로 잡는다 — 지나간 날짜의 방문지에도 뒤늦게 기록할 수
		jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-01', '2026-09-10', 1, ?, ?)",
				tripId, this.ownerId, now, now);

		insertMember(tripId, this.ownerId, "OWNER", now);
		insertMember(tripId, this.viewerId, "VIEWER", now);
		// 비회원(strangerId)은 app_user 에는 있지만 trip_member 에는 없다.

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

		insertItem(versionId, this.visitedItemKey, 0, "2026-09-01", placeId, now);
		insertItem(versionId, this.untouchedItemKey, 9, "2026-09-10", placeId, now);
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

	/** 계획 시각을 함께 넣는다 — 기록이 없을 때 계획 시각만 나오는지를 보려면 필요하다. */
	private void insertItem(UUID versionId, UUID itemKey, int dayIndex, String visitDate, UUID placeId,
			OffsetDateTime now) {
		jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, start_time, end_time, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, CAST(? AS date), 1, ?, '10:00', '12:00', FALSE, 'VERIFIED', ?)",
				UUID.randomUUID(), versionId, itemKey, dayIndex, visitDate, placeId, now);
	}

	private Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private int actualRowCount() {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_item_actual WHERE itinerary_id = ?", Integer.class,
				this.itineraryId);
	}

	private int versionRowCount() {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_versions WHERE itinerary_id = ?", Integer.class, this.itineraryId);
	}

	private static String body(String arrivedAt, String departedAt) {
		StringBuilder json = new StringBuilder("{");
		if (arrivedAt != null) {
			json.append("\"arrivedAt\":\"").append(arrivedAt).append("\"");
		}
		if (departedAt != null) {
			if (arrivedAt != null) {
				json.append(",");
			}
			json.append("\"departedAt\":\"").append(departedAt).append("\"");
		}
		return json.append("}").toString();
	}

	@Test
	@DisplayName("도착 시각을 보내면 일정 조회 결과에 실제 도착 시각이 들어 있다")
	void recordedArrivalIsVisibleInItineraryDetail() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T10:30:15+09:00", null)))
				.andExpect(status().isOk())
				// 응답이 조회와 같은 모양이라 화면이 그대로 상태에 넣을 수 있다.
				.andExpect(jsonPath("$.data.days[0].items[0].actualArrivedAt")
						.value("2026-09-01T10:30:15+09:00"));

		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].id").value(this.visitedItemKey.toString()))
				.andExpect(jsonPath("$.data.days[0].items[0].actualArrivedAt")
						.value("2026-09-01T10:30:15+09:00"))
				.andExpect(jsonPath("$.data.days[0].items[0].actualDepartedAt").value(nullValue()));

		assertThat(actualRowCount()).isEqualTo(1);
		// 기록은 판을 만들지 않는다 — 판이 하나 더 생겼다면 편집 경로와 섞인 것이다.
		assertThat(versionRowCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 방문지에 다시 보내면 마지막 값이 남는다 — 행이 늘지 않는다")
	void secondRecordOverwritesTheFirst() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T10:30:15+09:00", null)))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T11:05:20+09:00", "2026-09-01T12:40:30+09:00")))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].actualArrivedAt")
						.value("2026-09-01T11:05:20+09:00"))
				.andExpect(jsonPath("$.data.days[0].items[0].actualDepartedAt")
						.value("2026-09-01T12:40:30+09:00"));

		assertThat(actualRowCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("도착만 다시 보내면 앞서 적은 출발 시각은 지워진다 — 부분 갱신이 아니다")
	void resendingOnlyArrivalClearsDeparture() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T10:00:10+09:00", "2026-09-01T12:00:10+09:00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].actualDepartedAt")
						.value("2026-09-01T12:00:10+09:00"));

		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T10:20:10+09:00", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].actualDepartedAt").value(nullValue()));

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT arrived_at, departed_at FROM itinerary_item_actual WHERE itinerary_id = ? AND item_key = ?",
				this.itineraryId, this.visitedItemKey);
		assertThat(row.get("arrived_at")).isNotNull();
		assertThat(row.get("departed_at")).isNull();
	}

	@Test
	@DisplayName("기록이 없는 방문지는 계획 시각만 나오고 실제 시각 자리가 비어 있다")
	void itemWithoutRecordHasEmptyActualSlots() throws Exception {
		// 같은 일정의 다른 방문지에는 기록을 남긴다 — 아래 null 이 "기능이 아예 안 붙었다" 가
		// 아니라 "이 방문지에만 기록이 없다" 임을 같은 응답 안에서 보이기 위해서다.
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T10:30:15+09:00", null)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(as(this.ownerId)))
				.andExpect(status().isOk())
				// 먼저 응답이 비어 있지 않음을 단정한다 — 빈 목록에 대고 null 을 재면 아무것도
				// 안 본 것이 초록으로 보인다.
				.andExpect(jsonPath("$.data.days.length()").value(10))
				.andExpect(jsonPath("$.data.days[9].items.length()").value(1))
				.andExpect(jsonPath("$.data.days[9].items[0].id").value(this.untouchedItemKey.toString()))
				.andExpect(jsonPath("$.data.days[9].items[0].title").value("해운대해수욕장"))
				// 계획 시각은 그대로 있다. 이 응답의 시각은 초까지 함께 나간다 — 초가 0 이어도 생략되지 않는다.
				.andExpect(jsonPath("$.data.days[9].items[0].startsAt")
						.value("2026-09-10T10:00:00+09:00"))
				// 실제 시각 자리는 키가 있고 값이 null 이다.
				.andExpect(jsonPath("$.data.days[9].items[0].actualArrivedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.days[9].items[0].actualDepartedAt").value(nullValue()))
				// 기록을 남긴 방문지 쪽은 채워져 있다.
				.andExpect(jsonPath("$.data.days[0].items[0].actualArrivedAt")
						.value("2026-09-01T10:30:15+09:00"));

		assertThat(actualRowCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("남의 여행 방문지에 기록을 보내면 거부된다 — 비회원은 404, VIEWER 는 403")
	void strangerGetsNotFoundAndViewerGetsForbidden() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.strangerId))
						.content(body("2026-09-01T10:30:15+09:00", null)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"))
				.andExpect(jsonPath("$.data").doesNotExist());

		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.viewerId))
						.content(body("2026-09-01T10:30:15+09:00", null)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_FORBIDDEN"))
				.andExpect(jsonPath("$.error.fields").value(org.hamcrest.Matchers.hasItem("role=VIEWER")));

		// 둘 다 거부됐으니 기록이 하나도 안 남았다 — 거부가 응답에만 있고 저장은 됐다면 이 줄이 잡는다.
		assertThat(actualRowCount()).isZero();
	}

	@Test
	@DisplayName("지나간 날짜의 방문지에도 뒤늦게 기록할 수 있다")
	void pastVisitCanBeRecordedLate() throws Exception {
		// 씨앗 여행이 2026-09-01~09-10 이고 그 첫날이 이미 지났다는 사실이 이 확인의 전제다.
		assertThat(java.time.LocalDate.of(2026, 9, 1)).isBefore(java.time.LocalDate.now());

		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T09:05:30+09:00", "2026-09-01T11:45:30+09:00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].actualArrivedAt")
						.value("2026-09-01T09:05:30+09:00"))
				.andExpect(jsonPath("$.data.days[0].items[0].actualDepartedAt")
						.value("2026-09-01T11:45:30+09:00"));

		// 적은 시각(updated_at)은 다녀온 시각보다 늦다 — 그것이 뒤늦은 기록의 정의다.
		Boolean recordedLater = jdbcTemplate.queryForObject(
				"SELECT updated_at > arrived_at FROM itinerary_item_actual "
						+ "WHERE itinerary_id = ? AND item_key = ?",
				Boolean.class, this.itineraryId, this.visitedItemKey);
		assertThat(recordedLater).isTrue();
	}

	// ── 값 검증과 대상 확인 ───────────────────────────────────────────────────

	@Test
	@DisplayName("도착도 출발도 없으면 400 INVALID_REQUEST — 어느 칸이 비었는지 알려준다")
	void bothTimesMissingIsBadRequest() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields")
						.value(org.hamcrest.Matchers.hasItem("arrivedAt=null")));

		assertThat(actualRowCount()).isZero();
	}

	@Test
	@DisplayName("출발이 도착보다 앞서면 400 — 표의 CHECK 가 아니라 애플리케이션이 먼저 막는다")
	void departureBeforeArrivalIsBadRequest() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						this.visitedItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T12:00:10+09:00", "2026-09-01T10:00:10+09:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

		assertThat(actualRowCount()).isZero();
	}

	@Test
	@DisplayName("최신 판에 없는 방문지에 보내면 404 ITINERARY_ITEM_NOT_FOUND")
	void unknownItemKeyIsNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/itineraries/{id}/items/{itemKey}/actual", this.itineraryId,
						UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.principal(as(this.ownerId))
						.content(body("2026-09-01T10:30:15+09:00", null)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_ITEM_NOT_FOUND"));

		assertThat(actualRowCount()).isZero();
	}
}
