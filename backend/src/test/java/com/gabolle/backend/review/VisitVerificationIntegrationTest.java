package com.gabolle.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.review.presentation.ReviewExceptionHandler;
import com.gabolle.backend.review.presentation.VisitVerificationController;
import com.gabolle.backend.review.support.ReviewPostgresIntegrationTest;

/**
 * 방문 인증 — S15P21E201-279.
 *
 * <p>완료 기준 넷 중 앞의 셋(같은 위치는 인증, 1km 는 거절, 정확도 200m 는 재시도)은 응답으로
 * 확인하고, 마지막(좌표가 어디에도 없다)은 실제 표를 직접 훑어 확인한다({@link
 * #verifiedRowLeavesNoCoordinateAnywhereInTheTable()}).
 *
 * <p>표준 도(latitude) 1도는 자오선을 따라가면 정확히 {@code EARTH_RADIUS_METERS * 라디안} 이다
 * (경도차가 0이면 하버사인 공식이 근사 없이 그 등식으로 접힌다 — {@link #northOf} 참고). 그래서
 * 경계값 테스트(199m·201m)를 오차 없이 만들 수 있다.
 */
class VisitVerificationIntegrationTest extends ReviewPostgresIntegrationTest {

	/** {@code GeoDistance.EARTH_RADIUS_METERS} 와 같은 값. 그 상수는 private 이라 복제했다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	@Autowired
	private VisitVerificationController controller;

	@Autowired
	private ReviewExceptionHandler exceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private PlaceFixture placeFixture;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
				.setControllerAdvice(this.exceptionHandler)
				.build();
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		this.userId = UUID.randomUUID();
		createUser(this.userId);
	}

	private void createUser(UUID id) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, now, now);
	}

	private Authentication as(UUID id) {
		return new UsernamePasswordAuthenticationToken(id.toString(), null, List.of());
	}

	/** 자오선(경도 불변) 위에서 북쪽으로 {@code meters} 만큼 떨어진 점. */
	private double[] northOf(double lat, double lng, double meters) {
		double latDelta = Math.toDegrees(meters / EARTH_RADIUS_METERS);
		return new double[] { lat + latDelta, lng };
	}

	private String requestBody(double lat, double lng, int accuracyM) {
		return "{\"lat\":" + lat + ",\"lng\":" + lng + ",\"accuracyM\":" + accuracyM + "}";
	}

	private int verificationRowCount(UUID placeId, UUID userId) {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_visit_verification WHERE place_id = ? AND user_id = ?", Integer.class,
				placeId, userId);
		return count == null ? 0 : count;
	}

	@Test
	@DisplayName("같은 좌표를 보내면 인증된다")
	void sameCoordinateIsVerified() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("해운대해수욕장", null, "BEACH", 35.1587, 129.1604);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(35.1587, 129.1604, 10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(true))
				.andExpect(jsonPath("$.data.status").value("VERIFIED"))
				.andExpect(jsonPath("$.data.distanceM").value(0));

		assertThat(verificationRowCount(placeId, this.userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("1km 떨어지면 거절되고 응답에 실제 거리가 들어 있다 — 인증 행은 만들어지지 않는다")
	void oneKilometerAwayIsRejectedWithDistance() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("해운대해수욕장", null, "BEACH", 35.1587, 129.1604);
		double[] farPoint = northOf(35.1587, 129.1604, 1000);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(farPoint[0], farPoint[1], 10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(false))
				.andExpect(jsonPath("$.data.status").value("TOO_FAR"))
				.andExpect(jsonPath("$.data.distanceM").value(1000));

		assertThat(verificationRowCount(placeId, this.userId)).isEqualTo(0);
	}

	@Test
	@DisplayName("경계값 — 199m 는 인증되고 201m 는 거절된다")
	void boundaryDistances() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("경계장소", null, "BEACH", 35.1587, 129.1604);

		double[] point199 = northOf(35.1587, 129.1604, 199);
		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(point199[0], point199[1], 10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(true))
				.andExpect(jsonPath("$.data.distanceM").value(199));

		UUID secondUser = UUID.randomUUID();
		createUser(secondUser);
		double[] point201 = northOf(35.1587, 129.1604, 201);
		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(secondUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(point201[0], point201[1], 10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(false))
				.andExpect(jsonPath("$.data.distanceM").value(201));
	}

	@Test
	@DisplayName("정확도 200m 를 보내면 재시도 응답이고 인증 행이 만들어지지 않는다 — 거리 판정 자체를 안 한다")
	void lowAccuracyTriggersRetryWithoutMeasuringDistance() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("정확도장소", null, "BEACH", 35.1587, 129.1604);

		// 좌표는 정확히 같은 곳이다 — 거리로는 인증될 상황이어도 정확도가 나쁘면 재지 않는다.
		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(35.1587, 129.1604, 200)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(false))
				.andExpect(jsonPath("$.data.status").value("LOW_ACCURACY"))
				.andExpect(jsonPath("$.data.distanceM").doesNotExist());

		assertThat(verificationRowCount(placeId, this.userId)).isEqualTo(0);
	}

	@Test
	@DisplayName("좌표가 없는 장소에 인증을 요청하면 422 다")
	void placeWithoutCoordinatesReturns422() throws Exception {
		UUID placeId = this.placeFixture.insertPlace("좌표없는장소", null, "BEACH", null, null);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(35.1587, 129.1604, 10)))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.error.code").value("PLACE_COORDINATES_MISSING"));

		assertThat(verificationRowCount(placeId, this.userId)).isEqualTo(0);
	}

	@Test
	@DisplayName("없는 장소는 404 다 (500 이 아니다)")
	void unknownPlaceReturns404() throws Exception {
		UUID missingPlaceId = UUID.randomUUID();

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", missingPlaceId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(35.1587, 129.1604, 10)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
	}

	/**
	 * 🔴 이 테스트가 완료 기준의 핵심이다 — "인증 뒤 데이터베이스 어디에도 좌표 값이 없다."
	 *
	 * <p>두 가지를 <b>따로</b> 확인한다. (1) 표 자체에 좌표를 담을 칸이 없다
	 * ({@code information_schema.columns}). (2) 실제 인증 행의 모든 칸 값을 문자열로 훑어 보낸
	 * 좌표 값이 어디에도 없다. 하나만 확인하면 "칸은 없는데 다른 칸에 우연히 같은 문자열이
	 * 박히는 경우" 나 "칸은 있는데 이번엔 비어 있는 경우" 를 놓칠 수 있다.
	 *
	 * <p>🔴 {@code table_schema = current_schema()} 를 반드시 넣는다 — 이 표에는 스키마 격리가
	 * 걸려 있어(테스트는 {@code rev_b}) 그것 없이 세면 다른 스키마의 동명 칸까지 섞여 든다.
	 *
	 * <p>인증 행이 실제로 만들어졌다는 것도 함께 잰다({@code rowCount == 1}) — 그러지 않으면
	 * 행이 하나도 없어도 "좌표가 없다" 는 훑기가 그냥 통과해 버린다.
	 */
	@Test
	@DisplayName("🔴 인증 뒤 DB 에 좌표가 없다 — 칸도 없고 값도 없다")
	void verifiedRowLeavesNoCoordinateAnywhereInTheTable() throws Exception {
		double sentLat = 35.1587;
		double sentLng = 129.1604;
		UUID placeId = this.placeFixture.insertPlace("좌표검증장소", null, "BEACH", sentLat, sentLng);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
						.principal(as(this.userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody(sentLat, sentLng, 10)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.verified").value(true));

		// 행이 실제로 만들어졌다 — 이게 없으면 아래 훑기가 빈 결과를 보고 통과한다.
		assertThat(verificationRowCount(placeId, this.userId)).isEqualTo(1);

		List<String> columnNames = this.jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns "
						+ "WHERE table_name = 'place_visit_verification' AND table_schema = current_schema()",
				String.class);
		assertThat(columnNames).isNotEmpty();
		for (String columnName : columnNames) {
			String lower = columnName.toLowerCase();
			assertThat(lower).doesNotContain("lat").doesNotContain("lng").doesNotContain("longitude")
					.doesNotContain("latitude");
		}

		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT * FROM place_visit_verification WHERE place_id = ? AND user_id = ?", placeId, this.userId);
		String sentLatText = String.valueOf(sentLat);
		String sentLngText = String.valueOf(sentLng);
		for (Map.Entry<String, Object> entry : row.entrySet()) {
			String value = String.valueOf(entry.getValue());
			assertThat(value).as("칸 %s 에 좌표 값이 남으면 안 된다", entry.getKey())
					.doesNotContain(sentLatText)
					.doesNotContain(sentLngText);
		}
	}
}
