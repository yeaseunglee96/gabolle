package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryEditController;
import com.gabolle.backend.itinerary.presentation.ItineraryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 판을 옮기는 편집 뒤에도 일정 조회 응답에 길 선과 교통비가 남는다 (S15P21E201-1706) — 실제 PostgreSQL 에서, 편집 창구로
 * 고정하고 되돌린 뒤 조회 창구로 읽는다.
 *
 * <p>일정 조회는 항목마다 「그 항목으로 들어오는 구간」의 선형 · 요금을 싣는다. 전에는 고정 · 되돌리기가 판을 옮기며 두
 * 칸을 흘려서, 같은 구간인데도 편집 한 번에 지도 선이 점선이 되고 교통비 줄이 사라졌다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryLegCopyIntegrationTest {

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
	private JdbcTemplate jdbc;

	private MockMvc mockMvc;

	private UUID userId;

	private UUID itineraryId;

	private UUID firstKey;

	@BeforeEach
	void seed() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.userId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		this.firstKey = UUID.randomUUID();

		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, "
				+ "updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", this.userId, now, now);
		this.jdbc.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
				+ "VALUES (?, ?, '2026-09-10', '2026-09-10', 1, ?, ?)", tripId, this.userId, now, now);
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.userId, now);
		this.jdbc.update("INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);
		this.jdbc.update("INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
				+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				versionId, this.itineraryId, this.userId, now);

		UUID first = insertItem(versionId, this.firstKey, 1);
		UUID second = insertItem(versionId, UUID.randomUUID(), 2);
		// 첫 곳 → 둘째 곳, 자가용. 길찾기로 잰 선형과 요금이 있다.
		this.jdbc.update("INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, from_place_id, "
				+ "to_place_id, travel_mode, distance_m, duration_min, data_status, fare_krw, path, created_at) "
				+ "VALUES (?, ?, 0, 2, ?, ?, 'PRIVATE_CAR', 4200, 12, 'VERIFIED', 4800, "
				+ "'[[129.16,35.15],[129.14,35.15],[129.12,35.16]]'::jsonb, ?)", UUID.randomUUID(), versionId, first, second, now);
	}

	@Test
	@DisplayName("🔴 고정한 뒤에도, 그 뒤 되돌린 뒤에도 같은 구간의 길 선 · 교통비가 조회 응답에 남는다")
	void lockThenRevertKeepThePathAndFare() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.firstKey)
				.contentType(MediaType.APPLICATION_JSON).principal(asOwner()).content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().is2xxSuccessful());
		expectPathAndFareOnSecondStop(2);

		this.mockMvc.perform(post("/api/v1/itineraries/{id}/revert", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON).principal(asOwner()).content("{\"baseVersion\":2,\"toVersion\":1}"))
				.andExpect(status().is2xxSuccessful());
		expectPathAndFareOnSecondStop(3);
	}

	private void expectPathAndFareOnSecondStop(int version) throws Exception {
		this.mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(version))
				.andExpect(jsonPath("$.data.days[0].items[1].travelFareKrw").value(4800))
				.andExpect(jsonPath("$.data.days[0].items[1].travelPath.length()").value(3))
				.andExpect(jsonPath("$.data.days[0].items[1].travelPath[2][0]").value(129.12));
	}

	private UUID insertItem(UUID versionId, UUID itemKey, int sequence) {
		UUID placeId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '곳', ?)", placeId, now);
		this.jdbc.update("INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
				+ "sequence, place_id, locked, data_status, created_at) VALUES (?, ?, ?, 0, '2026-09-10', ?, ?, FALSE, 'UNKNOWN', ?)",
				UUID.randomUUID(), versionId, itemKey, sequence, placeId, now);
		return placeId;
	}

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.userId.toString(), null, List.of());
	}
}
