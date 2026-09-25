package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.gabolle.backend.itinerary.presentation.NotificationSummaryController;
import com.gabolle.backend.itinerary.presentation.NotificationSummaryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.TripActivityController;
import com.gabolle.backend.itinerary.presentation.TripActivityExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.CollaborationSliceApplication;

import tools.jackson.databind.json.JsonMapper;

/**
 * 종 점 (S15P21E201-1699) — 내 모든 여행의 활동에서 가장 최근 것을 한 번에 본다. 실제 PostgreSQL 에서.
 *
 * <p>나는 여행 A 의 주인이고 여행 B 에는 열람자로 들어가 있다. 가장 최근 활동은 B 의 것이다. 그보다 나중인 활동이 둘
 * 더 있지만 안 세야 한다 — 내가 지운 여행 C, 내가 없는 남의 여행 D.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class NotificationSummaryIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private NotificationSummaryController summaryController;

	@Autowired
	private NotificationSummaryExceptionHandler summaryHandler;

	@Autowired
	private TripActivityController activityController;

	@Autowired
	private TripActivityExceptionHandler activityHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID me;

	private UUID tripB;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.summaryController, this.activityController)
				.setControllerAdvice(this.summaryHandler, this.activityHandler)
				.build();
		this.me = insertUser("나");
		UUID friend = insertUser("친구");
		UUID stranger = insertUser("남");

		UUID tripA = insertTrip(this.me, false);
		member(tripA, this.me, "OWNER");
		versions(tripA, this.me, 0, 1);

		this.tripB = insertTrip(friend, false);
		member(this.tripB, friend, "OWNER");
		member(this.tripB, this.me, "VIEWER");
		versions(this.tripB, friend, 5);

		UUID deleted = insertTrip(this.me, true);
		member(deleted, this.me, "OWNER");
		versions(deleted, this.me, 10);

		UUID others = insertTrip(stranger, false);
		member(others, stranger, "OWNER");
		versions(others, stranger, 20);
	}

	@Test
	@DisplayName("🔴 열람자로 들어간 여행까지 보고, 지운 여행과 남의 여행은 안 본다 — 가장 최근 시각은 여행 활동의 at 과 같은 값")
	void latestAcrossMyTripsMatchesTheActivityFeed() throws Exception {
		String activityAt = json(this.mockMvc.perform(get("/api/v1/trips/{tripId}/activity", this.tripB).principal(as(this.me)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
				.at("/data/entries/0/at").asString();

		this.mockMvc.perform(get("/api/v1/me/notification-summary").principal(as(this.me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.latestAt").value(activityAt))
				.andExpect(jsonPath("$.data.hasUnseen").value(true));
	}

	@Test
	@DisplayName("🔴 본 시각보다 나중 활동이 있으면 점, 없으면 점 없음 — 같은 시각이면 본 것이다")
	void sinceDecidesTheDot() throws Exception {
		Instant latest = Instant.parse(summary(null).at("/data/latestAt").asString());

		assertThat(summary(latest.minusMillis(1)).at("/data/hasUnseen").asBoolean()).isTrue();
		assertThat(summary(latest).at("/data/hasUnseen").asBoolean()).isFalse();
		assertThat(summary(latest.plusSeconds(60)).at("/data/hasUnseen").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("여행도 활동도 없으면 점 없음 · 시각 없음")
	void nobodyHasNothing() throws Exception {
		this.mockMvc.perform(get("/api/v1/me/notification-summary").principal(as(insertUser("새 사람"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasUnseen").value(false))
				.andExpect(jsonPath("$.data.latestAt").doesNotExist());
	}

	@Test
	@DisplayName("since 가 시각이 아니면 우리 봉투의 400")
	void badSinceIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/me/notification-summary").param("since", "어제").principal(as(this.me)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("NOTIFICATION_SUMMARY_INVALID_REQUEST"));
	}

	// ---- 도우미 ----

	private tools.jackson.databind.JsonNode summary(Instant since) throws Exception {
		var request = get("/api/v1/me/notification-summary").principal(as(this.me));
		if (since != null) {
			request = request.param("since", since.toString());
		}
		return json(this.mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString());
	}

	private static tools.jackson.databind.JsonNode json(String body) {
		return JsonMapper.builder().build().readTree(body);
	}

	private UUID insertUser(String displayName) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, "
				+ "updated_at) VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", id, displayName, now, now);
		return id;
	}

	private UUID insertTrip(UUID owner, boolean deleted) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at, "
				+ "deleted_at) VALUES (?, ?, '2026-10-10', '2026-10-11', 2, ?, ?, ?)", id, owner, now, now,
				deleted ? now : null);
		return id;
	}

	private void member(UUID tripId, UUID userId, String role) {
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, now())",
				UUID.randomUUID(), tripId, userId, role);
	}

	/** 일정 하나에 판을 차례로 — 판마다 지금부터 {@code secondsLater} 초 뒤에 만든 것으로. */
	private void versions(UUID tripId, UUID createdBy, int... secondsLater) {
		UUID itineraryId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, ?, now())",
				itineraryId, tripId, secondsLater.length);
		for (int i = 0; i < secondsLater.length; i++) {
			this.jdbc.update("INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
					+ "operation, created_by, request_id, created_at) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 second'))",
					UUID.randomUUID(), itineraryId, i + 1, i == 0 ? null : i, i == 0 ? "CREATE" : "REORDER", createdBy,
					"req_seed_" + UUID.randomUUID(), secondsLater[i]);
		}
	}

	private static Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
