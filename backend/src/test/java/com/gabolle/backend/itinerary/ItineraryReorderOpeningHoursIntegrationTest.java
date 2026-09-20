package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.gabolle.backend.place.service.PlaceTimeFactFilterPort;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 일정을 편집했을 때 영업시간 위반이 응답에 실리는가. 순서 바꾸기·장소 더하기·되돌리기
 * 세 경로가 같은 씨앗 데이터를 쓰므로 한 클래스에서 함께 잰다.
 *
 * <p>권한과 검증(409 · 400 · 403 · 404)은 {@link ItineraryReorderIntegrationTest} 가, 바뀐
 * 날의 이동시간은 {@link ItineraryReorderLegRebuildIntegrationTest} 가 잰다. 여기서는 경고가
 * 나가는지만 잰다.
 *
 * <p>영업시간 문은 {@link SwitchableOpeningHours} 로 답을 정할 수 있는 가짜를 세운다. 실제
 * 구현은 적재된 값을 읽어서, 장소마다 영업시간 JSON 을 심으면 값 모양이 이 검사의 주제보다
 * 커진다. 가짜도 실제 계약과 같은 세 갈래(연다·닫는다·모른다)를 돌려준다 — 두 갈래로 줄이면
 * 가장 틀리기 쉬운 「모른다」가 검사에서 빠진다.
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
	 * <p>장소마다 답을 따로 심을 수 있고, 심지 않은 장소는 {@code fallback} 을 받는다. 이 빈은
	 * 검사 클래스 하나에 하나뿐이라 {@code @BeforeEach} 에서 매번 되돌린다 — 안 되돌리면 앞
	 * 시험이 심어 둔 "닫힘" 이 다음 시험으로 흘러가고, 실행 순서에 따라 결과가 달라진다.
	 */
	static class SwitchableOpeningHours implements OpeningHoursFilterPort {

		private final Map<UUID, Answer> answers = new HashMap<>();

		private Answer fallback = Answer.OPEN;

		/** 되돌린다 — 전부 열려 있고 심어 둔 답은 없다. */
		void reset() {
			this.answers.clear();
			this.fallback = Answer.OPEN;
		}

		/** 이 장소들만 닫혀 있고 나머지는 열려 있다. */
		void closed(Set<UUID> places) {
			places.forEach((place) -> this.answers.put(place, Answer.CLOSED));
		}

		/** 이 장소들만 모른다. */
		void notCollected(Set<UUID> places) {
			places.forEach((place) -> this.answers.put(place, Answer.NOT_COLLECTED));
		}

		/** 심어 두지 않은 장소의 기본 답을 바꾼다 — 아무것도 안 넣은 상태를 재현한다. */
		void fallback(Answer answer) {
			this.fallback = answer;
		}

		@Override
		public Answer openAt(UUID placeId, OffsetDateTime at) {
			return this.answers.getOrDefault(placeId, this.fallback);
		}
	}

	/**
	 * 브레이크타임·라스트오더용 가짜 문. 이 파일은 영업시간만 재는데, 실제 구현을 그대로
	 * 두면 씨앗 장소에 그 값이 없어 매 항목마다 NOT_COLLECTED 가 섞여 {@code notChecked}
	 * 개수가 어긋난다. 기본값은 {@code OPEN} 이다.
	 */
	static class SwitchableTimeFact implements PlaceTimeFactFilterPort {

		private OpeningHoursFilterPort.Answer fallback = OpeningHoursFilterPort.Answer.OPEN;

		void reset() {
			this.fallback = OpeningHoursFilterPort.Answer.OPEN;
		}

		@Override
		public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, OffsetDateTime at) {
			return this.fallback;
		}

		@Override
		public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, OffsetDateTime at) {
			return this.fallback;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SwitchableOpeningHoursOverride {

		@Bean
		@Primary
		SwitchableOpeningHours switchableOpeningHours() {
			return new SwitchableOpeningHours();
		}

		@Bean
		@Primary
		SwitchableTimeFact switchableTimeFact() {
			return new SwitchableTimeFact();
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
	private SwitchableTimeFact timeFact;

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
	 * <p>순서를 바꿔도 시각표는 그 자리에 남고 항목만 자리를 옮겨 앉는다
	 * ({@code ItineraryRevision.withReorderedDay}). 그래서 C 를 맨 앞으로 보내면 C 가 09:00 을
	 * 받고, C 가 그 시각에 안 연다고 심어 두면 위반이 하나 확정된다.
	 */
	@BeforeEach
	void seedThreePlacesWithTimesOnDayZero() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		this.openingHours.reset();
		this.timeFact.reset();

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
		this.openingHours.closed(Set.of(this.placeC));

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
		this.openingHours.fallback(OpeningHoursFilterPort.Answer.NOT_COLLECTED);

		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.days[0].items[0].id").value(this.keyC.toString()))
				// 빈 경고 목록만 보내면 "확인했고 문제 없음" 으로 읽힌다. 왜 못 봤는지를 싣는다.
				.andExpect(jsonPath("$.data.warnings.length()").value(0))
				.andExpect(jsonPath("$.data.notChecked.length()").value(1))
				.andExpect(jsonPath("$.data.notChecked[0].check").value("OPENING_HOURS"))
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NOT_COLLECTED"));
	}

	@Test
	@DisplayName("🔴 한 곳은 닫혀 있고 다른 곳은 모르면 경고와 못 한 검사가 함께 온다")
	void reorderReportsBothWhenOnlySomePlacesHaveHours() throws Exception {
		this.openingHours.closed(Set.of(this.placeC));
		this.openingHours.notCollected(Set.of(this.placeA));

		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk())
				// 둘 중 하나만 올라오면 화면이 거짓말을 한다 — 경고만 오면 나머지를 확인한 것으로
				// 읽히고, 못 한 검사만 오면 실제 위반이 묻힌다.
				.andExpect(jsonPath("$.data.warnings.length()").value(1))
				.andExpect(jsonPath("$.data.warnings[0].placeId").value(this.placeC.toString()))
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
				// 시각을 모르면 영업시간·브레이크타임·라스트오더 셋 다 못 재고, 세 축이 각각
				// 따로 보고한다.
				.andExpect(jsonPath("$.data.notChecked.length()").value(3))
				.andExpect(jsonPath("$.data.notChecked[0].check").value("OPENING_HOURS"))
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NO_ITEM_TIME"))
				.andExpect(jsonPath("$.data.notChecked[1].check").value("BREAK_TIME"))
				.andExpect(jsonPath("$.data.notChecked[1].reason").value("NO_ITEM_TIME"))
				.andExpect(jsonPath("$.data.notChecked[2].check").value("LAST_ORDER_TIME"))
				.andExpect(jsonPath("$.data.notChecked[2].reason").value("NO_ITEM_TIME"));
	}

	@Test
	@DisplayName("장소를 더하면 그 날짜의 기존 항목 위반이 응답에 실린다")
	void addItemCarriesOpeningHoursForThatDay() throws Exception {
		UUID placeD = UUID.randomUUID();
		insertPlace(placeD, "D 범어사", 35.28, 129.06, OffsetDateTime.now(ZoneOffset.UTC));
		this.openingHours.closed(Set.of(this.placeC));

		addItem(placeD, 0, 1)
				.andExpect(status().isCreated())
				// 예전에는 이 두 칸이 함께 비어 나갔고 화면은 그것을 "확인했고 문제 없음" 으로 읽었다.
				.andExpect(jsonPath("$.data.warnings.length()").value(1))
				.andExpect(jsonPath("$.data.warnings[0].code").value("OPENING_HOURS_CLOSED"))
				.andExpect(jsonPath("$.data.warnings[0].placeId").value(this.placeC.toString()))
				// 더한 항목은 아직 시각이 없다. 그 사실도 함께 올라간다.
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NO_ITEM_TIME"));
	}

	@Test
	@DisplayName("더한 항목 자체는 시각이 없어 판정 대상이 아니다 — 그 사실을 못 한 검사로 알린다")
	void addedItemHasNoTimeYetSoItIsReportedAsUnchecked() throws Exception {
		UUID placeD = UUID.randomUUID();
		insertPlace(placeD, "D 범어사", 35.28, 129.06, OffsetDateTime.now(ZoneOffset.UTC));

		addItem(placeD, 0, 1)
				.andExpect(status().isCreated())
				// 시각은 그 날짜 재계산이 정하고 화면이 이어서 부른다. 그때까지는 판정할 수 없다 —
				// 여는 것으로 넘기면 화면이 "확인했고 문제 없음" 으로 읽는다.
				.andExpect(jsonPath("$.data.warnings.length()").value(0))
				// 시각을 모르면 영업시간·브레이크타임·라스트오더 셋 다 못 잰다.
				.andExpect(jsonPath("$.data.notChecked.length()").value(3))
				.andExpect(jsonPath("$.data.notChecked[0].check").value("OPENING_HOURS"))
				.andExpect(jsonPath("$.data.notChecked[0].reason").value("NO_ITEM_TIME"))
				.andExpect(jsonPath("$.data.notChecked[1].check").value("BREAK_TIME"))
				.andExpect(jsonPath("$.data.notChecked[1].reason").value("NO_ITEM_TIME"))
				.andExpect(jsonPath("$.data.notChecked[2].check").value("LAST_ORDER_TIME"))
				.andExpect(jsonPath("$.data.notChecked[2].reason").value("NO_ITEM_TIME"));
	}

	@Test
	@DisplayName("되돌리면 되살린 판 전체의 영업시간 판정이 실린다")
	void revertCarriesOpeningHoursForEveryDay() throws Exception {
		this.openingHours.closed(Set.of(this.placeC));
		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk());

		revertTo(1, 2)
				.andExpect(status().isCreated())
				// 되살린 판에도 C 가 있으므로 위반이 그대로 있다. 되돌리기가 위반을 없애 주지 않는다.
				.andExpect(jsonPath("$.data.warnings.length()").value(1))
				.andExpect(jsonPath("$.data.warnings[0].placeId").value(this.placeC.toString()));
	}

	private ResultActions addItem(UUID placeId, int dayIndex, int baseVersion) throws Exception {
		String body = "{\"placeId\":\"" + placeId + "\",\"dayIndex\":" + dayIndex
				+ ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/items", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(this.ownerId))
				.content(body));
	}

	private ResultActions revertTo(int toVersion, int baseVersion) throws Exception {
		String body = "{\"toVersion\":" + toVersion + ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/revert", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(this.ownerId))
				.content(body));
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
