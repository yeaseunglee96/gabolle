package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
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
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 남은 하루 재계획 — S15P21E201-308. 실제 PostgreSQL 위에서 HTTP 로 본다.
 *
 * <h2>한 시나리오로 완료 기준 전부를 본다</h2>
 * 0일차에 A(지나간 방문지, 실제로 30분 늦게 출발)·B(고정, 아직 안 감)·C(아직 안 감) 셋을
 * 두고, 1일차에는 손대면 안 되는 D 를 둔다. {@code factor} 가 이 티켓에서는 아직 배선되지
 * 않아({@link com.gabolle.backend.itinerary.application.ItineraryEditService#replanDay}
 * 주석 참고) 시각이 밀리는 것은 온전히 "A 가 실제로 늦게 출발했다" 는 사실 하나에서 온다 —
 * 계수를 곱하지 않아도 남은 방문지의 예상 시각은 실제 도착·출발을 따라 다시 매겨진다.
 *
 * <p>구간(leg)의 이동 시간을 A→B 15분, B→C 20분으로 넣어 둔다. 그래서 B·C 의 새 시각은
 * 정확히 계산할 수 있는 값이다 — cursor(10:30) + 15분 = B 도착(10:45), +60분(계획 머문
 * 시간) = B 출발(11:45), +20분 = C 도착(12:05), +60분 = C 출발(13:05). 이 값을 그대로
 * 단정한다.
 *
 * <p>날짜는 실행 시점의 {@code LocalDate.now()} 기준으로 며칠 뒤를 쓴다 — 고정 날짜를 쓰면
 * {@link ItineraryDelayProjector} 의 시작점 판정({@code max(마지막 실제 출발, now)})이
 * 테스트를 도는 실제 시각에 따라 흔들릴 수 있다. 여행일을 항상 미래로 두면 "마지막 실제
 * 출발" 이 항상 "지금" 보다 커서 cursor 가 그 값으로 고정된다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryReplanIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryEditController editController;

	@Autowired
	private ItineraryExceptionHandler editExceptionHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID itineraryId;

	private LocalDate day0;

	private LocalDate day1;

	private UUID keyA;

	private UUID keyB;

	private UUID keyC;

	private UUID keyD;

	/**
	 * 1판: 0일차에 A(방문 기록 있음, 09:00~10:00 계획인데 10:30 에 출발)·B(고정,
	 * 11:00~12:00)·C(13:30~14:30), 1일차에 D(09:00~10:00). 구간은 0일차에 셋
	 * (출발지→A 5분, A→B 15분, B→C 20분), 1일차에 하나(출발지→D 10분).
	 */
	@BeforeEach
	void seedDayZeroWithAVisitedPlaceAndDayOneUntouched() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController)
				.setControllerAdvice(this.editExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.day0 = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
		this.day1 = this.day0.plusDays(1);
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();
		this.keyC = UUID.randomUUID();
		this.keyD = UUID.randomUUID();
		UUID placeA = UUID.randomUUID();
		UUID placeB = UUID.randomUUID();
		UUID placeC = UUID.randomUUID();
		UUID placeD = UUID.randomUUID();

		createUser(this.ownerId, now);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, ?::date, ?::date, 1, ?, ?)",
				tripId, this.ownerId, this.day0, this.day1, now, now);
		insertMember(tripId, this.ownerId, "OWNER", now);

		insertPlace(placeA, "A 해운대해수욕장", now);
		insertPlace(placeB, "B 광안리해변", now);
		insertPlace(placeC, "C 태종대", now);
		insertPlace(placeD, "D 감천문화마을", now);

		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID v1 = insertVersion(1, null, "CREATE", null);
		insertItem(v1, this.keyA, 0, this.day0, 1, placeA, "09:00", "10:00", false, "VERIFIED");
		insertItem(v1, this.keyB, 0, this.day0, 2, placeB, "11:00", "12:00", true, "ESTIMATED");
		insertItem(v1, this.keyC, 0, this.day0, 3, placeC, "13:30", "14:30", false, "ESTIMATED");
		insertItem(v1, this.keyD, 1, this.day1, 1, placeD, "09:00", "10:00", false, "ESTIMATED");

		insertLeg(v1, 0, 1, null, placeA, 500, 5, "VERIFIED", now);
		insertLeg(v1, 0, 2, placeA, placeB, 1500, 15, "VERIFIED", now);
		insertLeg(v1, 0, 3, placeB, placeC, 2000, 20, "VERIFIED", now);
		insertLeg(v1, 1, 1, null, placeD, 800, 10, "VERIFIED", now);

		// A 는 계획보다 30분 늦게 출발했다 — 이 사실 하나가 B·C 의 새 시각을 만든다.
		insertActual(this.keyA, seoul(this.day0, "09:00"), seoul(this.day0, "10:30"), this.ownerId, now);
	}

	// ---- 테스트 ----

	@Test
	@DisplayName("완료 기준 — 재계획하면 지나간 방문지는 그대로 있고 이후 방문지의 시각만 바뀐다")
	void pastItemStaysAndLaterItemsShiftAfterReplan() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		List<Map<String, Object>> day0v2 = itemsOf(2, 0);

		// A(지나간 방문지)는 시각 지도에 아예 없어서 원래 값 그대로 복사된다.
		Map<String, Object> a = day0v2.get(0);
		assertThat(a.get("item_key")).isEqualTo(this.keyA.toString());
		assertThat(a.get("start_time")).as("지나간 방문지의 계획 시각은 재계획으로도 안 바뀐다").isEqualTo("09:00:00");
		assertThat(a.get("end_time")).isEqualTo("10:00:00");

		// B·C 는 cursor(A 의 실제 출발 10:30)에서 이동 시간만큼 더한 시각으로 다시 매겨진다.
		Map<String, Object> b = day0v2.get(1);
		assertThat(b.get("item_key")).isEqualTo(this.keyB.toString());
		assertThat(b.get("start_time")).as("cursor 10:30 + A→B 이동 15분").isEqualTo("10:45:00");
		assertThat(b.get("end_time")).as("도착 10:45 + 계획 머문 시간 60분").isEqualTo("11:45:00");

		Map<String, Object> c = day0v2.get(2);
		assertThat(c.get("item_key")).isEqualTo(this.keyC.toString());
		assertThat(c.get("start_time")).as("B 출발 11:45 + B→C 이동 20분").isEqualTo("12:05:00");
		assertThat(c.get("end_time")).as("도착 12:05 + 계획 머문 시간 60분").isEqualTo("13:05:00");
	}

	@Test
	@DisplayName("완료 기준 — 고정한 장소는 재계획 후에도 남아 있다")
	void lockedItemSurvivesReplan() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		List<Map<String, Object>> day0v2 = itemsOf(2, 0);
		Map<String, Object> b = day0v2.stream()
				.filter((row) -> this.keyB.toString().equals(row.get("item_key")))
				.findFirst()
				.orElseThrow();

		assertThat(b.get("locked")).as("고정한 장소는 새 판에도 있고 여전히 고정 상태다").isEqualTo(true);
	}

	@Test
	@DisplayName("완료 기준 — 되돌리기를 부르면 재계획 전 상태로 돌아간다")
	void revertAfterReplanRestoresPreReplanTimes() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		this.mockMvc.perform(post("/api/v1/itineraries/{id}/revert", this.itineraryId)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"baseVersion\":2}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(3));

		List<Map<String, Object>> day0v1 = itemsOf(1, 0);
		List<Map<String, Object>> day0v3 = itemsOf(3, 0);

		// item_key 까지 같아야 한다 — 값만 같고 다른 항목이 된 것이 아님을 확인한다.
		assertThat(day0v3).extracting((row) -> row.get("item_key"))
				.containsExactlyElementsOf(day0v1.stream().map((row) -> row.get("item_key")).toList());
		assertThat(day0v3).extracting((row) -> row.get("start_time"), (row) -> row.get("end_time"))
				.as("재계획으로 밀렸던 B·C 시각이 재계획 전 값으로 돌아와야 한다")
				.containsExactlyElementsOf(day0v1.stream()
						.map((row) -> org.assertj.core.groups.Tuple.tuple(row.get("start_time"), row.get("end_time")))
						.toList());
	}

	@Test
	@DisplayName("재계획은 그날 구간을 버리지 않는다")
	void replanKeepsTheDaysLegsIntact() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		List<Map<String, Object>> legsV1 = legsOf(1, 0);
		List<Map<String, Object>> legsV2 = legsOf(2, 0);

		// 거리 숫자가 아니라 (from,to) 짝과 상태로 "같은 구간인가" 를 본다 — 우연히 같은
		// 숫자가 아니라 순서 바꾸기가 하는 것처럼 다시 만든 것이 아님을 보이기 위해서다.
		assertThat(legsV2).extracting(
						(row) -> row.get("from_place_id"),
						(row) -> row.get("to_place_id"),
						(row) -> row.get("distance_m"),
						(row) -> row.get("data_status"))
				.as("시간표만 밀렸을 뿐 장소 사이 거리는 그대로다")
				.containsExactlyElementsOf(legsV1.stream()
						.map((row) -> org.assertj.core.groups.Tuple.tuple(row.get("from_place_id"),
								row.get("to_place_id"), row.get("distance_m"), row.get("data_status")))
						.toList());
	}

	@Test
	@DisplayName("다른 날은 안 건드린다")
	void otherDayIsUntouched() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		List<Map<String, Object>> day1v1 = itemsOf(1, 1);
		List<Map<String, Object>> day1v2 = itemsOf(2, 1);
		assertThat(day1v2).extracting((row) -> row.get("item_key"), (row) -> row.get("start_time"),
						(row) -> row.get("end_time"))
				.containsExactlyElementsOf(day1v1.stream()
						.map((row) -> org.assertj.core.groups.Tuple.tuple(row.get("item_key"), row.get("start_time"),
								row.get("end_time")))
						.toList());

		List<Map<String, Object>> legs1v1 = legsOf(1, 1);
		List<Map<String, Object>> legs1v2 = legsOf(2, 1);
		assertThat(legs1v2).extracting((row) -> row.get("to_place_id"), (row) -> row.get("distance_m"))
				.containsExactlyElementsOf(legs1v1.stream()
						.map((row) -> org.assertj.core.groups.Tuple.tuple(row.get("to_place_id"), row.get("distance_m")))
						.toList());
	}

	@Test
	@DisplayName("낡은 판 번호로 부르면 409 이고 error.fields 에 latestVersion=<n> 이 있다")
	void staleBaseVersionIsConflict() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		// 최신판은 이제 2인데 1을 바탕 판이라고 우기며 다시 부른다 — 다른 편집과 같은 409다.
		replanDay(0, 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_VERSION_CONFLICT"))
				.andExpect(jsonPath("$.error.fields").value(Matchers.hasItem("latestVersion=2")));
	}

	@Test
	@DisplayName("새 판의 operation 은 REPLAN_DAY 로 남는다")
	void newVersionOperationIsReplanDay() throws Exception {
		replanDay(0, 1).andExpect(status().isOk());

		// 응답 JSON 이 아니라 표에 실제로 적힌 값을 직접 읽는다 — 응답만 맞고 저장이 다른
		// 값이면 되돌리기·최근 변경 목록이 엉뚱한 사건으로 읽는다.
		String operation = this.jdbc.queryForObject(
				"SELECT operation FROM itinerary_versions WHERE itinerary_id = ? AND version = 2",
				String.class, this.itineraryId);
		assertThat(operation).isEqualTo("REPLAN_DAY");
	}

	// ---- 도우미 ----

	private ResultActions replanDay(int dayIndex, int baseVersion) throws Exception {
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/days/{dayIndex}/replan", this.itineraryId,
						dayIndex)
				.param("baseVersion", String.valueOf(baseVersion))
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asOwner()));
	}

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.ownerId.toString(), null, List.of());
	}

	/** 계획 시각과 같은 시간대(Asia/Seoul, 고정 +09:00)로 실제 시각을 만든다. */
	private static OffsetDateTime seoul(LocalDate date, String hhmm) {
		return OffsetDateTime.of(date, LocalTime.parse(hhmm), ZoneOffset.ofHours(9));
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

	private void insertPlace(UUID placeId, String nameKo, OffsetDateTime now) {
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, ?, ?)", placeId, nameKo, now);
	}

	private UUID insertVersion(int version, Integer baseVersion, String operation, Integer revertedFrom) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at, reverted_from_version) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?)",
				versionId, this.itineraryId, version, baseVersion, operation, this.ownerId, "req_seed_" + version,
				revertedFrom);
		this.jdbc.update("UPDATE itineraries SET latest_version = ? WHERE itinerary_id = ?", version,
				this.itineraryId);
		return versionId;
	}

	private void insertItem(UUID versionId, UUID itemKey, int dayIndex, LocalDate visitDate, int sequence,
			UUID placeId, String start, String end, boolean locked, String dataStatus) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, ?::date, ?, ?, ?::time, ?::time, NULL, ?, ?, now())",
				UUID.randomUUID(), versionId, itemKey, dayIndex, visitDate, sequence, placeId, start, end, locked,
				dataStatus);
	}

	private void insertLeg(UUID versionId, int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId,
			int distanceM, int durationMin, String dataStatus, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, "
						+ "from_place_id, to_place_id, travel_mode, distance_m, duration_min, walking_meters, "
						+ "data_status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'WALK', ?, ?, ?, ?, ?)",
				UUID.randomUUID(), versionId, dayIndex, sequence, fromPlaceId, toPlaceId, distanceM, durationMin,
				distanceM, dataStatus, now);
	}

	private void insertActual(UUID itemKey, OffsetDateTime arrivedAt, OffsetDateTime departedAt, UUID recordedBy,
			OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_item_actual (itinerary_item_actual_id, itinerary_id, item_key, arrived_at, "
						+ "departed_at, recorded_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), this.itineraryId, itemKey, arrivedAt, departedAt, recordedBy, now, now);
	}

	private List<Map<String, Object>> itemsOf(int version, int dayIndex) {
		return this.jdbc.queryForList(
				"SELECT i.item_key::text AS item_key, i.start_time::text AS start_time, "
						+ "i.end_time::text AS end_time, i.locked FROM itinerary_item i "
						+ "JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? AND i.day_index = ? ORDER BY i.sequence",
				this.itineraryId, version, dayIndex);
	}

	private List<Map<String, Object>> legsOf(int version, int dayIndex) {
		return this.jdbc.queryForList(
				"SELECT l.from_place_id::text AS from_place_id, l.to_place_id::text AS to_place_id, "
						+ "l.distance_m, l.data_status FROM itinerary_leg l "
						+ "JOIN itinerary_versions v ON v.itinerary_version_id = l.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? AND l.day_index = ? ORDER BY l.sequence",
				this.itineraryId, version, dayIndex);
	}
}
