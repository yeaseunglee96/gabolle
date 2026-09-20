package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
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
 * 사용자가 고른 장소(축제)를 일정에 더한다.
 *
 * <p>여기서 고정하는 계약은 이렇다.
 *
 * <ol>
 *   <li>더한 항목이 그 날의 마지막 순번으로 들어간다. 다른 날의 순번을 세지 않는다</li>
 *   <li>더한 항목은 고정(locked)돼 있다 — 뒤따르는 재계산이 그 날을 다시 채우면서 방금
 *       고른 장소를 빼면 안 된다</li>
 *   <li>시각을 지어내지 않는다. 시작·종료 시각이 비어 있고 {@code data_status} 가
 *       {@code UNKNOWN} 이다</li>
 *   <li>기존 항목의 {@code item_key} 가 판을 건너 그대로 살아남는다</li>
 *   <li>여행 기간을 벗어난 날은 400 이다. 표의 CHECK 는 이것을 막지 못한다</li>
 *   <li>기간이 정해진 장소는 그 날 실제로 열리는지 보고, 같은 축제를 두 번 넣으면 두
 *       번째가 거부된다. 기간 행이 없는 일반 장소에는 안 걸린다</li>
 * </ol>
 *
 * <p>여행은 2026-09-10 부터 09-12 까지 사흘이다. 1판에 첫날(dayIndex 0) 항목 둘이 있다.
 * {@code festival} 은 기본적으로 기간 행이 없는 장소이고, 기간이 필요한 시험은
 * {@link #insertEventPeriod} 로 그 시험 안에서 직접 심는다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryAddItemIntegrationTest {

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

	private UUID userId;

	private UUID itineraryId;

	private UUID placeA;

	private UUID placeB;

	private UUID festival;

	private UUID keyA;

	private UUID keyB;

	@BeforeEach
	void seedVersionOne() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.userId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.placeA = UUID.randomUUID();
		this.placeB = UUID.randomUUID();
		this.festival = UUID.randomUUID();
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();

		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
						+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				this.userId, now, now);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				tripId, this.userId, now, now);
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) "
						+ "VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.userId, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'A 해운대해수욕장', ?)",
				this.placeA, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'B 광안리해변', ?)",
				this.placeB, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '진주 남강유등축제', ?)",
				this.festival, now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID v1 = insertVersion(1, null, "CREATE");
		insertItem(v1, this.keyA, 0, 1, this.placeA, "09:00", "12:00");
		insertItem(v1, this.keyB, 0, 2, this.placeB, "12:00", "18:00");
	}

	@Test
	@DisplayName("🔴 더한 축제가 그 날 마지막 순번에 고정된 채로 들어간다 — 시각은 비어 있다")
	void addedItemGoesLastLockedAndWithoutTimes() throws Exception {
		addItem(this.festival, 0, 1)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.operation").value("ADD_ITEM"));

		List<Map<String, Object>> items = itemsOf(2);
		assertThat(items).hasSize(3);

		Map<String, Object> added = items.get(2);
		assertThat(added.get("place_id")).isEqualTo(this.festival.toString());
		assertThat(added.get("sequence")).isEqualTo(3);
		// 이 두 줄이 이 테스트의 핵심이다 — 고정돼 있고 시각이 비어 있다.
		assertThat(added.get("locked")).isEqualTo(true);
		assertThat(added.get("start_time")).isNull();
		assertThat(added.get("end_time")).isNull();
		assertThat(added.get("stay_minutes")).isNull();
		assertThat(added.get("data_status")).isEqualTo("UNKNOWN");
		assertThat(added.get("visit_date")).hasToString("2026-09-10");
		assertThat((String) added.get("reason_codes")).contains("USER_ADDED");
	}

	@Test
	@DisplayName("기존 항목의 item_key 가 새 판에서도 그대로다 — 화면이 가리키던 이름이 살아남는다")
	void existingItemKeysSurvive() throws Exception {
		addItem(this.festival, 0, 1).andExpect(status().isCreated());

		List<String> keys = itemsOf(2).stream().map((row) -> (String) row.get("item_key")).toList();
		assertThat(keys).startsWith(this.keyA.toString(), this.keyB.toString());
		// 더한 항목은 새 item_key 를 받는다 — 기존 것과 겹치지 않는다
		assertThat(keys.get(2)).isNotIn(this.keyA.toString(), this.keyB.toString());
	}

	@Test
	@DisplayName("항목이 하나도 없는 날에 넣으면 순번 1 로 들어간다 — 다른 날의 순번을 세지 않는다")
	void emptyDayStartsAtSequenceOne() throws Exception {
		addItem(this.festival, 2, 1).andExpect(status().isCreated());

		Map<String, Object> added = itemsOf(2).stream()
				.filter((row) -> this.festival.toString().equals(row.get("place_id")))
				.findFirst()
				.orElseThrow();

		assertThat(added.get("sequence")).isEqualTo(1);
		assertThat(added.get("day_index")).isEqualTo(2);
		assertThat(added.get("visit_date")).hasToString("2026-09-12");
	}

	@Test
	@DisplayName("🔴 여행 기간을 벗어난 날은 400 이다 — 어느 날에도 안 보이는 항목을 저장하지 않는다")
	void dayOutsideTripIsRejected() throws Exception {
		// 여행은 사흘(dayIndex 0·1·2)이다. 3 은 마지막 날 다음이다
		addItem(this.festival, 3, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_DAY_OUTSIDE_TRIP"));

		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("바탕 판이 낡았으면 409 다 — 새 판을 만들지 않는다")
	void staleBaseVersionIsRejected() throws Exception {
		addItem(this.festival, 0, 1).andExpect(status().isCreated());

		// 이제 최신은 2판. 1판을 바탕으로 또 더하려 하면 낡은 것이다
		addItem(this.placeA, 0, 1).andExpect(status().isConflict());

		assertThat(latestVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("본문에서 dayIndex 를 빼면 검증 오류다 — 판은 만들어지지 않는다")
	void missingDayIndexIsAValidationError() throws Exception {
		String body = "{\"placeId\":\"" + this.festival + "\",\"baseVersion\":1}";
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asOwner())
				.content(body))
				.andExpect(status().isBadRequest());

		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("음수 dayIndex 는 400 이다")
	void negativeDayIndexIsRejected() throws Exception {
		addItem(this.festival, -1, 1).andExpect(status().isBadRequest());
		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("기간이 있는 축제를 열리는 날에 넣으면 들어간다")
	void festivalIsAddedOnAnOpenDay() throws Exception {
		// 이 축제는 여행 둘째·셋째 날에만 열린다
		insertEventPeriod(this.festival, "2026-09-11", "2026-09-12");

		addItem(this.festival, 1, 1).andExpect(status().isCreated());

		List<Map<String, Object>> items = itemsOf(2);
		assertThat(items).hasSize(3);

		Map<String, Object> added = festivalItemsOf(2).get(0);
		assertThat(added.get("day_index")).isEqualTo(1);
		assertThat(added.get("visit_date")).hasToString("2026-09-11");
	}

	@Test
	@DisplayName("🔴 그 날에는 안 열리면 400 이고, 넣을 수 있는 날들이 응답에 담긴다")
	void closedOnRequestedDayAnswersWithTheOpenDays() throws Exception {
		insertEventPeriod(this.festival, "2026-09-11", "2026-09-12");

		// 첫날(2026-09-10)에는 안 열린다. 다른 날을 고르면 되는 요청이다
		addItem(this.festival, 0, 1)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_PLACE_CLOSED_ON_DAY"))
				// 목록의 내용을 보기 전에 목록이 비어 있지 않음을 먼저 단정한다 — 비어 있어도
				// hasItem 없이 통과하는 단정을 쓰면 이 시험이 공허하게 초록이 된다
				.andExpect(jsonPath("$.error.fields").isNotEmpty())
				.andExpect(jsonPath("$.error.fields").value(hasItem("requestedDayIndex=0")))
				.andExpect(jsonPath("$.error.fields").value(hasItem("requestedDate=2026-09-10")))
				.andExpect(jsonPath("$.error.fields").value(hasItem("availableDayIndexes=1,2")))
				.andExpect(jsonPath("$.error.fields").value(hasItem("availableDates=2026-09-11,2026-09-12")))
				// 실패 봉투에는 data 가 없다. 키가 아예 빠진 것과 값이 null 인 것을 구분하려고
				// nullValue() 로 보고, 같은 응답의 다른 칸이 채워져 있음을 함께 본다
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.meta.requestId").isNotEmpty());

		assertThat(latestVersion()).isEqualTo(1);
		assertThat(festivalItemsOf(1)).isEmpty();
	}

	@Test
	@DisplayName("여행 기간과 전혀 겹치지 않는 축제는 다른 코드로 거부된다 — 422")
	void festivalOutsideTheTripIsRejectedWithADifferentCode() throws Exception {
		// 10월 축제, 여행은 9월이다. 어느 날을 골라도 안 된다
		insertEventPeriod(this.festival, "2026-10-01", "2026-10-05");

		addItem(this.festival, 0, 1)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_PLACE_NOT_OPEN_DURING_TRIP"))
				.andExpect(jsonPath("$.error.fields").isNotEmpty())
				.andExpect(jsonPath("$.error.fields").value(hasItem("tripStartDate=2026-09-10")))
				.andExpect(jsonPath("$.error.fields").value(hasItem("tripFinishDate=2026-09-12")));

		// 다른 날을 골라도 같은 판정이어야 한다 — "고칠 수 있는 요청" 이 아니다
		addItem(this.festival, 2, 1)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_PLACE_NOT_OPEN_DURING_TRIP"));

		assertThat(latestVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 축제를 두 번 넣으면 두 번째가 409 이고, 일정에는 하나만 남는다")
	void addingTheSameFestivalTwiceIsRejected() throws Exception {
		// 여행 내내 열리는 축제. 날짜 검사에는 걸리지 않는다
		insertEventPeriod(this.festival, "2026-09-10", "2026-09-12");

		addItem(this.festival, 0, 1).andExpect(status().isCreated());

		// 다른 날을 골라도 중복이다 — 중복 판정은 그 날이 아니라 판 전체를 본다
		addItem(this.festival, 2, 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_PLACE_ALREADY_ADDED"))
				.andExpect(jsonPath("$.error.fields").isNotEmpty())
				.andExpect(jsonPath("$.error.fields").value(hasItem("existingDayIndex=0")))
				.andExpect(jsonPath("$.error.fields").value(hasItem("existingDayIndexes=0")))
				.andExpect(jsonPath("$.error.fields").value(hasItem("requestedDayIndex=2")));

		// 판이 낡았다는 409 와 섞이지 않았다는 것을 코드로 확인했다. 이제 판을 실제로 읽는다
		assertThat(latestVersion()).isEqualTo(2);
		List<Map<String, Object>> festivalItems = festivalItemsOf(2);
		assertThat(festivalItems).hasSize(1);
		assertThat(festivalItems.get(0).get("day_index")).isEqualTo(0);
		assertThat(itemsOf(2)).hasSize(3);
	}

	@Test
	@DisplayName("기간 행이 없는 일반 장소는 날짜·중복 검사에 걸리지 않는다")
	void placeWithoutEventPeriodsSkipsBothChecks() throws Exception {
		// placeA 는 1판 첫날에 이미 들어 있고 기간 행이 없다. 같은 카페를 이틀 연속 가는 경우다
		addItem(this.placeA, 1, 1).andExpect(status().isCreated());
		addItem(this.placeA, 2, 2).andExpect(status().isCreated());

		List<Map<String, Object>> samePlace = itemsOf(3).stream()
				.filter((row) -> this.placeA.toString().equals(row.get("place_id")))
				.toList();
		assertThat(samePlace).hasSize(3);
		assertThat(samePlace.stream().map((row) -> row.get("day_index")))
				.containsExactly(0, 1, 2);
	}

	// ---- 도우미 ----

	private ResultActions addItem(UUID placeId, int dayIndex, int baseVersion) throws Exception {
		String body = "{\"placeId\":\"" + placeId + "\",\"dayIndex\":" + dayIndex
				+ ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/items", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asOwner())
				.content(body));
	}

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.userId.toString(), null, List.of());
	}

	private UUID insertVersion(int version, Integer baseVersion, String operation) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now())",
				versionId, this.itineraryId, version, baseVersion, operation, this.userId,
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

	/**
	 * 축제 기간 한 회차를 심는다. 이 표가 비어 있는 장소는 "기간이 정해진 장소" 가 아니므로
	 * 날짜·중복 검사를 건너뛴다 — 그것을 확인하는 시험이 위에 있다.
	 */
	private void insertEventPeriod(UUID placeId, String startDate, String endDate) {
		this.jdbc.update(
				"INSERT INTO place_event_period (place_event_period_id, place_id, title, start_date, end_date, "
						+ "created_at) VALUES (?, ?, '테스트 회차', ?::date, ?::date, now())",
				UUID.randomUUID(), placeId, startDate, endDate);
	}

	/** 그 판에 들어 있는 축제 장소의 항목들. 몇 개인지를 보는 것이 중복 시험의 핵심이다. */
	private List<Map<String, Object>> festivalItemsOf(int version) {
		return itemsOf(version).stream()
				.filter((row) -> this.festival.toString().equals(row.get("place_id")))
				.toList();
	}

	private List<Map<String, Object>> itemsOf(int version) {
		return this.jdbc.queryForList(
				"SELECT i.item_key::text AS item_key, i.place_id::text AS place_id, i.sequence, i.day_index, "
						+ "i.visit_date::text AS visit_date, i.start_time::text AS start_time, "
						+ "i.end_time::text AS end_time, i.stay_minutes, i.locked, i.data_status, "
						+ "i.reason_codes::text AS reason_codes FROM itinerary_item i "
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
