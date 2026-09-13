package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.presentation.TripController;
import com.gabolle.backend.trip.presentation.TripExceptionHandler;
import com.gabolle.testslice.TripSliceApplication;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 앱이 실제로 보내는 본문으로 여행을 만든다 — S15P21E201-665.
 *
 * <h2>🔴 이 테스트가 없어서 실제 앱은 여행을 만들 수 없었다</h2>
 * 여행 생성 테스트가 전부 대문자 차원({@code CATEGORY})으로 만들었다. 앱은 소문자 camelCase
 * ({@code category}, {@code touristPreference} …)로 보내고, DB CHECK 는 대문자만 받는다.
 * 그 사이를 컨트롤러가 그대로 통과시켜서 실제 요청은 전부 CHECK 위반으로 롤백됐는데,
 * 어느 테스트도 앱 모양으로 컨트롤러를 통과시키지 않아 아무도 몰랐다.
 *
 * <p>그래서 이 본문은 {@code frontend/src/api/tripApi.ts} 의 {@code toCreateTripPayload} 가
 * 만드는 것과 <b>같은 모양</b>이다 — 차원 이름 아홉 개, 소문자 camelCase, {@code transport}
 * 포함, 답 안 한 차원은 {@code UNKNOWN} 에 {@code value: null}, 값은 {@code JSON.stringify}
 * 문자열. 제약({@code constraints})은 비워 둔다 — 이 결함은 취향 쪽이고 제약은 별도 테스트가 있다.
 *
 * <p>단정은 응답이 아니라 <b>표</b>로 한다. 응답이 201 이어도 취향이 어떻게 저장됐는지는
 * {@code preference_answer} 를 읽어야 안다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripCreateAppPayloadIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripController tripController;

	@Autowired
	private TripExceptionHandler tripExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.tripController)
				.setControllerAdvice(this.tripExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.userId = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				this.userId, now, now);
	}

	private Authentication asUser() {
		return new UsernamePasswordAuthenticationToken(this.userId.toString(), null, List.of());
	}

	/**
	 * {@code tripApi.ts} 의 {@code toCreateTripPayload} 와 같은 모양. 차원 이름은 그 파일의
	 * 문자열 그대로다.
	 *
	 * <p>🔴 2026-09-14 — {@code category} 값을 {@code ["BEACH","CAFE"]} 에서 바꿨다. <b>그 두
	 * 낱말은 앱에서 온 것이 아니었다</b> — 앱의 어휘는 여섯이다({@code SEA_BEACH}·{@code CITY}·
	 * {@code CAFE_HEALING}·{@code CULTURE_TEMPLE}·{@code FOOD}·{@code NATURE_WALK}).
	 * S15P21E201-915 가 {@code CATEGORY} 에 사전 강제를 걸었으므로 <b>지어낸 낱말로 되돌리면
	 * DB 가 거부한다.</b>
	 *
	 * <p>🔴 2026-09-10 — 출발지 좌표를 실제 값으로 바꿨다. 그전까지 여기에 {@code null} 이
	 * 박혀 있었는데, 그것은 앱이 좌표를 받아 두고도 안 보내던 결함(S15P21E201-791)을 <b>사실로
	 * 고정</b>하고 있던 것이다. 그 결함이 고쳐졌으므로(!479) 이 본문도 따라간다.
	 *
	 * <p>고정한 본문이 낡으면 검사는 초록인데 지키는 것이 없다 — 앱이 실제로 보내는 모양이
	 * 아니라 <b>예전에 보내던 모양</b>을 지키게 되기 때문이다. 이 파일의 존재 이유가 정확히
	 * 그 어긋남을 잡는 것이라, 프런트가 요청 모양을 바꾸면 여기도 같이 바꾼다.
	 */
	private static String appPayload(String transportDimensionName) {
		return """
				{
				  "startDate": "2026-09-10",
				  "finishDate": "2026-09-12",
				  "originLat": 35.1587,
				  "originLng": 129.1604,
				  "budgetKrw": 100000,
				  "partySize": 1,
				  "timeWindow": "09:00-18:00",
				  "timezone": "Asia/Seoul",
				  "preferences": [
				    { "dimension": "category",          "value": "[\\"SEA_BEACH\\",\\"CAFE_HEALING\\"]", "answerStatus": "SELECTED" },
				    { "dimension": "atmosphere",        "value": null, "answerStatus": "UNKNOWN" },
				    { "dimension": "locality",          "value": null, "answerStatus": "UNKNOWN" },
				    { "dimension": "quietness",         "value": null, "answerStatus": "UNKNOWN" },
				    { "dimension": "touristPreference", "value": null, "answerStatus": "UNKNOWN" },
				    { "dimension": "foodPreference",    "value": null, "answerStatus": "UNKNOWN" },
				    { "dimension": "%s",                "value": "\\"TRANSIT\\"", "answerStatus": "SELECTED" },
				    { "dimension": "slopePreference",   "value": null, "answerStatus": "UNKNOWN" },
				    { "dimension": "shadePreference",   "value": null, "answerStatus": "UNKNOWN" }
				  ],
				  "constraints": []
				}
				""".formatted(transportDimensionName);
	}

	@Test
	@DisplayName("🔴 앱 본문 그대로 보내면 201 이고, 취향 8행이 대문자 어휘로 저장된다(transport 제외)")
	void appPayloadCreatesTripAndStoresUppercaseDimensions() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/trips")
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asUser())
						.content(appPayload("transport")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.tripId").exists())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID tripId = UUID.fromString(body.path("data").path("tripId").asText());

		// 여행이 실제로 남았다 — 예전에는 취향 CHECK 위반으로 이 행까지 롤백됐다.
		Integer trips = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM trip WHERE trip_id = ?", Integer.class, tripId);
		assertThat(trips).isEqualTo(1);

		List<String> storedDimensions = jdbcTemplate.queryForList(
				"SELECT pa.dimension FROM preference_answer pa "
						+ "JOIN preference_snapshot ps ON ps.preference_snapshot_id = pa.preference_snapshot_id "
						+ "WHERE ps.trip_id = ? ORDER BY pa.dimension",
				String.class, tripId);

		// 아홉 개를 보냈고 transport 는 travel_modes 로 갔으니 여덟 개, 전부 CHECK 어휘다.
		assertThat(storedDimensions).containsExactly(
				"ATMOSPHERE", "CATEGORY", "FOOD_PREFERENCE", "LOCALITY",
				"QUIETNESS", "SHADE_PREFERENCE", "SLOPE_PREFERENCE", "TOURIST_PREFERENCE");

		// transport 답은 스냅샷이 아니라 여행의 이동수단 칸에 있다 (S15P21E201-664 배선).
		String travelModes = jdbcTemplate.queryForObject(
				"SELECT array_to_string(travel_modes, ',') FROM trip WHERE trip_id = ?", String.class, tripId);
		assertThat(travelModes).isEqualTo("BUS,SUBWAY");
	}

	@Test
	@DisplayName("이미 대문자 어휘로 보내도 그대로 통과한다 — 기존 클라이언트를 깨지 않는다")
	void vocabularyPayloadStillWorks() throws Exception {
		String payload = appPayload("transport")
				.replace("\"category\"", "\"CATEGORY\"")
				.replace("\"touristPreference\"", "\"TOURIST_PREFERENCE\"");

		mockMvc.perform(post("/api/v1/trips")
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asUser())
						.content(payload))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("모르는 차원 이름은 400 이고 원문이 fields 에 남는다 — 여행은 만들어지지 않는다")
	void unknownDimensionIsRejectedAndNothingIsStored() throws Exception {
		Integer before = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM trip WHERE owner_user_id = ?", Integer.class, this.userId);

		mockMvc.perform(post("/api/v1/trips")
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asUser())
						.content(appPayload("vibe")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0]").value(org.hamcrest.Matchers.containsString("vibe")));

		Integer after = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM trip WHERE owner_user_id = ?", Integer.class, this.userId);
		assertThat(after).isEqualTo(before);
	}
}
