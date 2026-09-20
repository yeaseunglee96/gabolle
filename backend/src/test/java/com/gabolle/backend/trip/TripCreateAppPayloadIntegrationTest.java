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
 * 앱이 실제로 보내는 본문으로 여행을 만든다 — 다른 생성 테스트는 대문자 차원으로 부르지만 앱은
 * 소문자 camelCase 로 보내고 DB CHECK 는 대문자만 받는다.
 *
 * <p>단정은 응답이 아니라 표로 한다 — 201 이어도 취향이 어떻게 저장됐는지는
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
	 * 문자열 그대로이고, {@code category} 값은 앱의 어휘 여섯 개 중에서만 고른다 — 지어낸 낱말을
	 * 넣으면 DB 가 거부한다.
	 *
	 * <p>이 본문이 낡으면 검사는 초록인데 지키는 것이 없다. 프런트가 요청 모양을 바꾸면 여기도
	 * 같이 바꾼다.
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

		// 여행이 실제로 남았다 — 취향이 CHECK 를 어기면 이 행까지 롤백된다.
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

		// transport 답은 스냅샷이 아니라 여행의 이동수단 칸에 있다.
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
