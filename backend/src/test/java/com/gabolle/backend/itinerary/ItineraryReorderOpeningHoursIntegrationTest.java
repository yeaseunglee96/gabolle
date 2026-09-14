package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 순서를 바꿨을 때 영업시간 위반을 순서는 그대로 두고 알려 주는가 — S15P21E201-268 의 마지막
 * 완료 기준.
 *
 * <p>다른 두 검사가 이미 옆에 있다. {@link ItineraryReorderIntegrationTest} 는 권한과 검증
 * (409 · 400 · 403 · 404)을, {@link ItineraryReorderLegRebuildIntegrationTest} 는 바뀐 날의
 * 이동시간이 다시 채워지는지를 잰다. 여기서는 <b>경고가 나가는지</b>만 잰다.
 *
 * <h2>가짜 문을 세우는 이유</h2>
 * 운영에는 영업시간 데이터가 한 건도 없고, 그래서 실제 구현
 * ({@code NotCollectedOpeningHoursFilter})은 "수집 안 했다" 만 답한다. 그 상태로는 위반이 잡히는
 * 갈래를 볼 수 없다. 그래서 {@link SwitchableOpeningHours} 로 <b>답을 정할 수 있는 문</b>을 세운다 —
 * 실제 인터페이스의 계약(못 볼 때는 {@code isAvailable()} 이 거짓이고 {@code isOpenAt} 은 부르면
 * 안 된다)을 그대로 지킨다. 그 문 하나로 네 상황을 다 재현하므로 컨텍스트도 하나면 된다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@Import(ItineraryReorderOpeningHoursIntegrationTest.SwitchableOpeningHoursOverride.class)
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryReorderOpeningHoursIntegrationTest {

	/**
	 * 답을 시험마다 정할 수 있는 영업시간 문.
	 *
	 * <p>🔴 {@code available} 이 거짓일 때 {@link #isOpenAt} 은 <b>예외를 던진다.</b> 실제 구현이
	 * 그렇게 하기로 약속했기 때문이다 — "모른다" 를 "열려 있다" 로 바꾸지 않는다는 약속이고, 이
	 * 가짜가 그 자리에서 조용히 참을 답하면 검사는 초록인데 운영에서만 틀린 안심이 나간다.
	 */
	static class SwitchableOpeningHours implements OpeningHoursFilterPort {

		private boolean available = true;

		private String unavailableReason = "NOT_COLLECTED";

		private final Set<UUID> closedPlaces = new HashSet<>();

		void answerAvailable(Set<UUID> closed) {
			this.available = true;
			this.closedPlaces.clear();
			this.closedPlaces.addAll(closed);
		}

		void answerUnavailable(String reason) {
			this.available = false;
			this.unavailableReason = reason;
			this.closedPlaces.clear();
		}

		@Override
		public boolean isAvailable() {
			return this.available;
		}

		@Override
		public String unavailableReason() {
			return this.unavailableReason;
		}

		@Override
		public boolean isOpenAt(UUID placeId, OffsetDateTime at) {
			if (!this.available) {
				throw new IllegalStateException("isAvailable() 이 거짓인데 isOpenAt 이 불렸다");
			}
			return !this.closedPlaces.contains(placeId);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SwitchableOpeningHoursOverride {

		@Bean
		@Primary
		SwitchableOpeningHours switchableOpeningHours() {
			return new SwitchableOpeningHours();
		}
	}

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
	private SwitchableOpeningHours openingHours;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID itineraryId;

	private UUID placeA;

	private UUID placeB;

	private UUID placeC;

	private UUID keyA;

	private UUID keyB;

	private UUID keyC;

	/**
	 * 0일차에 셋(A 09:00 · B 11:00 · C 13:00).
	 *
	 * <p>🔴 <b>순서를 바꿔도 시각표는 그 자리에 남고 항목만 자리를 옮겨 앉는다</b>
	 * ({@code ItineraryRevision.withReorderedDay}). 그래서 C 를 맨 앞으로 보내면 C 가 09:00 을
	 * 받는다. "C 는 09:00 에 문을 안 연다" 를 이 문에 심어 두면 위반이 확정적으로 하나 생긴다.
	 */
	@BeforeEach
	void seedThreePlacesWithTimesOnDayZero() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		this.openingHours.answerAvailable(Set.of());

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.placeA = UUID.randomUUID();
		this.placeB = UUID.randomUUID();
		this.placeC = UUID.randomUUID();
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();
		this.keyC = UUID.randomUUID();

		createUser(this.ownerId, now);
		insertTrip(tripId, this.ownerId, "2026-09-10", "2026-09-11", 35.10, 129.10, now);
		insertMember(tripId, this.ownerId, "OWNER", now);
		insertPlace(this.placeA, "A 해운대해수욕장", 35.15, 129.16, now);
		insertPlace(this.placeB, "B 광안리해변", 35.16, 129.12, now);
		insertPlace(this.placeC, "C 태종대", 35.05, 129.09, now);

		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID v1 = insertVersion(this.itineraryId, this.ownerId, 1);
		insertItem(v1, this.keyA, 0, 1, this.placeA, "09:00", "11:00");
		insertItem(v1, this.keyB, 0, 2, this.placeB, "11:00", "13:00");
		insertItem(v1, this.keyC, 0, 3, this.placeC, "13:00", "15:00");
	}

	@Test
	@DisplayName("문 닫은 시간에 걸리는 순서를 보내면 순서가 유지된 채 경고가 온다")
	void reorderKeepsRequestedOrderAndReturnsClosedWarning() throws Exception {
		this.openingHours.answerAvailable(Set.of(this.placeC));

		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk())
				// 순서가 그대로다 — 서버가 더 나은 순서로 고쳐 놓지 않는다.
				.andExpect(jsonPath("$.data.days[0].items[0].id").value(this.keyC.toString()))
				.andExpect(jsonPath("$.data.days[0].items[1].id").value(this.keyA.toString()))
				.andExpect(jsonPath("$.data.days[0].items[2].id").value(this.keyB.toString()))
				// 그리고 경고가 함께 온다.
				.andExpect(jsonPath("$.data.warnings.length()").value(1))
				.andExpect(jsonPath("$.data.warnings[0].code").value("OPENING_HOURS_CLOSED"))
				.andExpect(jsonPath("$.data.warnings[0].itemId").value(this.keyC.toString()))
				.andExpect(jsonPath("$.data.warnings[0].placeId").value(this.placeC.toString()))
				// 09:00 자리로 옮겨 앉은 것이 위반의 이유다.
				.andExpect(jsonPath("$.data.warnings[0].at").value("2026-09-10T09:00+09:00"))
				.andExpect(jsonPath("$.data.notChecked.length()").value(0));
	}

	@Test
	@DisplayName("전부 문을 여는 시간이면 경고가 없고 못 한 검사도 없다")
	void reorderReturnsNoWarningWhenEveryPlaceIsOpen() throws Exception {
		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].id").value(this.keyC.toString()))
				.andExpect(jsonPath("$.data.warnings.length()").value(0))
				.andExpect(jsonPath("$.data.notChecked.length()").value(0));
	}

	@Test
	@DisplayName("영업시간을 아직 수집하지 않았으면 경고 대신 못 한 검사로 알린다")
	void reorderReportsUncheckedWhenOpeningHoursAreNotCollected() throws Exception {
		this.openingHours.answerUnavailable("NOT_COLLECTED");

		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].id").value(this.keyC.toString()))
				// 🔴 빈 경고 목록만 보내면 "확인했고 문제 없음" 으로 읽힌다. 그래서 왜 못 봤는지를 싣는다.
				.andExpect(jsonPath("$.data.warnings.length()").value(0))
				.andExpect(jsonPath("$.data.notChecked.length()").value(1))
				.andExpect(jsonPath("$.data.notChecked[0].check").value("OPENING_HOURS"))
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NOT_COLLECTED"));
	}

	@Test
	@DisplayName("방문 시각이 없는 항목이 있으면 그 항목은 판정하지 못했다고 알린다")
	void reorderReportsUncheckedForItemWithoutTime() throws Exception {
		UUID keyD = UUID.randomUUID();
		UUID placeD = UUID.randomUUID();
		insertPlace(placeD, "D 오륙도", 35.08, 129.12, OffsetDateTime.now(ZoneOffset.UTC));
		insertItemWithoutTime(latestVersionId(), keyD, 0, 4, placeD);

		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString(), keyD.toString()), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.warnings.length()").value(0))
				.andExpect(jsonPath("$.data.notChecked.length()").value(1))
				.andExpect(jsonPath("$.data.notChecked[0].check").value("OPENING_HOURS"))
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NO_ITEM_TIME"));
	}

	private ResultActions reorderDay(int dayIndex, List<String> itemKeys, int baseVersion) throws Exception {
		String body = "{\"itemKeys\":" + toJsonArray(itemKeys) + ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/days/{dayIndex}/reorder",
				this.itineraryId, dayIndex)
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

	private void insertTrip(UUID tripId, UUID ownerId, String startDate, String endDate,
			Double originLat, Double originLng, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, origin_lat, "
						+ "origin_lng, created_at, updated_at) VALUES (?, ?, ?::date, ?::date, 1, ?, ?, ?, ?)",
				tripId, ownerId, startDate, endDate, originLat, originLng, now, now);
	}

	private void insertMember(UUID tripId, UUID userId, String role, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, now);
	}

	private void insertPlace(UUID placeId, String nameKo, double lat, double lng, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, ?, ?, ?)",
				placeId, nameKo, lat, lng, now);
	}

	private UUID insertVersion(UUID itineraryId, UUID ownerId, int version) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, NULL, 'CREATE', ?, ?, now())",
				versionId, itineraryId, version, ownerId, "req_seed_" + version);
		this.jdbc.update("UPDATE itineraries SET latest_version = ? WHERE itinerary_id = ?", version, itineraryId);
		return versionId;
	}

	private UUID latestVersionId() {
		return this.jdbc.queryForObject(
				"SELECT v.itinerary_version_id FROM itinerary_versions v JOIN itineraries i "
						+ "ON i.itinerary_id = v.itinerary_id AND i.latest_version = v.version "
						+ "WHERE v.itinerary_id = ?",
				UUID.class, this.itineraryId);
	}

	private void insertItem(UUID versionId, UUID itemKey, int dayIndex, int sequence, UUID placeId,
			String start, String end) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, '2026-09-10', ?, ?, ?::time, ?::time, 120, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, dayIndex, sequence, placeId, start, end);
	}

	/** 시각이 안 정해진 항목 — 언제 가는지 모르면 문이 열렸는지 물어볼 수가 없다. */
	private void insertItemWithoutTime(UUID versionId, UUID itemKey, int dayIndex, int sequence, UUID placeId) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, "
						+ "data_status, created_at) "
						+ "VALUES (?, ?, ?, ?, '2026-09-10', ?, ?, NULL, NULL, 120, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, dayIndex, sequence, placeId);
	}
}
