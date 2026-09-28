package com.gabolle.backend.itinerary;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 일정 응답의 추천 이유 칸(S15P21E201-1643) — 실제 PostgreSQL 위에서 HTTP 로 본다.
 *
 * <p>화면은 칸이 없으면 줄을 안 그리게 짜여 있어, 칸이 빠져도 아무 오류가 안 난다. 그래서 「키가 있고 빈 배열이다」를
 * {@code hasSize(0)} 로 단정한다 — {@code doesNotExist()} 로는 칸이 아예 없는 것과 구분되지 않는다.
 * {@link ItineraryActualTimeIntegrationTest} 와 같은 방식(standalone MockMvc + 진짜 서비스·저장소)이다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryReasonCodesIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryQueryController queryController;

	@Autowired
	private ItineraryQueryExceptionHandler queryExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID itineraryId;

	@BeforeEach
	void seedOneRecommendedAndOneBareItem() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.queryController)
				.setControllerAdvice(this.queryExceptionHandler)
				.build();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();

		jdbcTemplate.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
				+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", this.ownerId, now, now);
		jdbcTemplate.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, "
				+ "updated_at) VALUES (?, ?, '2026-10-01', '2026-10-01', 1, ?, ?)", tripId, this.ownerId, now, now);
		jdbcTemplate.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) "
				+ "VALUES (?, ?, ?, 'OWNER', ?)", UUID.randomUUID(), tripId, this.ownerId, now);
		jdbcTemplate.update("INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) "
				+ "VALUES (?, ?, 1, ?)", this.itineraryId, tripId, now);
		jdbcTemplate.update("INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
				+ "operation, created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				versionId, this.itineraryId, this.ownerId, now);
		UUID placeId = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, ?, ?)", placeId, "광안리해수욕장",
				now);

		// 첫째는 추천이 넣은 곳(이유 둘), 둘째는 이유가 비어 있는 곳이다.
		insertItem(versionId, 1, "10:00", placeId, "{NEAR_ORIGIN,TOP_CONTRIBUTOR_interest}", now);
		insertItem(versionId, 2, "13:00", placeId, "{}", now);
	}

	@Test
	@DisplayName("🔴 이유가 있는 곳은 저장된 코드가 차례 그대로, 없는 곳은 키가 있고 빈 배열이다")
	void reasonCodesAreAlwaysPresent() throws Exception {
		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId)
						.principal(new UsernamePasswordAuthenticationToken(this.ownerId.toString(), null, List.of())))
				.andExpect(status().isOk())
				// 먼저 두 곳이 다 나왔는지 본다 — 빈 목록에 대고 재면 아무것도 안 본 것이 초록이 된다.
				.andExpect(jsonPath("$.data.days[0].items", hasSize(2)))
				.andExpect(jsonPath("$.data.days[0].items[0].reasonCodes", contains("NEAR_ORIGIN", "TOP_CONTRIBUTOR_interest")))
				.andExpect(jsonPath("$.data.days[0].items[1].reasonCodes", hasSize(0)))
				// 더하기만 했다 — 기존 칸은 그대로 있다.
				.andExpect(jsonPath("$.data.days[0].items[1].warningCodes", hasSize(0)))
				.andExpect(jsonPath("$.data.days[0].items[0].title").value("광안리해수욕장"));
	}

	private void insertItem(UUID versionId, int sequence, String start, UUID placeId, String reasonCodes,
			OffsetDateTime now) {
		jdbcTemplate.update("INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
				+ "visit_date, sequence, place_id, start_time, end_time, locked, data_status, reason_codes, created_at) "
				+ "VALUES (?, ?, ?, 0, '2026-10-01', ?, ?, CAST(? AS time), CAST(? AS time) + interval '1 hour', FALSE, 'VERIFIED', CAST(? AS text[]), ?)",
				UUID.randomUUID(), versionId, UUID.randomUUID(), sequence, placeId, start, start, reasonCodes, now);
	}
}
