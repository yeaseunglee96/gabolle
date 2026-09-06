package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * 고정·해제가 실제 표에 무엇을 남기는가 — S15P21E201-662.
 *
 * <h2>🔴 이 테스트가 대체한 것</h2>
 * 예전 {@code ItineraryEditControllerTest} 는 Spring 컨텍스트 없이 인메모리 저장소로 돌았다.
 * 그 구성으로는 <b>이번 결함을 구조적으로 못 잡는다</b> — 인메모리 저장소에 애초에 항목이
 * 없었으므로 "판을 복사했는가" 를 물을 대상 자체가 없었다. 그래서 진짜 PostgreSQL 위로 옮겼다.
 *
 * <h2>🔴 응답값이 아니라 표를 읽어 단정하는 자리가 있다</h2>
 * {@code locked} 는 {@link JdbcTemplate} 로 {@code itinerary_item} 을 직접 읽어 확인한다.
 * 응답만 보면 서버가 저장하지 않은 값을 그대로 되돌려줘도 통과한다 — 실제로 고치기 전
 * 코드가 URL 로 받은 {@code itemId} 를 응답에 그대로 실어 "고정됐다" 처럼 보이게 하고
 * 있었다.
 *
 * <p>MockMvc 는 {@code standaloneSetup} 으로 컨트롤러 빈만 올린다({@code
 * RecommendationResultAuthorizationTest} 와 같은 방식). 서비스·저장소는 컨텍스트에서
 * 주입받은 <b>진짜</b> 구현이고, 인증 필터 체인만 안 태운다 — 그 설정은 {@code auth}
 * 패키지 소유라 이 슬라이스가 스캔하지 않는다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryLockPersistenceIntegrationTest {

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
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID userId;
	private UUID itineraryId;
	private UUID versionId;
	private UUID itemKey;
	private UUID otherItemKey;

	@BeforeEach
	void seedItineraryWithTwoItems() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.userId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.versionId = UUID.randomUUID();
		this.itemKey = UUID.randomUUID();
		this.otherItemKey = UUID.randomUUID();

		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'PERSONALIZED', 'ACTIVE', ?, ?)",
				this.userId, now, now);
		jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				tripId, this.userId, now, now);
		// 🔴 조회 권한 판정이 이 표를 본다(TripQueryService). 없으면 자기 일정도 못 본다.
		jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.userId, now);
		jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);
		jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				this.versionId, this.itineraryId, this.userId, now);

		insertItem(this.versionId, this.itemKey, 1, "해운대해수욕장");
		insertItem(this.versionId, this.otherItemKey, 2, "광안리해변");
	}

	private void insertItem(UUID versionId, UUID itemKey, int sequence, String placeName) {
		UUID placeId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, ?, ?)",
				placeId, placeName, now);
		jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, '2026-09-10', ?, ?, FALSE, 'UNKNOWN', ?)",
				UUID.randomUUID(), versionId, itemKey, sequence, placeId, now);
	}

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.userId.toString(), null, List.of());
	}

	private Boolean lockedInDatabase(int version, UUID key) {
		return jdbcTemplate.queryForObject(
				"SELECT i.locked FROM itinerary_item i JOIN itinerary_versions v "
						+ "ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? AND i.item_key = ?",
				Boolean.class, this.itineraryId, version, key);
	}

	private int itemCount(int version) {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_item i JOIN itinerary_versions v "
						+ "ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ?",
				Integer.class, this.itineraryId, version);
	}

	/**
	 * 🔴 이 테스트가 이 파일의 존재 이유다 — 고정 한 번에 일정이 사라지던 결함.
	 *
	 * <p>{@code ItineraryRevision.copyOf} 의 항목 복사를 빼면 새 판의 항목 수가 0이 되어
	 * 여기서 빨개진다.
	 */
	@Test
	@DisplayName("🔴 고정하면 새 판이 생기고 그 판에 항목이 그대로 남는다")
	void lockCopiesContentIntoNewVersion() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.baseVersion").value(1))
				.andExpect(jsonPath("$.data.operation").value("LOCK_ITEM"))
				// 🔴 앱이 이 응답을 일정 전체로 받아 화면 상태에 그대로 넣는다.
				.andExpect(jsonPath("$.data.days").isArray())
				.andExpect(jsonPath("$.data.days[0].items.length()").value(2))
				.andExpect(jsonPath("$.meta.requestId").exists())
				.andExpect(jsonPath("$.error").doesNotExist());

		assertThat(itemCount(2)).isEqualTo(2);
		assertThat(lockedInDatabase(2, this.itemKey)).isTrue();
		// 나머지 항목은 그대로여야 한다.
		assertThat(lockedInDatabase(2, this.otherItemKey)).isFalse();

		// 조회도 같은 것을 본다 — 최신 판이 빈 판이 아니다.
		mockMvc.perform(get("/api/v1/itineraries/{id}", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(2))
				.andExpect(jsonPath("$.data.days[0].items.length()").value(2));
	}

	/**
	 * 🔴 {@code itemKey} 를 복사할 때 새로 만들면 두 번째 요청이 404 가 되어 빨개진다.
	 * DB 의 {@code uq_itinerary_item_key} 는 같은 판 안의 중복만 막으므로 이 실수를 못 잡는다.
	 */
	@Test
	@DisplayName("🔴 같은 itemKey 로 두 번 연속 편집할 수 있다")
	void itemKeySurvivesAcrossVersions() throws Exception {
		lockVia(1, true);
		lockVia(2, false);

		assertThat(lockedInDatabase(3, this.itemKey)).isFalse();
		assertThat(itemCount(3)).isEqualTo(2);
	}

	@Test
	@DisplayName("본문 locked:false 로 해제된다 — 앱이 이 경로를 토글로 쓴다")
	void unlockViaBodyFlag() throws Exception {
		lockVia(1, true);
		assertThat(lockedInDatabase(2, this.itemKey)).isTrue();

		lockVia(2, false);
		assertThat(lockedInDatabase(3, this.itemKey)).isFalse();
	}

	@Test
	@DisplayName("ITN-04 — DELETE + If-Match 로도 해제된다")
	void unlockViaDeleteWithIfMatch() throws Exception {
		lockVia(1, true);

		mockMvc.perform(delete("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.header("If-Match", "\"2\"")
						.principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.version").value(3))
				.andExpect(jsonPath("$.data.days[0].items.length()").value(2));

		assertThat(lockedInDatabase(3, this.itemKey)).isFalse();
	}

	@Test
	@DisplayName("ITN-04 — 바탕 판을 안 보내면 400")
	void deleteWithoutBaseVersionIsRejected() throws Exception {
		mockMvc.perform(delete("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.principal(asOwner()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_BASE_VERSION_REQUIRED"));
	}

	/**
	 * 🔴 앱이 읽는 자리 그대로 단정한다.
	 *
	 * <p>{@code frontend/src/plan/itinerary.ts} 가 {@code error.fields} 를
	 * {@code /^latestVersion=/} 로 훑어 최신 판 번호를 뽑는다. 코드와 상태만 맞고 이
	 * 문자열이 없으면 충돌 배너에 최신 번호가 안 뜬다 — 고치기 전이 그 상태였다.
	 */
	@Test
	@DisplayName("🔴 낡은 baseVersion 은 409 이고 error.fields 에 latestVersion=<n> 이 있다")
	void staleBaseVersionReturnsConflictInWireFormatTheAppReads() throws Exception {
		lockVia(1, true);

		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.otherItemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_VERSION_CONFLICT"))
				.andExpect(jsonPath("$.error.message").isString())
				.andExpect(jsonPath("$.error.fields").value(org.hamcrest.Matchers.hasItem("latestVersion=2")))
				.andExpect(jsonPath("$.error.fields").value(org.hamcrest.Matchers.hasItem("attemptedBaseVersion=1")))
				.andExpect(jsonPath("$.data").doesNotExist());

		// 진 요청이 판을 남기지 않았다.
		assertThat(itemCount(3)).isZero();
	}

	@Test
	@DisplayName("바탕 판에 없는 항목을 고정하면 404")
	void unknownItemKeyReturnsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"locked\":true,\"baseVersion\":1}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_ITEM_NOT_FOUND"));
	}

	@Test
	@DisplayName("명세대로 locked 없이 baseVersion 만 보내면 고정으로 본다")
	void bodyWithoutLockedFlagLocks() throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"baseVersion\":1}"))
				.andExpect(status().isCreated());

		assertThat(lockedInDatabase(2, this.itemKey)).isTrue();
	}

	private void lockVia(int baseVersion, boolean locked) throws Exception {
		mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"locked\":" + locked + ",\"baseVersion\":" + baseVersion + "}"))
				.andExpect(status().isCreated());
	}
}
