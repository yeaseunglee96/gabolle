package com.gabolle.backend.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabolle.backend.common.security.OpaqueTokens;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.share.presentation.ShareExceptionHandler;
import com.gabolle.backend.share.presentation.ShareLinkController;
import com.gabolle.backend.share.presentation.SharedItineraryController;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.testslice.CollaborationSliceApplication;

/**
 * 읽기 전용 공유 주소 발급과 비로그인 조회가 실제 PostgreSQL 에서 동작하는지 본다.
 *
 * <p>발급(POST)은 소유자만 되고 편집자·외부인은 막혀야 한다. 조회(GET)는 인증 없이 열리고
 * 만료·삭제된 원본·없는 표를 구분해 답해야 한다. 응답에 출발지·예산·인원·연락처가 새지
 * 않는지를 문자열 대조로 직접 본다.
 */
@SpringBootTest(classes = CollaborationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ShareLinkIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ShareLinkController shareLinkController;

	@Autowired
	private SharedItineraryController sharedItineraryController;

	@Autowired
	private ShareExceptionHandler shareExceptionHandler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID ownerId;
	private UUID editorId;
	private UUID outsiderId;
	private UUID tripId;
	private UUID placeA;
	private UUID placeB;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.shareLinkController, this.sharedItineraryController)
				.setControllerAdvice(this.shareExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = insertUser("owner");
		this.editorId = insertUser("editor");
		this.outsiderId = insertUser("outsider");

		// 3일짜리 여행(2026-09-10 ~ 2026-09-12) — 소유자·편집자가 회원이고 외부인은 아니다.
		this.tripId = insertTrip(this.ownerId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), now);
		insertMember(this.tripId, this.ownerId, "OWNER", now);
		insertMember(this.tripId, this.editorId, "EDITOR", now);

		this.placeA = insertPlace("A 해운대해수욕장", "BEACH");
		this.placeB = insertPlace("B 광안리해변", "BEACH");
	}

	// ---- 완료 기준 1 — 소유자 발급 ----

	@Test
	@DisplayName("소유자가 발급하면 201, token 43글자, expiresAt 이 지금+30일(±1분), 행 1개·view_count=0")
	void ownerCanIssueShareLink() throws Exception {
		String body = this.mockMvc.perform(post("/api/v1/trips/{tripId}/share-links", this.tripId)
						.principal(StoryFixture.as(this.ownerId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.tripId").value(this.tripId.toString()))
				.andExpect(jsonPath("$.data.token", hasLength(43)))
				.andExpect(jsonPath("$.data.path").value(Matchers.startsWith("/api/v1/shares/")))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		JsonNode data = this.objectMapper.readTree(body).path("data");
		Instant expiresAt = Instant.parse(data.path("expiresAt").asText());
		Instant expected = Instant.now().plus(30, ChronoUnit.DAYS);
		assertThat(Math.abs(ChronoUnit.SECONDS.between(expected, expiresAt))).isLessThan(60);

		String token = data.path("token").asText();
		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"SELECT view_count, token FROM trip_share_link WHERE trip_id = ?", this.tripId);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get("token")).isEqualTo(token);
		assertThat(((Number) rows.get(0).get("view_count")).intValue()).isZero();
	}

	// ---- 완료 기준 2 — 편집자 403, 외부인 404 ----

	@Test
	@DisplayName("편집자가 발급하면 403 TRIP_FORBIDDEN, 외부인은 404 TRIP_NOT_FOUND, 행이 생기지 않는다")
	void onlyOwnerCanIssue() throws Exception {
		this.mockMvc.perform(post("/api/v1/trips/{tripId}/share-links", this.tripId)
						.principal(StoryFixture.as(this.editorId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("TRIP_FORBIDDEN"));

		this.mockMvc.perform(post("/api/v1/trips/{tripId}/share-links", this.tripId)
						.principal(StoryFixture.as(this.outsiderId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));

		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM trip_share_link WHERE trip_id = ?", Integer.class, this.tripId);
		assertThat(count).isZero();
	}

	// ---- 완료 기준 3·4 — 비로그인 조회, 계약 밖 항목이 새지 않는다 ----

	@Test
	@DisplayName("발급된 표로 principal 없이 GET 하면 200, 날짜·항목이 여행 그대로이고 원본 전용 항목이 새지 않는다")
	void anyoneCanOpenSharedItinerary() throws Exception {
		UUID itineraryId = insertItinerary(this.tripId);
		UUID versionId = insertVersion(itineraryId, 1);
		insertItem(versionId, 1, this.placeA, LocalDate.of(2026, 9, 10), "09:00", "12:00");
		insertItem(versionId, 2, this.placeB, LocalDate.of(2026, 9, 10), "12:00", "18:00");

		String token = issueToken(now().plus(30, ChronoUnit.DAYS));

		ResultActions result = this.mockMvc.perform(get("/api/v1/shares/{token}", token));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(1))
				.andExpect(jsonPath("$.data.days.length()").value(3))
				.andExpect(jsonPath("$.data.days[0].items.length()").value(2))
				.andExpect(jsonPath("$.data.days[0].items[0].placeName").value("A 해운대해수욕장"))
				.andExpect(jsonPath("$.data.days[0].items[1].placeName").value("B 광안리해변"))
				.andExpect(jsonPath("$.data.notShared.length()").value(4))
				.andExpect(jsonPath("$.error").doesNotExist());

		// 완료 기준 — 값이 null 인지가 아니라 그 키 자체가 응답에 없는지를 본다.
		// 콜론까지 넣어 "JSON 키로 나타나는가" 를 본다 — notShared 값 자체가 "partySize" 라는
		// 글자를 담고 있어(SharedItineraryResponse.NOT_SHARED), 콜론 없이 부분 문자열만 보면
		// 그 고지문과 충돌해 오탐이 난다.
		result.andExpect(content().string(not(Matchers.containsString("\"originLat\":"))))
				.andExpect(content().string(not(Matchers.containsString("\"originLng\":"))))
				.andExpect(content().string(not(Matchers.containsString("\"budgetKrw\":"))))
				.andExpect(content().string(not(Matchers.containsString("\"partySize\":"))))
				.andExpect(content().string(not(Matchers.containsString("\"ownerUserId\":"))))
				.andExpect(content().string(not(Matchers.containsString("\"userId\":"))));
	}

	// ---- 완료 기준 5 — 열람 수 ----

	@Test
	@DisplayName("두 번 GET 하면 view_count=2 이고 last_viewed_at 이 채워진다")
	void viewCountIncrementsOnEachOpen() throws Exception {
		// 일정 유무는 이 시나리오의 관심사가 아니다 — 일정 없는 여행으로 최소로 둔다.
		String token = issueToken(now().plus(30, ChronoUnit.DAYS));

		this.mockMvc.perform(get("/api/v1/shares/{token}", token)).andExpect(status().isOk());
		this.mockMvc.perform(get("/api/v1/shares/{token}", token)).andExpect(status().isOk());

		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT view_count, last_viewed_at FROM trip_share_link WHERE token = ?", token);
		assertThat(((Number) row.get("view_count")).intValue()).isEqualTo(2);
		assertThat(row.get("last_viewed_at")).isNotNull();
	}

	// ---- 완료 기준 6 — 만료 ----

	@Test
	@DisplayName("만료된 표는 410 SHARE_LINK_EXPIRED 이고 view_count 가 오르지 않는다")
	void expiredLinkReturnsGone() throws Exception {
		// ck_trip_share_link_expiry 가 "expires_at > created_at" 을 항상 요구한다 — 지금
		// 시각보다 만료가 앞서야 하는 이 시나리오도 그 제약을 지키며 만들어야 한다. 만든
		// 시각도 만료 시각도 둘 다 지금보다 과거로 두면 "이미 만들어졌고 이미 끝난 표" 가 된다.
		String token = issueToken(OffsetDateTime.now(ZoneOffset.UTC).minusDays(40), now().minus(10, ChronoUnit.DAYS));

		this.mockMvc.perform(get("/api/v1/shares/{token}", token))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.error.code").value("SHARE_LINK_EXPIRED"));

		Integer viewCount = this.jdbc.queryForObject(
				"SELECT view_count FROM trip_share_link WHERE token = ?", Integer.class, token);
		assertThat(viewCount).isZero();
	}

	// ---- 완료 기준 7 — 원본 삭제 ----

	@Test
	@DisplayName("원본 여행의 deleted_at 을 채운 뒤 GET 하면 404 SHARED_TRIP_NOT_FOUND")
	void softDeletedTripReturnsSharedTripNotFound() throws Exception {
		String token = issueToken(now().plus(30, ChronoUnit.DAYS));

		this.jdbc.update("UPDATE trip SET deleted_at = ? WHERE trip_id = ?", OffsetDateTime.now(ZoneOffset.UTC),
				this.tripId);

		this.mockMvc.perform(get("/api/v1/shares/{token}", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("SHARED_TRIP_NOT_FOUND"));
	}

	// ---- 완료 기준 8 — 없는 표 ----

	@Test
	@DisplayName("없는 표는 404 SHARE_LINK_NOT_FOUND")
	void unknownTokenReturnsNotFound() throws Exception {
		this.mockMvc.perform(get("/api/v1/shares/{token}", OpaqueTokens.generate()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("SHARE_LINK_NOT_FOUND"));
	}

	// ---- 완료 기준 9 — 일정이 아직 없는 여행 ----

	@Test
	@DisplayName("일정이 아직 없는 여행의 공유 주소를 열면 200, version=null, 날짜마다 빈 items")
	void tripWithoutItineraryReturnsEmptyDays() throws Exception {
		String token = issueToken(now().plus(30, ChronoUnit.DAYS));

		this.mockMvc.perform(get("/api/v1/shares/{token}", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").doesNotExist())
				.andExpect(jsonPath("$.data.days.length()").value(3))
				.andExpect(jsonPath("$.data.days[0].items.length()").value(0))
				.andExpect(jsonPath("$.data.days[1].items.length()").value(0))
				.andExpect(jsonPath("$.data.days[2].items.length()").value(0));
	}

	// ---- 시드 도우미 ----

	private Instant now() {
		return Instant.now();
	}

	private UUID insertUser(String tag) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				id, tag + "-" + id, now, now);
		return id;
	}

	private UUID insertTrip(UUID ownerUserId, LocalDate start, LocalDate end, OffsetDateTime now) {
		UUID id = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, owner_type, start_date, end_date, created_at, updated_at) "
						+ "VALUES (?, ?, 'USER', ?, ?, ?, ?)",
				id, ownerUserId, start, end, now, now);
		return id;
	}

	private void insertMember(UUID tripIdParam, UUID userId, String role, OffsetDateTime at) {
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripIdParam, userId, role, at);
	}

	private UUID insertPlace(String nameKo, String category) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, category, created_at) VALUES (?, ?, ?, now())",
				id, nameKo, category);
		return id;
	}

	private UUID insertItinerary(UUID tripIdParam) {
		UUID itineraryId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, tripIdParam);
		return itineraryId;
	}

	private UUID insertVersion(UUID itineraryId, int version) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, NULL, 'CREATE', ?, ?, now())",
				versionId, itineraryId, version, this.ownerId, "req_seed_" + version);
		return versionId;
	}

	private void insertItem(UUID versionId, int sequence, UUID placeId, LocalDate visitDate, String start,
			String end) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, start_time, end_time, stay_minutes, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, ?, ?, ?, ?::time, ?::time, 120, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, UUID.randomUUID(), visitDate, sequence, placeId, start, end);
	}

	/** 발급 서비스를 거치지 않고 표를 직접 심는다 — 이 표들이 보는 것은 발급이 아니라 조회다. */
	private String issueToken(Instant expiresAt) {
		return issueToken(OffsetDateTime.now(ZoneOffset.UTC), expiresAt);
	}

	/**
	 * {@code ck_trip_share_link_expiry} 가 {@code expires_at > created_at} 을 항상 요구한다.
	 * 만료 시나리오를 심을 때도 이 제약을 지켜야 하므로 만든 시각을 따로 받는다.
	 */
	private String issueToken(OffsetDateTime createdAt, Instant expiresAt) {
		String token = OpaqueTokens.generate();
		this.jdbc.update(
				"INSERT INTO trip_share_link (trip_share_link_id, trip_id, token, created_by, created_at, expires_at, view_count) "
						+ "VALUES (?, ?, ?, ?, ?, ?, 0)",
				UUID.randomUUID(), this.tripId, token, this.ownerId,
				createdAt, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
		return token;
	}
}
