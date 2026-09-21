package com.gabolle.backend.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

import com.gabolle.backend.itinerary.presentation.ItineraryRunController;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 여행 진행을 <b>실제 PostgreSQL 위에서 HTTP 로</b> 본다.
 *
 * <p>이 시험이 생긴 이유가 둘이다.
 *
 * <p>첫째, 진행 엔드포인트에는 <b>실제 DB 를 쓰는 시험이 하나도 없었다.</b> 단위 시험은
 * 저장소를 대역으로 바꾸므로, 저장한 값을 곧바로 다시 읽을 때 무엇이 나오는지를 못 본다.
 *
 * <p>둘째, 그 「곧바로 다시 읽기」가 의심스러웠다. 덮어쓰기가 원시 SQL 이라 같은 트랜잭션의
 * 영속성 컨텍스트에 이미 올라온 엔티티가 <b>안 바뀐다</b>.
 *
 * <p>🔴 <b>재 보니 결함이 맞았다. 다만 짐작한 자리가 아니었다.</b> 「출발」은 멀쩡하다 —
 * 그때는 행이 아직 없어서 읽어 둔 것이 없고, 넣은 뒤에 읽으면 새로 가져온다. 결함은
 * <b>행이 이미 있는 뒤</b>에 나온다(도착·건너뛰기·위치). 그때는 시작에서 읽어 둔 옛 엔티티가
 * 그대로 돌아와, 도착을 찍었는데 응답의 {@code currentStopIndex} 가 안 움직인다.
 * 이 시험이 그것을 잡았고 {@code ItineraryRunService} 가 방금 저장한 값을 싣도록 고쳤다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryRunProgressIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	/** 첫 정차지의 좌표(해운대 근처). 이름은 다른 시험과 안 부딪히게 지어 쓴다. */
	private static final double STOP_LAT = 35.1587;

	private static final double STOP_LNG = 129.1604;

	@Autowired
	private ItineraryRunController runController;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID ownerId;

	private UUID itineraryId;

	private UUID firstStop;

	private UUID secondStop;

	@BeforeEach
	void seedTwoStopItinerary() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.runController).build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.firstStop = UUID.randomUUID();
		this.secondStop = UUID.randomUUID();

		UUID tripId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();

		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				this.ownerId, now, now);
		this.jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-01', '2026-09-02', 1, ?, ?)",
				tripId, this.ownerId, now, now);
		this.jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.ownerId, now);
		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				versionId, this.itineraryId, this.ownerId, now);

		// 좌표가 있는 장소 — 자동 도착 판정은 좌표가 있어야 돈다.
		//
		// 🔴 진짜 장소 이름을 쓰지 않는다. 통합 시험들이 DB 를 함께 쓰기 때문에, 여기서
		// 「감천문화마을」 같은 이름을 넣으면 그 이름으로 검색 결과를 재는 다른 시험이 이
		// 자료를 첫 결과로 집어 빨개진다. 실제로 한 번 그렇게 깨뜨렸다.
		UUID here = UUID.randomUUID();
		UUID faraway = UUID.randomUUID();
		insertPlace(here, "진행시험-첫정차지-" + this.itineraryId, STOP_LAT, STOP_LNG, now);
		insertPlace(faraway, "진행시험-둘째정차지-" + this.itineraryId, 35.0975, 129.0106, now);

		insertItem(versionId, this.firstStop, 1, here, now);
		insertItem(versionId, this.secondStop, 2, faraway, now);
	}

	@Test
	@DisplayName("🔴 출발하면 응답이 곧바로 RUNNING 이다 — 저장 직후 다시 읽어 옛 값을 내보내지 않는가")
	void startRespondsWithTheNewStatus() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/start", this.itineraryId)
						.principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RUNNING"));
	}

	@Test
	@DisplayName("🔴 반경 안의 위치를 올리면 서버가 도착을 찍고 다음 정차지로 넘긴다")
	void locationInsideTheRadiusAdvancesTheStop() throws Exception {
		start();

		// 정차지에서 20m 남짓.
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/location", this.itineraryId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(batch(STOP_LAT + 0.0002, STOP_LNG, "2026-09-01T10:05:00Z"))
						.principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentStopIndex").value(1))
				.andExpect(jsonPath("$.data.stops[0].arrivedHow").value("auto"))
				.andExpect(jsonPath("$.data.stops[0].arrivedAt").value(notNullValue()))
				.andExpect(jsonPath("$.data.stops[1].arrivedAt").value(nullValue()));
	}

	@Test
	@DisplayName("반경 밖이면 아무것도 안 찍는다 — 궤적만 남는다")
	void locationOutsideTheRadiusOnlyLeavesATrail() throws Exception {
		start();

		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/location", this.itineraryId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(batch(35.0, 128.9, "2026-09-01T10:05:00Z"))
						.principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.currentStopIndex").value(0))
				.andExpect(jsonPath("$.data.stops[0].arrivedAt").value(nullValue()));

		assertThat(pingCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 같은 점을 두 번 올려도 궤적에는 한 번만 남는다 — 배치 재시도는 정상 경로다")
	void theSamePointIsStoredOnce() throws Exception {
		start();

		String same = batch(35.0, 128.9, "2026-09-01T10:05:00Z");
		for (int i = 0; i < 2; i++) {
			this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/location", this.itineraryId)
							.contentType(MediaType.APPLICATION_JSON).content(same).principal(as(this.ownerId)))
					.andExpect(status().isOk());
		}

		assertThat(pingCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("재개하면 마지막 위치가 나온다 — 앱을 껐다 켜도 지도를 어디에 놓을지 안다")
	void resumeReturnsTheLastLocation() throws Exception {
		start();
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/location", this.itineraryId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(batch(35.11, 129.02, "2026-09-01T10:05:00Z"))
						.principal(as(this.ownerId)))
				.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/v1/itineraries/{id}/progress", this.itineraryId)
						.principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.lastLocation.lat").value(35.11))
				.andExpect(jsonPath("$.data.lastLocation.lng").value(129.02));
	}

	@Test
	@DisplayName("출발 전에는 위치를 안 받는다 — 409")
	void locationBeforeStartIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/location", this.itineraryId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(batch(35.11, 129.02, "2026-09-01T10:05:00Z"))
						.principal(as(this.ownerId)))
				.andExpect(status().isConflict());

		assertThat(pingCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("점에 값이 빠지면 400 — 조용히 버리면 기기는 보냈다고 믿고 궤적에 구멍이 남는다")
	void incompletePointIsRejected() throws Exception {
		start();

		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/location", this.itineraryId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"points\":[{\"lat\":35.11,\"lng\":129.02}]}")
						.principal(as(this.ownerId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("🔴 완료하면 응답이 곧바로 DONE 이다")
	void completeRespondsWithDone() throws Exception {
		start();

		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/complete", this.itineraryId)
						.principal(as(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DONE"));
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private void start() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/progress/start", this.itineraryId)
						.principal(as(this.ownerId)))
				.andExpect(status().isOk());
	}

	private static String batch(double lat, double lng, String recordedAt) {
		return "{\"points\":[{\"lat\":%s,\"lng\":%s,\"recordedAt\":\"%s\"}]}".formatted(lat, lng, recordedAt);
	}

	private int pingCount() {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_run_ping WHERE itinerary_id = ?", Integer.class, this.itineraryId);
	}

	private void insertPlace(UUID placeId, String name, double lat, double lng, OffsetDateTime now) {
		this.jdbcTemplate.update(
				"INSERT INTO place (place_id, name_ko, lat, lng, created_at) VALUES (?, ?, ?, ?, ?)",
				placeId, name, lat, lng, now);
	}

	private void insertItem(UUID versionId, UUID itemKey, int sequence, UUID placeId, OffsetDateTime now) {
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, start_time, end_time, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, CAST('2026-09-01' AS date), ?, ?, '10:00', '12:00', FALSE, 'VERIFIED', ?)",
				UUID.randomUUID(), versionId, itemKey, sequence, placeId, now);
	}

	private Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}
}
