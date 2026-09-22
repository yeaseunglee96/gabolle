package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.presentation.ItineraryEditController;
import com.gabolle.backend.itinerary.presentation.ItineraryExceptionHandler;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 되돌리기가 실제 PostgreSQL 에서 판 체인으로 동작하는지 본다.
 *
 * <p>별도 스냅샷 표가 없다. 편집 직전 상태는 바탕 판에 그대로 남아 있고 되돌리기는 그 판을
 * 새 판으로 복사한다. 그래서 보는 것은 셋이다 — 돌아온 내용이 그 판과 항목별로 같은가
 * ({@code item_key} 까지), 중간 판이 그대로 남아 있는가, 되돌린 뒤에도 편집·되돌리기가
 * 이어지는가.
 *
 * <p>제외·순서변경 판은 SQL 로 직접 심는다. 여기서 재는 것은 그 편집들이 아니라 되돌리기다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryRevertIntegrationTest {

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
	private UUID placeA;
	private UUID placeB;
	private UUID keyA;
	private UUID keyB;

	/** 1판: A(1, 09:00-12:00) B(2, 12:00-18:00). */
	@BeforeEach
	void seedVersionOne() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.editController, this.queryController)
				.setControllerAdvice(this.editExceptionHandler, this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.userId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.placeA = UUID.randomUUID();
		this.placeB = UUID.randomUUID();
		this.keyA = UUID.randomUUID();
		this.keyB = UUID.randomUUID();

		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				this.userId, now, now);
		this.jdbc.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				tripId, this.userId, now, now);
		this.jdbc.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.userId, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'A 해운대해수욕장', ?)", this.placeA, now);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'B 광안리해변', ?)", this.placeB, now);
		this.jdbc.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, tripId, now);

		UUID v1 = insertVersion(1, null, "CREATE", null);
		insertItem(v1, this.keyA, 1, this.placeA, "09:00", "12:00");
		insertItem(v1, this.keyB, 2, this.placeB, "12:00", "18:00");
	}

	// ---- 시드 도우미 ----

	private UUID insertVersion(int version, Integer baseVersion, String operation, Integer revertedFrom) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at, reverted_from_version) VALUES (?, ?, ?, ?, ?, ?, ?, now(), ?)",
				versionId, this.itineraryId, version, baseVersion, operation, this.userId, "req_seed_" + version, revertedFrom);
		this.jdbc.update("UPDATE itineraries SET latest_version = ? WHERE itinerary_id = ?", version, this.itineraryId);
		return versionId;
	}

	private void insertItem(UUID versionId, UUID itemKey, int sequence, UUID placeId, String start, String end) {
		this.jdbc.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, start_time, end_time, stay_minutes, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, '2026-09-10', ?, ?, ?::time, ?::time, 180, FALSE, 'ESTIMATED', now())",
				UUID.randomUUID(), versionId, itemKey, sequence, placeId, start, end);
	}

	/** 2판 = "A 를 뺐다": B 만 남고 A 는 제외 목록에. 제외 경로가 만드는 모양 그대로. */
	private void seedVersionTwoAsRemovalOfA() {
		UUID v2 = insertVersion(2, 1, "REMOVE_ITEM", null);
		insertItem(v2, this.keyB, 1, this.placeB, "09:00", "18:00");
		this.jdbc.update(
				"INSERT INTO itinerary_excluded_place (itinerary_excluded_place_id, itinerary_version_id, place_id, "
						+ "item_key, excluded_by, reason_code, created_at) VALUES (?, ?, ?, ?, ?, 'USER_REMOVED', now())",
				UUID.randomUUID(), v2, this.placeA, this.keyA, this.userId);
	}

	/** 2판 = "순서를 바꿨다": B(1) A(2). */
	private void seedVersionTwoAsReorder() {
		UUID v2 = insertVersion(2, 1, "REORDER", null);
		insertItem(v2, this.keyB, 1, this.placeB, "09:00", "12:00");
		insertItem(v2, this.keyA, 2, this.placeA, "12:00", "18:00");
	}

	// ---- 조회 도우미 ----

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.userId.toString(), null, List.of());
	}

	private ResultActions revert(int baseVersion, Integer toVersion) throws Exception {
		String body = toVersion == null
				? "{\"baseVersion\":" + baseVersion + "}"
				: "{\"baseVersion\":" + baseVersion + ",\"toVersion\":" + toVersion + "}";
		return this.mockMvc.perform(post("/api/v1/itineraries/{id}/revert", this.itineraryId)
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asOwner())
				.content(body));
	}

	/** 그 판의 항목을 순번 순으로 (item_key, place_id, start_time) 로 읽는다. */
	private List<Map<String, Object>> itemsOf(int version) {
		return this.jdbc.queryForList(
				"SELECT i.item_key::text AS item_key, i.place_id::text AS place_id, i.sequence, i.start_time::text AS start_time "
						+ "FROM itinerary_item i JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = ? ORDER BY i.sequence",
				this.itineraryId, version);
	}

	private int exclusionCount(int version) {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM itinerary_excluded_place x JOIN itinerary_versions v "
						+ "ON v.itinerary_version_id = x.itinerary_version_id WHERE v.itinerary_id = ? AND v.version = ?",
				Integer.class, this.itineraryId, version);
	}

	private int latestVersion() {
		return this.jdbc.queryForObject("SELECT latest_version FROM itineraries WHERE itinerary_id = ?", Integer.class,
				this.itineraryId);
	}

	private Map<String, Object> versionRow(int version) {
		return this.jdbc.queryForMap(
				"SELECT operation, base_version, reverted_from_version FROM itinerary_versions WHERE itinerary_id = ? AND version = ?",
				this.itineraryId, version);
	}

	// ---- 테스트 ----

	/**
	 * 제외한 뒤 되돌리면 그 장소가 원래 자리·원래 시각으로 돌아오고 제외 목록도 풀린다.
	 * {@code ItineraryEditService.revert} 가 {@code target} 대신 {@code baseVersion} 의
	 * 내용을 복사하면 여기서 빨개진다.
	 */
	@Test
	@DisplayName("🔴 장소를 뺀 뒤 되돌리면 그 장소가 같은 itemKey·자리·시각으로 돌아오고 제외가 풀린다")
	void revertAfterRemovalRestoresPlaceAndClearsExclusion() throws Exception {
		seedVersionTwoAsRemovalOfA();

		revert(2, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(3))
				.andExpect(jsonPath("$.data.baseVersion").value(2))
				.andExpect(jsonPath("$.data.operation").value("REVERT"))
				.andExpect(jsonPath("$.data.revertedFromVersion").value(1))
				// 앱이 이 응답을 일정 전체로 받아 화면 상태에 넣는다 — 항목 둘이 보여야 한다.
				.andExpect(jsonPath("$.data.days[0].items.length()").value(2))
				.andExpect(jsonPath("$.error").doesNotExist());

		List<Map<String, Object>> v3 = itemsOf(3);
		assertThat(v3).extracting((r) -> r.get("item_key")).containsExactly(this.keyA.toString(), this.keyB.toString());
		assertThat(v3).extracting((r) -> r.get("place_id")).containsExactly(this.placeA.toString(), this.placeB.toString());
		assertThat(v3).extracting((r) -> r.get("start_time")).containsExactly("09:00:00", "12:00:00");
		assertThat(exclusionCount(3)).isZero();
		assertThat(latestVersion()).isEqualTo(3);

		// 중간 판(2)은 그대로다 — 덮어쓰기 금지(FR-ITN-08).
		assertThat(itemsOf(2)).extracting((r) -> r.get("item_key")).containsExactly(this.keyB.toString());
		assertThat(exclusionCount(2)).isEqualTo(1);
		assertThat(versionRow(3)).containsEntry("reverted_from_version", 1).containsEntry("base_version", 2);
	}

	@Test
	@DisplayName("순서를 바꾼 뒤 되돌리면 이전 순서로 돌아온다")
	void revertAfterReorderRestoresOrder() throws Exception {
		seedVersionTwoAsReorder();

		revert(2, null).andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(3));

		assertThat(itemsOf(3)).extracting((r) -> r.get("item_key")).containsExactly(this.keyA.toString(), this.keyB.toString());
		assertThat(itemsOf(3)).extracting((r) -> r.get("sequence")).containsExactly(1, 2);
	}

	/**
	 * 편집한 적 없는 일정에서는 되돌릴 것이 없다는 응답이 온다. {@code latest.baseVersion()}
	 * null 검사를 빼면 NPE 가 500 으로 나가 여기서 빨개진다.
	 */
	@Test
	@DisplayName("🔴 편집한 적 없는 일정은 422 ITINERARY_NOTHING_TO_REVERT 이고 판이 늘지 않는다")
	void nothingToRevertOnFreshItinerary() throws Exception {
		revert(1, null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOTHING_TO_REVERT"))
				.andExpect(jsonPath("$.error.fields").value(Matchers.hasItem("latestVersion=1")))
				.andExpect(jsonPath("$.data").doesNotExist());

		assertThat(latestVersion()).isEqualTo(1);
	}

	/**
	 * 되돌린 뒤 다시 편집해도 계속 동작한다. 되돌리기를 한 번 더 누르면 되돌리기 직전
	 * (= 다시 실행)으로 간다 — 되돌리기 판도 판이기 때문이다.
	 */
	@Test
	@DisplayName("🔴 되돌린 뒤 고정·되돌리기가 이어진다 — 되돌리기의 되돌리기는 다시 실행이다")
	void editingContinuesAfterRevertAndRevertIsItselfRevertible() throws Exception {
		seedVersionTwoAsRemovalOfA();
		revert(2, null).andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(3));

		// 3판 위에서 고정 — 평범한 편집이 이어진다.
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/lock", this.itineraryId, this.keyA)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"locked\":true,\"baseVersion\":3}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(4))
				.andExpect(jsonPath("$.data.days[0].items.length()").value(2));

		// 4판(고정) 되돌리기 → 5판 = 3판 내용(고정 전). revertedFrom = 3.
		revert(4, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(5))
				.andExpect(jsonPath("$.data.revertedFromVersion").value(3));
		Boolean lockedInV5 = this.jdbc.queryForObject(
				"SELECT i.locked FROM itinerary_item i JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = 5 AND i.item_key = ?",
				Boolean.class, this.itineraryId, this.keyA);
		assertThat(lockedInV5).isFalse();

		// 되돌리기의 되돌리기: 3판(REVERT, base 2) 위에서 누르면 2판(A 없음)으로 — 다시 실행.
		seedRedoScenarioCheck();
	}

	private void seedRedoScenarioCheck() throws Exception {
		// 5판은 REVERT(base 4) 이므로 되돌리면 4판 내용(고정됨)으로 간다 = 다시 실행.
		revert(5, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(6))
				.andExpect(jsonPath("$.data.revertedFromVersion").value(4));
		Boolean lockedInV6 = this.jdbc.queryForObject(
				"SELECT i.locked FROM itinerary_item i JOIN itinerary_versions v ON v.itinerary_version_id = i.itinerary_version_id "
						+ "WHERE v.itinerary_id = ? AND v.version = 6 AND i.item_key = ?",
				Boolean.class, this.itineraryId, this.keyA);
		assertThat(lockedInV6).isTrue();
		// 판 여섯이 전부 남아 있다.
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM itinerary_versions WHERE itinerary_id = ?",
				Integer.class, this.itineraryId);
		assertThat(count).isEqualTo(6);
	}

	/**
	 * 제외 목록도 판의 일부다 — 되돌리기의 되돌리기(다시 실행)로 제외 판(2)에 돌아가면 뺀 장소가 다시 빠지고
	 * 제외 목록도 돌아온다. 부수기: {@code revert} 가 {@code draft.exclusions()} 대신 빈 목록을 넘기면 여기서 빨개진다.
	 */
	@Test
	@DisplayName("🔴 다시 실행하면 제외 목록도 함께 돌아온다")
	void redoRestoresExclusionList() throws Exception {
		seedVersionTwoAsRemovalOfA();
		revert(2, null).andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(3));
		assertThat(exclusionCount(3)).isZero();

		revert(3, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(4))
				.andExpect(jsonPath("$.data.revertedFromVersion").value(2))
				.andExpect(jsonPath("$.data.days[0].items.length()").value(1));
		assertThat(itemsOf(4)).extracting((r) -> r.get("item_key")).containsExactly(this.keyB.toString());
		assertThat(exclusionCount(4)).isEqualTo(1);
	}

	/** {@code toVersion} 을 주면 목록에서 고른 그 판으로 간다. 범위 밖이면 400. */
	@Test
	@DisplayName("toVersion 으로 특정 판을 고를 수 있고, 현재 판 이상은 400 이다")
	void explicitTargetVersion() throws Exception {
		seedVersionTwoAsRemovalOfA();

		revert(2, 2)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_REVERT_TARGET_INVALID"));
		assertThat(latestVersion()).isEqualTo(2);

		revert(2, 1)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.version").value(3))
				.andExpect(jsonPath("$.data.revertedFromVersion").value(1));
	}

	/** 낡은 baseVersion 은 다른 편집과 같은 409 다 — 앱이 읽는 {@code latestVersion=<n>} 도 같다. */
	@Test
	@DisplayName("낡은 baseVersion 은 409 이고 error.fields 에 latestVersion=<n> 이 있다")
	void staleBaseVersionIsConflict() throws Exception {
		seedVersionTwoAsRemovalOfA();

		revert(1, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_VERSION_CONFLICT"))
				.andExpect(jsonPath("$.error.fields").value(Matchers.hasItem("latestVersion=2")));
		assertThat(latestVersion()).isEqualTo(2);
	}
}
