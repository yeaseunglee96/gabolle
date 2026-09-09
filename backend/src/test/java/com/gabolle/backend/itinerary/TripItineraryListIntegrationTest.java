package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.TripActivityExceptionHandler;
import com.gabolle.backend.itinerary.presentation.TripItineraryController;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.CollaborationSliceApplication;

/**
 * 여행 하나를 눌렀을 때 무엇을 열지 — {@code GET /api/v1/trips/{tripId}/itineraries} · S15P21E201-738.
 *
 * <p>내 여행 목록({@code GET /api/v1/trips})은 일정 식별자를 담지 않는다. 담으면 여행 모듈이
 * 일정 표를 알게 되기 때문이다. 그래서 이 자리가 그 답을 준다.
 *
 * <p>재는 것은 셋이다 — 회원이면 열람자라도 볼 수 있는가, 비회원에게 <b>404 로 존재를 감추는가</b>,
 * 일정이 아직 없는 여행이 오류가 아니라 빈 목록인가. 마지막 것이 중요하다. 여행을 만들고
 * 계산이 끝나기 전에 목록을 열면 그 상태가 정상인데, 여기서 404 를 내면 사용자에게는
 * "방금 만든 여행이 없어졌다" 로 보인다.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripItineraryListIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripItineraryController controller;

	@Autowired
	private TripActivityExceptionHandler exceptionHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID owner;
	private UUID viewer;
	private UUID outsider;
	private UUID tripId;
	private UUID emptyTripId;
	private UUID itineraryId;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
				.setControllerAdvice(this.exceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.owner = insertUser("소유자");
		this.viewer = insertUser("열람자");
		this.outsider = insertUser("외부인");
		this.tripId = insertTrip(now);
		this.emptyTripId = insertTrip(now);

		insertMember(this.tripId, this.owner, "OWNER");
		insertMember(this.tripId, this.viewer, "VIEWER");
		insertMember(this.emptyTripId, this.owner, "OWNER");

		this.itineraryId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 3, ?)",
				this.itineraryId, this.tripId, now);
	}

	@Test
	@DisplayName("회원이면 열람자라도 그 여행의 일정과 최신 판 번호를 받는다")
	void memberGetsTheItineraryOfTheTrip() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/itineraries", this.tripId).principal(as(this.viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.tripId").value(this.tripId.toString()))
				.andExpect(jsonPath("$.data.role").value("VIEWER"))
				.andExpect(jsonPath("$.data.itineraries.length()").value(1))
				.andExpect(jsonPath("$.data.itineraries[0].itineraryId").value(this.itineraryId.toString()))
				.andExpect(jsonPath("$.data.itineraries[0].latestVersion").value(3));
	}

	@Test
	@DisplayName("🔴 비회원에게는 404 다 — 403 으로 답하면 그 응답 코드가 남의 여행이 있다는 신호가 된다")
	void outsiderCannotTellWhetherTheTripExists() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/itineraries", this.tripId).principal(as(this.outsider)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));

		// 진짜로 없는 여행도 같은 답이어야 한다. 달라지면 그 차이가 곧 존재 여부다.
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/itineraries", UUID.randomUUID()).principal(as(this.outsider)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("아직 계산이 안 끝난 여행은 오류가 아니라 빈 목록이다")
	void aTripWithoutAnItineraryIsAnEmptyListNotAnError() throws Exception {
		this.mockMvc.perform(get("/api/v1/trips/{tripId}/itineraries", this.emptyTripId).principal(as(this.owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("OWNER"))
				.andExpect(jsonPath("$.data.itineraries.length()").value(0));
	}

	// ---- 시드 도우미 ----

	private UUID insertUser(String displayName) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, displayName, now, now);
		return id;
	}

	private UUID insertTrip(OffsetDateTime now) {
		UUID id = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-11', 2, ?, ?)",
				id, this.owner, now, now);
		return id;
	}

	private void insertMember(UUID trip, UUID userId, String role) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, now())",
				UUID.randomUUID(), trip, userId, role);
	}

	private static Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
