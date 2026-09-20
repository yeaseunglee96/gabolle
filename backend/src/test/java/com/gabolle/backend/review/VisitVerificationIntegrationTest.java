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

import com.gabolle.backend.common.security.GlobalAuthExceptionHandler;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.review.presentation.ReviewExceptionHandler;
import com.gabolle.backend.review.presentation.VisitVerificationController;
import com.gabolle.backend.review.support.ReviewPostgresIntegrationTest;

/**
 * 위도만 움직이면(경도차 0) 하버사인 공식이 근사 없이 {@code 반지름 * 라디안} 으로 접힌다.
 * 그래서 {@link #northOf} 로 만든 좌표는 경계값(199m·201m)을 오차 없이 재현한다.
 */
class VisitVerificationIntegrationTest extends ReviewPostgresIntegrationTest {

	/** {@code GeoDistance.EARTH_RADIUS_METERS} 와 같은 값. 그 상수는 private 이라 복제했다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	@Autowired
	private VisitVerificationController controller;

	@Autowired
	private ReviewExceptionHandler exceptionHandler;

	@Autowired
	private GlobalAuthExceptionHandler globalAuthExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private PlaceFixture placeFixture;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
				// 정밀 위치 미동의는 AuthException(403) 으로 나간다. 리뷰 처리기만 걸면 번역되지
				// 않아 500 으로 보이므로 전역 처리기를 함께 등록한다.
				.setControllerAdvice(this.exceptionHandler, this.globalAuthExceptionHandler)
				.build();
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		this.userId = UUID.randomUUID();
		createUser(this.userId);
		grantPreciseLocation(this.userId);
	}

	private void createUser(UUID id) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, now, now);
	}

	/** 이것 없이는 방문 인증이 403 이다. 미동의 쪽은 다른 검사가 따로 잰다. */
	private void grantPreciseLocation(UUID id) {
		this.jdbcTemplate.update(
				"INSERT INTO user_consent (consent_id, user_id, consent_type, status, policy_version, decided_at) "
						+ "VALUES (?, ?, 'PRECISE_LOCATION', 'GRANTED', '2026-01', ?)",
				UUID.randomUUID(), id, OffsetDateTime.now(ZoneOffset.UTC));
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
	@DisplayName("🔴 정밀 위치에 동의하지 않았으면 방문 인증이 거절된다 — 행도 안 남는다")
	void verificationIsRefusedWithoutPreciseLocationConsent() throws Exception {
		UUID stranger = UUID.randomUUID();
		createUser(stranger);
		UUID placeId = this.placeFixture.insertPlace("동의없음장소", null, "BEACH", 35.1587, 129.1604);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(stranger))
				.content(requestBody(35.1587, 129.1604, 10)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("PRECISE_LOCATION_CONSENT_REQUIRED"));

		assertThat(verificationRowCount(placeId, stranger)).isZero();
	}

	@Test
	@DisplayName("🔴 철회가 옛 동의를 이긴다 — 방침 판이 같아도 나중 결정이 이긴다")
	void aLaterRevocationBeatsAnEarlierGrant() throws Exception {
		UUID quitter = UUID.randomUUID();
		createUser(quitter);
		grantPreciseLocation(quitter);
		this.jdbcTemplate.update(
				"UPDATE user_consent SET status = 'REVOKED', decided_at = ? "
						+ "WHERE user_id = ? AND consent_type = 'PRECISE_LOCATION'",
				OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1), quitter);
		UUID placeId = this.placeFixture.insertPlace("철회장소", null, "BEACH", 35.1587, 129.1604);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(quitter))
				.content(requestBody(35.1587, 129.1604, 10)))
				.andExpect(status().isForbidden());

		assertThat(verificationRowCount(placeId, quitter)).isZero();
	}

	/**
	 * 동의 판정은 방침 판을 가리지 않고 가장 최근 결정만 본다. 현재 판으로 찾으면 방침을 올리는
	 * 날 기존 동의자 전원이 조용히 미동의가 된다.
	 */
	@Test
	@DisplayName("🔴 옛 방침 판에 동의했어도 통과한다 — 판을 올리는 날 전원이 막히면 안 된다")
	void aGrantOnAnOlderPolicyVersionStillCounts() throws Exception {
		UUID veteran = UUID.randomUUID();
		createUser(veteran);
		this.jdbcTemplate.update(
				"INSERT INTO user_consent (consent_id, user_id, consent_type, status, policy_version, decided_at) "
						+ "VALUES (?, ?, 'PRECISE_LOCATION', 'GRANTED', '2025-07', ?)",
				UUID.randomUUID(), veteran, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60));
		UUID placeId = this.placeFixture.insertPlace("옛판장소", null, "BEACH", 35.1587, 129.1604);

		this.mockMvc.perform(post("/api/v1/places/{placeId}/visit-verifications", placeId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(as(veteran))
				.content(requestBody(35.1587, 129.1604, 10)))
				.andExpect(status().isOk());
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

		// createUser 는 계정만 만들고 동의는 안 준다. 여기서 재려는 것은 거리 경계이므로 켠다.
		UUID secondUser = UUID.randomUUID();
		createUser(secondUser);
		grantPreciseLocation(secondUser);
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
	 * 칸이 없다는 것과 값이 없다는 것을 따로 확인한다. 하나만 보면 다른 칸에 같은 문자열이 박힌
	 * 경우나 칸은 있는데 이번만 비어 있는 경우를 놓친다.
	 *
	 * {@code table_schema = current_schema()} 는 빼면 안 된다 — 다른 스키마의 동명 칸이 섞여 든다.
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

		// 이 검사가 없으면 행이 하나도 없을 때 아래 훑기가 빈 결과를 보고 그냥 통과한다.
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
