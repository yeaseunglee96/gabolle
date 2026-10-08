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
 * 일정 조정(S15P21E201-2017) — 머무는 시간 바꾸기 · 고른 자리에 장소 더하기 · 「순서 고정」/「시각 고정」 이
 * HTTP 를 받아 저장까지 이어지는가.
 *
 * <p>여행은 2026-09-10 하루, 활동 시간 09:00~18:00. 1판 첫날에 A 09:00~12:00 · B 12:30~14:30(사이 30분).
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryAdjustIntegrationTest {

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

	private UUID itineraryId;

	private UUID placeC;

	private UUID keyA;

	private UUID keyB;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		UUID placeA = UUID.randomUUID();
		UUID placeB = UUID.randomUUID();
		this.placeC = UUID.randomUUID();
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();

		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
						+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				this.ownerId, now, now);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, time_window_start, "
						+ "time_window_end, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-10', 1, '09:00', '18:00', ?, ?)",
				tripId, this.ownerId, now, now);
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.ownerId, now);
		for (UUID place : List.of(placeA, placeB, this.placeC)) {
			this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '장소', ?)", place, now);
		}
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', now())",
				versionId, this.itineraryId, this.ownerId);
		insertItem(versionId, this.keyA, 1, placeA, "09:00", "12:00", 180);
		insertItem(versionId, this.keyB, 2, placeB, "12:30", "14:30", 120);
	}

	@Test
	@DisplayName("머무는 시간을 줄이면 그 곳이 짧아지고 뒤 곳이 그만큼 당겨진다 — 사이 30분은 그대로")
	void shorteningStayPullsFollowingItems() throws Exception {
		changeStay(this.keyA, 120, 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.operation").value("REPLAN_DAY"));

		List<Map<String, Object>> items = itemsOf(2);
		assertThat(items).extracting((row) -> row.get("start_time").toString()).containsExactly("09:00:00", "11:30:00");
		assertThat(items).extracting((row) -> row.get("end_time").toString()).containsExactly("11:00:00", "13:30:00");
		assertThat(items).extracting((row) -> row.get("stay_minutes")).containsExactly(120, 120);
	}

	@Test
	@DisplayName("머무는 시간은 15분 단위, 15분 이상이다 — 어기면 400 ITINERARY_STAY_INVALID, 판은 그대로")
	void invalidStayIsRejected() throws Exception {
		changeStay(this.keyA, 20, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_STAY_INVALID"));
		changeStay(this.keyA, 0, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_STAY_INVALID"));
		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 시각 고정한 곳은 앞을 줄여도 제 시각(12:30)에 시작한다 — 응답의 lockMode 는 TIME")
	void timeLockedItemKeepsItsStart() throws Exception {
		lock(this.keyB, "TIME", 1)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.days[0].items[1].lockMode").value("TIME"))
				.andExpect(jsonPath("$.data.days[0].items[1].locked").value(true));

		changeStay(this.keyA, 60, 2).andExpect(status().isOk());

		List<Map<String, Object>> items = itemsOf(3);
		assertThat(items.get(0).get("end_time").toString()).isEqualTo("10:00:00");
		assertThat(items.get(1).get("start_time").toString()).isEqualTo("12:30:00");
		assertThat(items.get(1).get("lock_mode")).isEqualTo("TIME");
	}

	@Test
	@DisplayName("시각 고정한 곳에 못 닿게 앞을 늘리면 밀리고 TIME_FIX_MISSED 경고가 붙는다")
	void timeLockedItemThatCannotBeReachedIsWarned() throws Exception {
		lock(this.keyB, "TIME", 1).andExpect(status().isCreated());

		changeStay(this.keyA, 240, 2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.warnings[*].code").value(hasItem("TIME_FIX_MISSED")));

		assertThat(itemsOf(3).get(1).get("start_time").toString()).isEqualTo("13:30:00");
	}

	@Test
	@DisplayName("하루 끝(18:00)을 넘기면 저장은 되고 DAY_END_EXCEEDED 경고가 붙는다")
	void runningPastDayEndIsWarned() throws Exception {
		changeStay(this.keyB, 360, 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.warnings[*].code").value(hasItem("DAY_END_EXCEEDED")));
		assertThat(itemsOf(2).get(1).get("end_time").toString()).isEqualTo("18:30:00");
	}

	@Test
	@DisplayName("예전처럼 방식 없이 고정하면 순서 고정(ORDER)이다")
	void lockWithoutModeIsOrderLock() throws Exception {
		lock(this.keyA, null, 1)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.days[0].items[0].lockMode").value("ORDER"));
	}

	@Test
	@DisplayName("자리를 골라 더하면 그 자리에 들어가고 시각이 바로 매겨진다 — 뒤 곳은 밀린다")
	void addAtPositionInsertsAndRetimes() throws Exception {
		String body = "{\"placeId\":\"" + this.placeC + "\",\"dayIndex\":0,\"baseVersion\":1,\"position\":1}";
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON).principal(as(this.ownerId)).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.operation").value("ADD_ITEM"));

		List<Map<String, Object>> items = itemsOf(2);
		assertThat(items).extracting((row) -> row.get("place_id")).first().isEqualTo(this.placeC.toString());
		assertThat(items).extracting((row) -> (Integer) row.get("sequence")).containsExactly(1, 2, 3);
		assertThat(items.get(0).get("start_time")).isNotNull();
		assertThat(items.get(0).get("start_time").toString()).isEqualTo("09:00:00");
		// 새 곳이 첫 자리를 차지했으니 A 는 그 뒤로 밀린다.
		assertThat(items.get(1).get("start_time").toString()).isNotEqualTo("09:00:00");
	}

	@Test
	@DisplayName("🔴 그날 구간이 이미 있는 일정에 끼워 넣어도 500 이 아니다 — 옛 구간을 버리고 새 순서로 다시 만든다")
	void addAtPositionReplacesExistingDayLegs() throws Exception {
		// 운영(2026-10-09)에서 uq_itinerary_leg_slot 중복으로 500 이 났다. 시험 여행에 구간이 없어서 못 잡았다.
		this.jdbc.update("UPDATE trip SET origin_lat = 35.158, origin_lng = 129.160");
		this.jdbc.update("UPDATE place SET lat = 35.15, lng = 129.15 WHERE place_id IN (SELECT place_id FROM itinerary_item)");
		this.jdbc.update("UPDATE place SET lat = 35.16, lng = 129.12 WHERE place_id = ?", this.placeC);
		UUID v1 = this.jdbc.queryForObject("SELECT itinerary_version_id FROM itinerary_versions WHERE itinerary_id = ? AND version = 1",
				UUID.class, this.itineraryId);
		for (int sequence = 1; sequence <= 2; sequence++) {
			UUID to = this.jdbc.queryForObject("SELECT place_id FROM itinerary_item WHERE itinerary_version_id = ? AND sequence = ?",
					UUID.class, v1, sequence);
			this.jdbc.update("INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, "
					+ "to_place_id, travel_mode, duration_min, created_at) VALUES (?, ?, 0, ?, ?, 'WALK', 10, now())",
					UUID.randomUUID(), v1, sequence, to);
		}

		String body = "{\"placeId\":\"" + this.placeC + "\",\"dayIndex\":0,\"baseVersion\":1,\"position\":2}";
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON).principal(as(this.ownerId)).content(body))
				.andExpect(status().isCreated());

		List<Integer> legSequences = this.jdbc.queryForList(
				"SELECT l.sequence FROM itinerary_leg l JOIN itinerary_versions v ON v.itinerary_version_id = l.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = 2 AND l.day_index = 0 ORDER BY l.sequence",
				Integer.class, this.itineraryId);
		assertThat(legSequences).doesNotHaveDuplicates();
	}

	// ---- 도우미 ----

	private ResultActions changeStay(UUID itemKey, int stayMinutes, int baseVersion) throws Exception {
		String body = "{\"stayMinutes\":" + stayMinutes + ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/stay", this.itineraryId, itemKey)
				.contentType(MediaType.APPLICATION_JSON).principal(as(this.ownerId)).content(body));
	}

	private ResultActions lock(UUID itemKey, String mode, int baseVersion) throws Exception {
		String body = "{\"baseVersion\":" + baseVersion + ",\"locked\":true"
				+ (mode == null ? "" : ",\"mode\":\"" + mode + "\"") + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, itemKey)
				.contentType(MediaType.APPLICATION_JSON).principal(as(this.ownerId)).content(body));
	}

	private Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private void insertItem(UUID versionId, UUID itemKey, int sequence, UUID placeId, String start, String end,
			int stay) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, '2026-09-10', ?, ?, ?::time, ?::time, ?, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, sequence, placeId, start, end, stay);
	}

	private List<Map<String, Object>> itemsOf(int version) {
		return this.jdbc.queryForList(
				"SELECT i.item_key::text AS item_key, i.place_id::text AS place_id, i.sequence, i.start_time, "
						+ "i.end_time, i.stay_minutes, i.lock_mode FROM itinerary_item i "
						+ "JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? ORDER BY i.day_index, i.sequence",
				this.itineraryId, version);
	}

	private int latestVersion() {
		return this.jdbc.queryForObject("SELECT latest_version FROM itineraries WHERE itinerary_id = ?",
				Integer.class, this.itineraryId);
	}
}
