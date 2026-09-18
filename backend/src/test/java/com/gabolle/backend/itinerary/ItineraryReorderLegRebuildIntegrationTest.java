package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
import org.springframework.beans.factory.annotation.Qualifier;
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

import com.gabolle.backend.itinerary.application.ItineraryEditService;
import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.ItineraryEditController;
import com.gabolle.backend.itinerary.presentation.ItineraryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 순서 바꾸기 뒤 이동시간을 다시 채우는가 — S15P21E201-755.
 *
 * <p>{@link ItineraryReorderIntegrationTest} 는 이미 순서 바꾸기의 권한·검증(409·400·403·404)을
 * 잰다. 여기서는 그 옆에서 <b>이 티켓이 새로 보장하는 것</b>만 잰다 — 순서를 바꾼 뒤 그날 구간이
 * 다시 채워지는가, 그 값이 새 순서를 따라가는가, 실패하면 ESTIMATED 로 표시되는가, 다른 날은
 * 그대로인가, 그리고 여행·좌표를 못 찾아도 순서 바꾸기 자체는 성공하는가.
 *
 * <p>이 슬라이스({@code ItinerarySliceApplication})는 {@code route} 패키지를 스캔하지 않아
 * {@code TravelTimePort} 빈이 원래 없다 — {@link ItineraryLegPlanner#measure} 가 그 경우
 * {@code TravelTime.unknown()} 으로 답한다. 그러면 좌표가 있어도 데이터 상태가 항상
 * {@code UNKNOWN} 으로만 나와 "길찾기가 실패하면 ESTIMATED" 갈래(3번)를 볼 수 없다. 그래서
 * {@link FakeTravelTimeOverride} 로 좌표가 있으면 어림값+ESTIMATED 를, 좌표가 없으면
 * {@code unknown()} 을 답하는 가짜를 세운다 — 실제 어댑터({@code RouteTravelTimeAdapter})가
 * 예외 대신 상태값으로 답하는 계약을 그대로 흉내낸 것이다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@Import({
		ItineraryReorderLegRebuildIntegrationTest.FakeTravelTimeOverride.class,
		ItineraryReorderLegRebuildIntegrationTest.MissingTripOverride.class
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryReorderLegRebuildIntegrationTest {

	/**
	 * 길찾기가 "실패해서 어림값" 을 답하는 상황을 확정적으로 재현하는 가짜 문 —
	 * {@code TravelTimePort} 는 예외를 던지지 않고 상태값으로 실패를 알린다고 그 인터페이스
	 * 주석에 적혀 있다. 좌표가 하나라도 없으면 실제 어댑터와 같이 {@code unknown()} 이다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class FakeTravelTimeOverride {

		@Bean
		@Primary
		TravelTimePort estimatingOnlyTravelTimePort() {
			return (fromLat, fromLng, toLat, toLng, mode) -> {
				if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
					return TravelTime.unknown();
				}
				return new TravelTime(1000, 15, com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus.ESTIMATED);
			};
		}
	}

	/**
	 * {@code TripRepository.findById} 가 딱 하나의 {@link #HIDDEN_TRIP_ID} 에서만 "없음" 을
	 * 답하게 만드는 대역 — "여행을 못 찾는다" 를 재현한다.
	 *
	 * <p><b>이 트립이 실제로 없다는 뜻이 아니다.</b> {@code itineraries.trip_id} 에 FK 가 걸려
	 * 있어 존재하지 않는 여행을 가리키는 일정 행 자체를 만들 수 없다. 그래서 실제로는
	 * {@code trip} 표에 그 행을 넣어 두고, 이 대역이 <b>그 존재를 숨긴다</b> — 결과는
	 * {@code tripRepository.findById(그 id)} 가 비어 있는 것으로 보이는 것과 같다. 나머지
	 * tripId 는 전부 진짜 구현({@code jpaTripRepository})으로 그대로 넘어간다 — 다른 검사가
	 * 이 대역 때문에 깨지면 안 된다.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class MissingTripOverride {

		@Bean
		@Primary
		TripRepository tripRepositoryHidingOneTrip(@Qualifier("jpaTripRepository") TripRepository real) {
			return (TripRepository) Proxy.newProxyInstance(
					TripRepository.class.getClassLoader(),
					new Class<?>[] { TripRepository.class },
					(proxy, method, args) -> {
						if ("findById".equals(method.getName()) && HIDDEN_TRIP_ID.toString().equals(args[0])) {
							return java.util.Optional.empty();
						}
						try {
							return method.invoke(real, args);
						}
						catch (InvocationTargetException wrapped) {
							throw wrapped.getCause();
						}
					});
		}
	}

	/**
	 * 고정 리터럴이 아니라 JVM 이 뜰 때 한 번 뽑는다 — 영속 테스트 DB(같은 서버를 여러
	 * 실행이 공유)에 같은 UUID 행이 남아 중복 키로 부딪히는 일을 막는다.
	 */
	private static final UUID HIDDEN_TRIP_ID = UUID.randomUUID();

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
	private ItineraryEditService editService;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID itineraryId;

	private UUID placeA;

	private UUID placeB;

	private UUID placeC;

	private UUID placeZ;

	private UUID keyA;

	private UUID keyB;

	private UUID keyC;

	private UUID keyZ;

	/**
	 * 0일차에 좌표가 있는 셋(A·B·C), 1일차에 하나(Z). 1판에 두 날 모두 구간이 이미 있다 —
	 * "생성 때 만들어진 구간" 을 흉내낸 것이고, 여기 적은 값(거리·시간·상태)이 다음 판에서
	 * 그대로 남아 있는지(1일차) 또는 새로 덮이는지(0일차)를 가른다.
	 */
	@BeforeEach
	void seedThreeCoordinatedPlacesOnDayZeroAndOneOnDayOne() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.placeA = UUID.randomUUID();
		this.placeB = UUID.randomUUID();
		this.placeC = UUID.randomUUID();
		this.placeZ = UUID.randomUUID();
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();
		this.keyC = UUID.randomUUID();
		this.keyZ = UUID.randomUUID();

		createUser(this.ownerId, now);
		insertTrip(tripId, this.ownerId, "2026-09-10", "2026-09-11", 35.10, 129.10, now);
		insertMember(tripId, this.ownerId, "OWNER", now);

		// 여행 출발지(origin)에도 좌표를 준다 — 안 주면 그날의 첫 구간(from=null, 출발지
		//    좌표를 쓴다)만 좌표 없음으로 빠져서 "채워져 있다"(1번)를 셋 다 확인할 수 없다.
		insertPlaceWithCoordinates(this.placeA, "A 해운대해수욕장", 35.15, 129.16, now);
		insertPlaceWithCoordinates(this.placeB, "B 광안리해변", 35.16, 129.12, now);
		insertPlaceWithCoordinates(this.placeC, "C 태종대", 35.05, 129.09, now);
		insertPlaceWithCoordinates(this.placeZ, "Z 감천문화마을", 35.10, 129.01, now);

		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID v1 = insertVersion(this.itineraryId, this.ownerId, 1, null, "CREATE");
		insertItem(v1, this.keyA, 0, 1, this.placeA, "09:00", "11:00");
		insertItem(v1, this.keyB, 0, 2, this.placeB, "11:00", "13:00");
		insertItem(v1, this.keyC, 0, 3, this.placeC, "13:00", "15:00");
		insertItem(v1, this.keyZ, 1, 1, this.placeZ, "09:00", "10:00");

		// 0일차 — 옛 순서(A,B,C) 기준의 구간. 재배열 뒤 이 값이 안 남아 있어야 한다(2번).
		insertLeg(v1, 0, 1, null, this.placeA, 111, 5, "VERIFIED", now);
		insertLeg(v1, 0, 2, this.placeA, this.placeB, 222, 8, "VERIFIED", now);
		insertLeg(v1, 0, 3, this.placeB, this.placeC, 333, 10, "VERIFIED", now);
		// 1일차 — 손대지 않아야 하는 구간(4번).
		insertLeg(v1, 1, 1, null, this.placeZ, 777, 12, "VERIFIED", now);
	}

	@Test
	@DisplayName("순서를 바꾼 뒤 일정을 조회하면 그날 방문지에 이동시간이 다시 채워져 있다")
	void reorderRefillsTravelTimeForTheChangedDay() throws Exception {
		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk());

		List<Map<String, Object>> day0Legs = legsOf(2, 0);

		// 이전(-755 전) 코드는 구간을 버리기만 하고 다시 안 채워서 여기가 통째로 비었다 —
		//    그 결함의 모양 그대로 되돌아가지 않았는지가 이 검사의 핵심이다.
		assertThat(day0Legs).as("항목 셋에 구간도 셋이어야 한다 — 하나라도 비면 다시 안 채운 것이다")
				.hasSize(3);
		assertThat(day0Legs).extracting((row) -> row.get("distance_m"))
				.as("거리가 비어 있으면 화면에 '이동시간 없음'이 다시 나타난다")
				.doesNotContainNull();
	}

	@Test
	@DisplayName("그 값이 새 순서 기준이다 — 바꾸기 전 순서의 거리가 남아 있지 않다")
	void refilledLegsFollowTheNewOrderNotTheOldOne() throws Exception {
		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk());

		List<Map<String, Object>> day0Legs = legsOf(2, 0);

		// 거리 숫자만 비교하지 않는다(가짜 문이 좌표만 있으면 항상 같은 값을 주므로 숫자로는
		//    "달라졌다"를 못 본다). from/to 장소 쌍의 정체성으로 새 순서(C,A,B)를 확인한다 —
		//    옛 순서(A,B,C)의 어느 구간과도 자리·짝이 같지 않다.
		assertThat(day0Legs).extracting(
						(row) -> row.get("from_place_id"),
						(row) -> row.get("to_place_id"))
				.as("첫 구간은 출발지→C, 다음은 C→A, 마지막은 A→B 여야 한다")
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(null, this.placeC.toString()),
						org.assertj.core.groups.Tuple.tuple(this.placeC.toString(), this.placeA.toString()),
						org.assertj.core.groups.Tuple.tuple(this.placeA.toString(), this.placeB.toString()));

		// 옛 판(1판)의 구간은 그대로 남아 있다 — 판을 지우지 않고 새로 쌓는 구조이기 때문이다.
		// 이 검사가 보는 것은 "새 판이 새 순서를 따라가는가"이지 "옛 판이 사라졌는가"가 아니다.
		List<Map<String, Object>> oldDay0Legs = legsOf(1, 0);
		assertThat(oldDay0Legs).extracting((row) -> row.get("to_place_id"))
				.containsExactly(this.placeA.toString(), this.placeB.toString(), this.placeC.toString());
	}

	@Test
	@DisplayName("길찾기가 실패하면 어림값이 채워지고 ESTIMATED 로 표시된다")
	void whenRoutingCannotAnswerLegsAreMarkedEstimated() throws Exception {
		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk());

		List<Map<String, Object>> day0Legs = legsOf(2, 0);

		// FakeTravelTimeOverride 가 좌표만 있으면 늘 "실패해서 어림값" 을 답하도록 세워 뒀다 —
		// 진짜 경로 조회 없이도 이 갈래(RouteTravelTimeAdapter 가 직선거리로 물러서는 경우와
		// 같은 모양)를 확정적으로 재현한다.
		assertThat(day0Legs).extracting((row) -> row.get("data_status"))
				.as("셋 다 ESTIMATED 여야 한다 — 하나라도 VERIFIED/UNKNOWN 이면 상태가 안 실렸다는 뜻이다")
				.containsExactly("ESTIMATED", "ESTIMATED", "ESTIMATED");
		assertThat(day0Legs).extracting((row) -> row.get("distance_m"), (row) -> row.get("duration_min"))
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(1000, 15),
						org.assertj.core.groups.Tuple.tuple(1000, 15),
						org.assertj.core.groups.Tuple.tuple(1000, 15));
	}

	@Test
	@DisplayName("다른 날의 구간은 그대로다")
	void otherDaysLegsStayUntouched() throws Exception {
		reorderDay(0, List.of(this.keyC.toString(), this.keyA.toString(), this.keyB.toString()), 1)
				.andExpect(status().isOk());

		List<Map<String, Object>> day1Legs = legsOf(2, 1);

		// 값이 우연히 같아 보이는 게 아니라 "이 판에서 아예 다시 안 만들었다"를 보이려면
		//    가짜 문의 값(1000/15/ESTIMATED)이 아니라 seed 때 넣은 원래 값(777/12)이 그대로
		//    있어야 한다 — 다시 만들었다면 가짜 문 값으로 덮였을 것이다.
		//
		// data_status 도 함께 본다. 이 검사를 처음 쓸 때 여기가 null 로 나와서 결함을 하나
		// 찾았다 — 다른 날 구간을 그대로 복사하는 경로(ItineraryRevision.copyLegs)가
		// dataStatus 를 안 받는 옛 생성자를 써서 "잰 값인가 어림값인가" 를 매 편집마다
		// 지우고 있었다. 순서 바꾸기만이 아니라 고정·해제·장소 추가·되돌리기가 전부 같았다.
		//
		// 고쳤고(S15P21E201-755), 이 줄이 그 고침을 지킨다. 이 표시를 잃으면 화면이 어림값을
		// 잰 값처럼 그린다 — S15P21E201-179 가 이 칸을 만든 이유가 그것이다.
		assertThat(day1Legs).hasSize(1);
		assertThat(day1Legs.get(0).get("data_status"))
				.as("다른 날 구간을 복사할 때 잰 값인지 어림값인지가 지워지면 안 된다")
				.isEqualTo("VERIFIED");
		Map<String, Object> leg = day1Legs.get(0);
		assertThat(leg.get("to_place_id")).isEqualTo(this.placeZ.toString());
		assertThat(leg.get("distance_m")).isEqualTo(777);
		assertThat(leg.get("duration_min")).isEqualTo(12);
	}

	@Test
	@DisplayName("여행을 찾을 수 없어도 순서 바꾸기는 성공한다 — 구간만 안 채워진다")
	void reorderSucceedsEvenWhenTheTripCannotBeFound() {
		// 컨트롤러(HTTP)를 거치지 않고 서비스를 직접 부른다. 접근 권한 판정
		//    (ItineraryAccess.requireEditor → TripQueryService.get)도 TripRepository 를
		//    쓰므로, HTTP 로 가면 이 여행이 "없다"는 이유로 403/404 로 먼저 걸려서 정작 보고
		//    싶은 자리(ItineraryEditService.reorderDay 안의 트립-없음 갈래)에 닿지 못한다.
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID hiddenItineraryId = UUID.randomUUID();
		UUID keyP = UUID.randomUUID();
		UUID keyQ = UUID.randomUUID();

		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-11', 1, ?, ?)",
				HIDDEN_TRIP_ID, this.ownerId, now, now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				hiddenItineraryId, HIDDEN_TRIP_ID, now);
		UUID v1 = insertVersion(hiddenItineraryId, this.ownerId, 1, null, "CREATE");
		insertItem(v1, keyP, 0, 1, this.placeA, "09:00", "11:00");
		insertItem(v1, keyQ, 0, 2, this.placeB, "11:00", "13:00");

		ItineraryVersion saved = this.editService.reorderDay(hiddenItineraryId.toString(), 0,
				List.of(keyQ.toString(), keyP.toString()), 1, this.ownerId.toString()).version();

		assertThat(saved.version()).isEqualTo(2);

		List<Map<String, Object>> items = itemsOf(hiddenItineraryId, 2);
		assertThat(items).extracting((row) -> row.get("item_key"))
				.as("여행을 못 찾아도 순서 자체는 요청대로 바뀌어야 한다")
				.containsExactly(keyQ.toString(), keyP.toString());

		List<Map<String, Object>> legs = legsOf(hiddenItineraryId, 2, 0);
		assertThat(legs).as("이동시간만 못 채운다 — 순서 바꾸기가 실패하는 것과는 다르다").isEmpty();
	}

	@Test
	@DisplayName("그날 방문지에 좌표가 없어도 순서 바꾸기는 성공한다 — 구간만 안 채워진다")
	void reorderSucceedsEvenWithoutCoordinates() throws Exception {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID tripId = UUID.randomUUID();
		UUID itineraryId = UUID.randomUUID();
		UUID placeP = UUID.randomUUID();
		UUID placeQ = UUID.randomUUID();
		UUID keyP = UUID.randomUUID();
		UUID keyQ = UUID.randomUUID();

		insertTrip(tripId, this.ownerId, "2026-09-10", "2026-09-11", null, null, now);
		insertMember(tripId, this.ownerId, "OWNER", now);
		// lat/lng 을 안 넣는다 — "그날 방문지에 좌표가 없다"를 그대로 재현한다.
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'P 무좌표 장소', ?)",
				placeP, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'Q 무좌표 장소', ?)",
				placeQ, now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				itineraryId, tripId, now);
		UUID v1 = insertVersion(itineraryId, this.ownerId, 1, null, "CREATE");
		insertItem(v1, keyP, 0, 1, placeP, "09:00", "11:00");
		insertItem(v1, keyQ, 0, 2, placeQ, "11:00", "13:00");

		reorderDay(itineraryId, 0, List.of(keyQ.toString(), keyP.toString()), 1)
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.data.version").value(2));

		List<Map<String, Object>> legs = legsOf(itineraryId, 2, 0);
		// 구간 자체는 만들어진다(방문지 수만큼) — 다만 잴 게 없으니 값이 비어 있다.
		assertThat(legs).hasSize(2);
		assertThat(legs).extracting((row) -> row.get("distance_m")).containsOnlyNulls();
		assertThat(legs).extracting((row) -> row.get("duration_min")).containsOnlyNulls();
		assertThat(legs).extracting((row) -> row.get("data_status"))
				.as("좌표가 없어 아무것도 못 쟀다 — 어림값(ESTIMATED)이 아니라 UNKNOWN 이어야 한다")
				.containsExactly("UNKNOWN", "UNKNOWN");
	}

	// ---- 도우미 ----
	// 기존 통합 검사(ItineraryReorderIntegrationTest)의 방식을 그대로 따른다 —
	//    createUser·insertMember·insertVersion·insertItem 은 그 검사의 헬퍼와 같은 SQL 이다.

	private ResultActions reorderDay(int dayIndex, List<String> itemKeys, int baseVersion) throws Exception {
		return reorderDay(this.itineraryId, dayIndex, itemKeys, baseVersion);
	}

	private ResultActions reorderDay(UUID targetItineraryId, int dayIndex, List<String> itemKeys, int baseVersion)
			throws Exception {
		String body = "{\"itemKeys\":" + toJsonArray(itemKeys) + ",\"baseVersion\":" + baseVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/days/{dayIndex}/reorder",
				targetItineraryId, dayIndex)
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

	private void insertPlaceWithCoordinates(UUID placeId, String nameKo, double lat, double lng, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, ?, ?, ?)",
				placeId, nameKo, lat, lng, now);
	}

	private UUID insertVersion(UUID itineraryId, UUID ownerId, int version, Integer baseVersion, String operation) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now())",
				versionId, itineraryId, version, baseVersion, operation, ownerId, "req_seed_" + version);
		this.jdbc.update("UPDATE itineraries SET latest_version = ? WHERE itinerary_id = ?", version, itineraryId);
		return versionId;
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

	private void insertLeg(UUID versionId, int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId,
			int distanceM, int durationMin, String dataStatus, OffsetDateTime now) {
		this.jdbc.update(
				"INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, "
						+ "from_place_id, to_place_id, travel_mode, distance_m, duration_min, walking_meters, "
						+ "data_status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'WALK', ?, ?, ?, ?, ?)",
				UUID.randomUUID(), versionId, dayIndex, sequence, fromPlaceId, toPlaceId, distanceM, durationMin,
				distanceM, dataStatus, now);
	}

	private List<Map<String, Object>> itemsOf(UUID itineraryId, int version) {
		return this.jdbc.queryForList(
				"SELECT i.item_key::text AS item_key, i.sequence, i.day_index FROM itinerary_item i "
						+ "JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? ORDER BY i.day_index, i.sequence",
				itineraryId, version);
	}

	private List<Map<String, Object>> legsOf(int version, int dayIndex) {
		return legsOf(this.itineraryId, version, dayIndex);
	}

	private List<Map<String, Object>> legsOf(UUID itineraryId, int version, int dayIndex) {
		return this.jdbc.queryForList(
				"SELECT l.from_place_id::text AS from_place_id, l.to_place_id::text AS to_place_id, "
						+ "l.distance_m, l.duration_min, l.data_status, l.sequence FROM itinerary_leg l "
						+ "JOIN itinerary_versions v ON v.itinerary_version_id = l.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? AND l.day_index = ? ORDER BY l.sequence",
				itineraryId, version, dayIndex);
	}
}
