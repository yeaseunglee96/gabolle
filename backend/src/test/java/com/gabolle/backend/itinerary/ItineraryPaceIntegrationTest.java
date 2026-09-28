package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryPaceController;
import com.gabolle.backend.itinerary.presentation.ItineraryPaceExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;
import com.jayway.jsonpath.JsonPath;

/**
 * 하루치 지연 경고 · 여행 리듬 조회. 실제 PostgreSQL 위에서 HTTP 로 본다.
 *
 * <p>응답은 DTO 로 되읽지 않고 JsonPath 로만 읽는다 — 되읽으면 칸 이름·타입이 바뀌어도
 * 통과한다.
 *
 * <p>날짜는 항상 미래로 둔다.
 * {@link com.gabolle.backend.itinerary.application.ItineraryDelayProjector} 의 시작점
 * 판정이 {@code max(마지막 실제 출발, now)} 라, 방문일이 과거이면 테스트를 도는 시각에
 * 따라 예상 시각이 흔들린다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryPaceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryPaceController paceController;

	@Autowired
	private ItineraryPaceExceptionHandler paceExceptionHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID strangerId;

	private UUID itineraryId;

	/** 0~2일차 3일짜리 여행 — dayIndex 범위 검사(0~2)에 여유를 준다. */
	private LocalDate day0;

	/**
	 * 공통 준비물만 심는다 — 사용자·3일짜리 여행·빈 1판. 각 검사가 필요한 방문지·기록은
	 * 스스로의 도우미를 불러 심는다(검사마다 필요한 표본 수·시각이 달라서다).
	 */
	@BeforeEach
	void seedOwnerAndThreeDayTrip() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.paceController)
				.setControllerAdvice(this.paceExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		this.strangerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.day0 = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
		LocalDate finish = this.day0.plusDays(2);

		createUser(this.ownerId, now);
		createUser(this.strangerId, now);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, ?::date, ?::date, 1, ?, ?)",
				tripId, this.ownerId, this.day0, finish, now, now);
		insertMember(tripId, this.ownerId, "OWNER", now);
		// strangerId 는 일부러 trip_member 에 넣지 않는다 — "참여자가 아니다" 를 재현한다.

		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);
		insertVersion(1, null, "CREATE");
	}

	@Test
	@DisplayName("완료 기준 — 기록 5건 이상이면 예상 도착 시각이 계획과 달라진다")
	void fiveOrMoreRecordsChangePredictedArrival() throws Exception {
		UUID keyUpcoming = seedFiveOverlongVisitsAndOneUpcomingItem();

		MvcResult result = this.mockMvc
				.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.paceFactor").value(1.5))
				.andReturn();

		String json = result.getResponse().getContentAsString();
		String predictedArrival = readOne(json,
				"$.data.items[?(@.itemId=='" + keyUpcoming + "')].predictedArrival");
		String plannedArrival = readOne(json, "$.data.items[?(@.itemId=='" + keyUpcoming + "')].plannedArrival");

		// 계획은 14:00 인데 앞선 다섯 방문이 실제로 30분씩 늦게 끝나서 cursor 가 14:30 이 된다 —
		// 우연히 같은 숫자가 아니라 "계획과 다른 값" 임을 정확한 문자열로 확인한다.
		assertThat(plannedArrival).isEqualTo(this.day0 + "T14:00:00+09:00");
		assertThat(predictedArrival).as("실제 지연을 반영해 예상 도착이 계획보다 늦어져야 한다")
				.isEqualTo(this.day0 + "T14:30:00+09:00")
				.isNotEqualTo(plannedArrival);
	}

	@Test
	@DisplayName("완료 기준 — 기록 2건에서는 계수가 적용되지 않고 추정 표시가 붙는다")
	void twoRecordsAreNotEnoughForAFactor() throws Exception {
		seedTwoShortOfMinimumSamples();

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				// doesNotExist() 로 재지 않는다 — 이 응답은 paceFactor 칸을 항상 만들고 표본이
				// 모자랄 때만 값을 null 로 채운다. doesNotExist() 는 칸이 아예 없는 것을 보는
				// 것이라 여기서는 항상 통과해 버린다.
				.andExpect(jsonPath("$.data.paceFactor").value(nullValue()))
				.andExpect(jsonPath("$.data.sampleCount").value(2))
				.andExpect(jsonPath("$.data.minSamples").value(3))
				.andExpect(jsonPath("$.data.notChecked[0].check").value("PACE_FACTOR"))
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NOT_ENOUGH_RECORDS"));
	}

	@Test
	@DisplayName("완료 기준 — 지연 위험 항목이 응답에서 구분된다")
	void atRiskItemIsCalledOutSeparately() throws Exception {
		UUID keyUpcoming = seedFiveOverlongVisitsAndOneUpcomingItem();

		MvcResult result = this.mockMvc
				.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.atRiskItemIds").value(org.hamcrest.Matchers.hasItem(keyUpcoming.toString())))
				.andReturn();

		String json = result.getResponse().getContentAsString();
		Boolean atRisk = readOne(json, "$.data.items[?(@.itemId=='" + keyUpcoming + "')].atRisk");

		// 계수(1.5)를 곱하면 머문 시간이 90분으로 늘어 하루 끝(15:00)을 넘긴다 — 그래서
		// atRiskItemIds 뿐 아니라 그 항목 자신의 atRisk 칸도 참이어야 한다.
		assertThat(atRisk).as("하루 끝을 넘기는 항목은 자기 칸에도 위험 표시가 있어야 한다").isTrue();
	}

	@Test
	@DisplayName("완료 기준 — 리듬 요약이 실제 일정 데이터와 맞는 숫자를 돌려준다")
	void rhythmSummaryMatchesSeededData() throws Exception {
		// dayCount 가 2 여야 하는 검사라 공통 3일 여행을 안 쓰고, 이 검사만의 2일짜리
		// 여행·일정을 따로 심는다.
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID owner = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		UUID itineraryId = UUID.randomUUID();
		LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
		LocalDate finish = start.plusDays(1);

		createUser(owner, now);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, ?::date, ?::date, 1, ?, ?)",
				tripId, owner, start, finish, now, now);
		insertMember(tripId, owner, "OWNER", now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				itineraryId, tripId, now);
		UUID v1 = insertVersionFor(itineraryId, owner, 1, null, "CREATE");

		// 0일차 셋(60·90·30분), 1일차 둘(60·60분) — 항목 다섯, 이틀. 5/2 = 2.5.
		insertItemFor(v1, UUID.randomUUID(), 0, start, 1, insertPlace(now), "09:00", "10:00", now);
		insertItemFor(v1, UUID.randomUUID(), 0, start, 2, insertPlace(now), "10:00", "11:30", now);
		insertItemFor(v1, UUID.randomUUID(), 0, start, 3, insertPlace(now), "11:30", "12:00", now);
		insertItemFor(v1, UUID.randomUUID(), 1, finish, 1, insertPlace(now), "09:00", "10:00", now);
		insertItemFor(v1, UUID.randomUUID(), 1, finish, 2, insertPlace(now), "10:00", "11:00", now);

		// 이동시간 합 75분(0일차 10+20+15, 1일차 25+5), 머문시간 합 300분(60+90+30+60+60).
		// travelShare = 75 / (75+300) = 0.2 — 손으로 미리 계산해 둔 값과 응답을 맞댄다.
		insertLegFor(v1, 0, 1, null, insertPlace(now), 10, now);
		insertLegFor(v1, 0, 2, null, insertPlace(now), 20, now);
		insertLegFor(v1, 0, 3, null, insertPlace(now), 15, now);
		insertLegFor(v1, 1, 1, null, insertPlace(now), 25, now);
		insertLegFor(v1, 1, 2, null, insertPlace(now), 5, now);

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/rhythm", itineraryId)
						.principal(new UsernamePasswordAuthenticationToken(owner.toString(), null, List.of())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.dayCount").value(2))
				.andExpect(jsonPath("$.data.averageItemsPerDay").value(2.5))
				.andExpect(jsonPath("$.data.travelShare").value(0.2));
	}

	// ── 계수를 저장하는 표 ────────────────────────────────────────────────

	@Test
	@DisplayName("계수가 없어도 계수 표에 행이 안 생긴다")
	void insufficientSamplesLeaveNoRowInPaceFactorTable() throws Exception {
		seedTwoShortOfMinimumSamples();

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk());

		Integer rows = this.jdbc.queryForObject("SELECT count(*) FROM user_pace_factor WHERE user_id = ?",
				Integer.class, this.ownerId);
		assertThat(rows).as("표본이 모자라면 1.0 도 넣지 않고 행 자체를 안 만든다").isZero();
	}

	@Test
	@DisplayName("계수를 구하면 표에 판이 하나 생기고, 다시 조회해도 현재 판은 하나뿐이다")
	void recomputingTwiceStillLeavesOneCurrentRow() throws Exception {
		seedFiveOverlongVisitsAndOneUpcomingItem();

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk());
		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk());

		// 조회할 때마다 다시 계산해 새 판을 여는 구조라 총 행 수는 둘일 수 있다 — 이 검사가
		// 보는 것은 "지금 쓰는 판"(superseded_at IS NULL)이 항상 하나뿐인가다. 조건부 UNIQUE
		// 색인(uq_user_pace_factor_current)이 실제로 그 하나를 강제하는지를 보는 것이다.
		Integer currentRows = this.jdbc.queryForObject(
				"SELECT count(*) FROM user_pace_factor WHERE user_id = ? AND superseded_at IS NULL",
				Integer.class, this.ownerId);
		assertThat(currentRows).isEqualTo(1);
	}

	// ── 행동 개인화 스위치 ──────────────────────────────

	@Test
	@DisplayName("행동 개인화를 끈 사람에게는 속도 계수를 만들지 않는다 — 표에도 행이 안 생긴다")
	void behaviorPersonalizationOffLeavesNoPaceFactor() throws Exception {
		// 켜져 있으면 paceFactor 1.5 가 나오는 바로 그 데이터를 쓴다
		// (fiveOrMoreRecordsChangePredictedArrival 와 같은 씨앗). 달라지는 것은 스위치뿐이다.
		seedFiveOverlongVisitsAndOneUpcomingItem();
		this.jdbc.update("UPDATE app_user SET personalization_mode = 'EXPLICIT_ONLY' WHERE user_id = ?",
				this.ownerId);

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				// 화면은 계속 나온다. 계수만 없다 — 표본이 모자랄 때와 같은 모양이라 부르는
				// 쪽이 이미 다루는 경우다.
				.andExpect(jsonPath("$.data.paceFactor").value(nullValue()));

		Integer rows = this.jdbc.queryForObject("SELECT count(*) FROM user_pace_factor WHERE user_id = ?",
				Integer.class, this.ownerId);
		assertThat(rows).as("개인화를 끈 사람의 행동으로 프로필을 만들지 않는다").isZero();
	}

	// ── 여행 기간·참여자 검증 ───────────────────────────────────────────────

	@Test
	@DisplayName("여행 기간 밖의 dayIndex 는 400 이고 음수도 400 이다")
	void dayIndexOutsideTripRangeIsBadRequest() throws Exception {
		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/3/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("DAY_OUTSIDE_TRIP"));

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/-1/pace", this.itineraryId).principal(asOwner()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("DAY_OUTSIDE_TRIP"));
	}

	@Test
	@DisplayName("참여자가 아니면 404 — 남의 여행은 존재를 감춘다")
	void nonParticipantGetsNotFound() throws Exception {
		this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", this.itineraryId)
						.principal(new UsernamePasswordAuthenticationToken(this.strangerId.toString(), null, List.of())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));
	}

	/**
	 * 운영에서 추천 화면이 코스 2·3안의 번호({@code 요청번호:2})를 일정 번호로 넘겨 500 이 났다
	 * (S15P21E201-1609). 형식이 틀린 번호는 없는 일정이다.
	 */
	@Test
	@DisplayName("🔴 형식이 틀린 일정 번호는 500 이 아니라 404 다 — 코스 번호(요청번호:2)·아무 글자")
	void malformedItineraryIdIsNotFound() throws Exception {
		for (String malformed : List.of(this.itineraryId + ":2", "test-trip-id")) {
			this.mockMvc.perform(get("/api/v1/itineraries/{id}/days/0/pace", malformed).principal(asOwner()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));
		}
	}

	// ---- 시나리오 도우미 ----

	/**
	 * 0일차에 60분씩 계획된 방문지 다섯 곳을 실제로는 90분씩(1.5배) 머문 것으로 심고,
	 * 이어서 계획 14:00~15:00 인 여섯째 방문지(아직 안 감)를 더한다.
	 *
	 * <p>다섯 표본의 비율이 전부 1.5 라 중앙값도 1.5 — 계수가 무엇으로 나올지 계산할 필요 없이
	 * 고정된다. 여섯째의 cursor 는 다섯 방문 중 가장 늦은 실제 출발(14:30)이 된다.
	 *
	 * @return 여섯째(아직 안 감) 항목의 itemKey
	 */
	private UUID seedFiveOverlongVisitsAndOneUpcomingItem() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String[] slots = { "09:00", "10:00", "11:00", "12:00", "13:00", "14:00" };
		UUID lastKey = null;
		for (int i = 0; i < 5; i++) {
			UUID key = UUID.randomUUID();
			UUID place = insertPlace(now);
			insertItem(key, 0, this.day0, i + 1, place, slots[i], slots[i + 1], now);
			insertActual(key, seoul(this.day0, slots[i]), seoul(this.day0, slots[i]).plusMinutes(90), this.ownerId,
					now);
			lastKey = key;
		}
		UUID upcoming = UUID.randomUUID();
		insertItem(upcoming, 0, this.day0, 6, insertPlace(now), "14:00", "15:00", now);
		assertThat(lastKey).isNotNull();
		return upcoming;
	}

	/** 60분 계획에 90분·80분 실제 — 표본 둘, 셋(MIN_SAMPLES) 에 하나 모자란다. */
	private void seedTwoShortOfMinimumSamples() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID key1 = UUID.randomUUID();
		UUID key2 = UUID.randomUUID();
		insertItem(key1, 0, this.day0, 1, insertPlace(now), "09:00", "10:00", now);
		insertItem(key2, 0, this.day0, 2, insertPlace(now), "10:00", "11:00", now);
		insertActual(key1, seoul(this.day0, "09:00"), seoul(this.day0, "10:30"), this.ownerId, now);
		insertActual(key2, seoul(this.day0, "10:00"), seoul(this.day0, "11:20"), this.ownerId, now);
	}

	// ---- 공용 도우미 ----

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.ownerId.toString(), null, List.of());
	}

	/** 계획 시각과 같은 시간대(Asia/Seoul, 고정 +09:00)로 실제 시각을 만든다. */
	private static OffsetDateTime seoul(LocalDate date, String hhmm) {
		return OffsetDateTime.of(date, LocalTime.parse(hhmm), ZoneOffset.ofHours(9));
	}

	@SuppressWarnings("unchecked")
	private static <T> T readOne(String json, String path) {
		Object value = JsonPath.read(json, path);
		return ((List<T>) value).get(0);
	}

	/**
	 * {@code BEHAVIOR_ENABLED} 로 심는다. 속도 계수는 행동으로 만드는 사람별 프로필이라
	 * 개인화를 끈 사람에게는 만들지 않고({@code PaceFactorService.recompute}), 이 파일의
	 * 검사 대부분은 계수가 나오는 것을 재므로 켠 계정이어야 한다. 끈 쪽은
	 * {@link #behaviorPersonalizationOffLeavesNoPaceFactor} 가 따로 잡는다.
	 */
	private void createUser(UUID userId, OffsetDateTime now) {
		createUser(userId, now, "BEHAVIOR_ENABLED");
	}

	private void createUser(UUID userId, OffsetDateTime now, String personalizationMode) {
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
						+ "created_at, updated_at) VALUES (?, 'test', 'ko', ?, 'ACTIVE', ?, ?)",
				userId, personalizationMode, now, now);
	}

	private void insertMember(UUID tripId, UUID userId, String role, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, now);
	}

	private UUID insertPlace(OffsetDateTime now) {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'test', ?)", placeId, now);
		return placeId;
	}

	private void insertVersion(int version, Integer baseVersion, String operation) {
		insertVersionFor(this.itineraryId, this.ownerId, version, baseVersion, operation);
	}

	private UUID insertVersionFor(UUID itineraryId, UUID ownerId, int version, Integer baseVersion,
			String operation) {
		UUID versionId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now())",
				versionId, itineraryId, version, baseVersion, operation, ownerId, "req_seed_" + version);
		this.jdbc.update("UPDATE itineraries SET latest_version = ? WHERE itinerary_id = ?", version, itineraryId);
		return versionId;
	}

	/** 공통 1판(this.itineraryId)에 항목을 심는다. stayMinutes 는 안 두고 시작·종료로만 잰다. */
	private void insertItem(UUID itemKey, int dayIndex, LocalDate visitDate, int sequence, UUID placeId,
			String start, String end, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, (SELECT itinerary_version_id FROM itinerary_versions "
						+ "WHERE itinerary_id = ? AND version = 1), ?, ?, ?::date, ?, ?, ?::time, ?::time, NULL, "
						+ "FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), this.itineraryId, itemKey, dayIndex, visitDate, sequence, placeId, start, end);
	}

	private void insertItemFor(UUID versionId, UUID itemKey, int dayIndex, LocalDate visitDate, int sequence,
			UUID placeId, String start, String end, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, ?::date, ?, ?, ?::time, ?::time, NULL, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, dayIndex, visitDate, sequence, placeId, start, end);
	}

	private void insertLegFor(UUID versionId, int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId,
			int durationMin, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, "
						+ "from_place_id, to_place_id, travel_mode, distance_m, duration_min, walking_meters, "
						+ "data_status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'WALK', ?, ?, ?, 'VERIFIED', ?)",
				UUID.randomUUID(), versionId, dayIndex, sequence, fromPlaceId, toPlaceId, durationMin * 100,
				durationMin, durationMin * 100, now);
	}

	private void insertActual(UUID itemKey, OffsetDateTime arrivedAt, OffsetDateTime departedAt, UUID recordedBy,
			OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_item_actual (itinerary_item_actual_id, itinerary_id, item_key, arrived_at, "
						+ "departed_at, recorded_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), this.itineraryId, itemKey, arrivedAt, departedAt, recordedBy, now, now);
	}
}
