package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryEditController;
import com.gabolle.backend.itinerary.presentation.ItineraryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 하루 안의 방문 순서 바꾸기. {@code POST .../days/{dayIndex}/reorder} 가
 * HTTP 를 받아서 저장까지 이어지는가만 본다.
 *
 * <p>규칙 자체(시각 자리 바꾸기 · 구간 버리기 · 고정 판정)는 {@link ItineraryReorderRevisionTest}
 * 가 DB 없이 이미 잰다. 여기서 다시 재지 않는다.
 *
 * <p>{@link ItineraryAddItemIntegrationTest} 와 같은 방식이다 — standalone MockMvc 에
 * 편집·조회 컨트롤러 빈만 올리고 서비스·저장소는 컨텍스트가 주입한 진짜 구현을 쓴다.
 * 권한·존재 판정의 403/404 구분은 {@link ItineraryAccessIntegrationTest} 를 그대로 따른다.
 *
 * <p>여행은 2026-09-10 부터 09-12 까지 사흘(dayIndex 0·1·2)이다. 1판에 첫날(dayIndex 0)
 * 항목 둘이 있다 — {@code keyA}(순번1) · {@code keyB}(순번2).
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryReorderIntegrationTest {

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
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID viewerId;

	private UUID strangerId;

	private UUID itineraryId;

	private UUID placeA;

	private UUID placeB;

	private UUID keyA;

	private UUID keyB;

	@BeforeEach
	void seedVersionOneWithTwoItemsOnDayZero() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		this.viewerId = UUID.randomUUID();
		this.strangerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.placeA = UUID.randomUUID();
		this.placeB = UUID.randomUUID();
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();

		createUser(this.ownerId, now);
		createUser(this.viewerId, now);
		createUser(this.strangerId, now);
		// strangerId 는 app_user 에는 있지만 trip_member 에는 없다 — 가입은 했지만 이
		// 여행의 회원은 아닌 상태를 재현한다.

		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				tripId, this.ownerId, now, now);
		insertMember(tripId, this.ownerId, "OWNER", now);
		insertMember(tripId, this.viewerId, "VIEWER", now);

		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'A 해운대해수욕장', ?)",
				this.placeA, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'B 광안리해변', ?)",
				this.placeB, now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID v1 = insertVersion(1, null, "CREATE");
		insertItem(v1, this.keyA, 0, 1, this.placeA, "09:00", "12:00");
		insertItem(v1, this.keyB, 0, 2, this.placeB, "12:00", "18:00");
	}

	@Test
	@DisplayName("편집 권한자가 순서를 바꾸면 200 이고 판 번호가 오르며, 다시 조회하면 바뀐 순서가 남아 있다")
	void reorderSucceedsBumpsVersionAndPersists() throws Exception {
		reorderDay(0, List.of(this.keyB.toString(), this.keyA.toString()), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.operation").value("REORDER"));

		// 응답만 보고 끝내지 않고 판을 다시 읽는다 — 저장까지 갔는지가 핵심이다.
		List<Map<String, Object>> items = itemsOf(2);
		assertThat(items).extracting((row) -> row.get("item_key"))
				.containsExactly(this.keyB.toString(), this.keyA.toString());
		assertThat(items).extracting((row) -> (Integer) row.get("sequence")).containsExactly(1, 2);
	}

	@Test
	@DisplayName("낡은 baseVersion 으로 보내면 409 다 — 다른 편집이 먼저 반영된 뒤의 요청이다")
	void staleBaseVersionIsRejected() throws Exception {
		// 먼저 한 번 바꿔서 최신을 2판으로 만든다.
		reorderDay(0, List.of(this.keyB.toString(), this.keyA.toString()), 1).andExpect(status().isOk());

		// 여전히 1판을 바탕으로 보내는 다른 편집 — 낡았다.
		reorderDay(0, List.of(this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ItineraryExceptionHandler.CONFLICT_CODE));

		assertThat(latestVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("그날 항목을 하나 빠뜨리면 400 ITINERARY_DAY_ORDER_MISMATCH 다 — 판은 그대로다")
	void missingDayItemIsRejected() throws Exception {
		// keyB 를 빠뜨렸다 — 그날 항목 전부가 아니다.
		reorderDay(0, List.of(this.keyA.toString()), 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_DAY_ORDER_MISMATCH"));

		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 고정된 항목을 옮기려 하면 409 ITINERARY_LOCKED_ITEM_MOVED 다 — 고정은 실제 고정 경로로 건다")
	void movingALockedItemIsRejected() throws Exception {
		// 표에 locked=true 를 직접 심지 않고 실제 고정 경로(POST /items/{itemId}/lock)를
		// 써서 배선까지 함께 본다. 이제 최신은 2판이고 keyB 가 고정돼 있다.
		lockItem(this.keyB, true, 1).andExpect(status().isCreated());

		// keyB 를 첫 자리로 옮기려는 요청 — 고정된 자리를 건드린다.
		reorderDay(0, List.of(this.keyB.toString(), this.keyA.toString()), 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_LOCKED_ITEM_MOVED"))
				.andExpect(jsonPath("$.error.fields").value(hasItem("itemKey=" + this.keyB)));

		assertThat(latestVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 VIEWER 는 403, 비회원은 404 다 — 없는 일정과 남의 일정이 같은 답이어야 한다")
	void viewerIsForbiddenAndNonMembersAllGetTheSameNotFound() throws Exception {
		reorderDay(this.itineraryId, this.viewerId, 0,
				List.of(this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_FORBIDDEN"))
				.andExpect(jsonPath("$.error.fields").value(hasItem("role=VIEWER")));

		// 회원이긴 하지만 이 여행의 trip_member 가 아니다 — 존재를 감춘 404.
		reorderDay(this.itineraryId, this.strangerId, 0,
				List.of(this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));

		// 아예 없는 일정 id 도 같은 코드다 — "있는데 접근 못 함" 과 "없음" 이 구분되면 안 된다.
		reorderDay(UUID.randomUUID(), this.ownerId, 0,
				List.of(this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));

		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("여행 기간을 벗어난 dayIndex 는 400 ITINERARY_DAY_OUTSIDE_TRIP 이다")
	void dayOutsideTripIsRejected() throws Exception {
		// 여행은 사흘(dayIndex 0·1·2)이다. 3 은 마지막 날 다음이다.
		reorderDay(3, List.of(this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_DAY_OUTSIDE_TRIP"));

		assertThat(latestVersion()).isEqualTo(1);
	}

	// ---- 도우미 ----

	private ResultActions reorderDay(int dayIndex, List<String> itemKeys, int baseVersion) throws Exception {
		return reorderDay(this.itineraryId, this.ownerId, dayIndex, itemKeys, baseVersion);
	}

	private ResultActions reorderDay(UUID targetItineraryId, UUID actingUserId, int dayIndex,
			List<String> itemKeys, int baseVersion) throws Exception {
		String body = "{\"itemKeys\":" + toJsonArray(itemKeys) + ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/days/{dayIndex}/reorder",
				targetItineraryId, dayIndex)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(actingUserId))
				.content(body));
	}

	private ResultActions lockItem(UUID itemKey, boolean locked, int baseVersion) throws Exception {
		String body = "{\"baseVersion\":" + baseVersion + ",\"locked\":" + locked + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock",
				this.itineraryId, itemKey)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(this.ownerId))
				.content(body));
	}

	private static String toJsonArray(List<String> values) {
		return values.stream().map((v) -> "\"" + v + "\"").collect(Collectors.joining(",", "[", "]"));
	}

	private Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private void createUser(UUID userId, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
						+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				userId, now, now);
	}

	private void insertMember(UUID tripId, UUID userId, String role, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, now);
	}

	private UUID insertVersion(int version, Integer baseVersion, String operation) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now())",
				versionId, this.itineraryId, version, baseVersion, operation, this.ownerId,
				"req_seed_" + version);
		this.jdbc.update("UPDATE itineraries SET latest_version = ? WHERE itinerary_id = ?", version,
				this.itineraryId);
		return versionId;
	}

	private void insertItem(UUID versionId, UUID itemKey, int dayIndex, int sequence, UUID placeId,
			String start, String end) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, '2026-09-10', ?, ?, ?::time, ?::time, 180, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, dayIndex, sequence, placeId, start, end);
	}

	private List<Map<String, Object>> itemsOf(int version) {
		return this.jdbc.queryForList(
				"SELECT i.item_key::text AS item_key, i.place_id::text AS place_id, i.sequence, i.day_index, "
						+ "i.locked FROM itinerary_item i "
						+ "JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? ORDER BY i.day_index, i.sequence",
				this.itineraryId, version);
	}

	private int latestVersion() {
		return this.jdbc.queryForObject(
				"SELECT latest_version FROM itineraries WHERE itinerary_id = ?", Integer.class,
				this.itineraryId);
	}
}
