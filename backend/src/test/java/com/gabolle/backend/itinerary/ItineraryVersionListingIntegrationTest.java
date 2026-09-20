package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryExceptionHandler;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 판 목록 조회가 실제 표에서 어떻게 읽히는가.
 *
 * <p>진짜 PostgreSQL 위에서 돈다. {@code reverted_from_version} 칸의 왕복과
 * {@code ck_itinerary_version_operation} 저장은 인메모리 저장소로 검증할 수 없다.
 *
 * <p>판 3(REVERT)은 시드 SQL 이 아니라 {@link ItineraryRepository#appendVersion} 으로
 * 직접 넣는다 — 그래야 {@code INSERT_VERSION_ON_CONFLICT_DO_NOTHING} 의 새 파라미터(?16)와
 * 읽기 쪽 매핑({@code JpaItineraryRepository#toDomain})을 양쪽 다 검증한다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryVersionListingIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	private static final ItineraryVersion.Versions EMPTY_VERSIONS =
			new ItineraryVersion.Versions(null, null, null, null, null);

	@Autowired
	private ItineraryRepository itineraryRepository;

	@Autowired
	private ItineraryQueryController queryController;

	@Autowired
	private ItineraryQueryExceptionHandler queryExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID ownerId;
	private UUID itineraryId;
	private UUID version1Id;

	@BeforeEach
	void seedItineraryWithThreeVersions() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.queryController)
				.setControllerAdvice(this.queryExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.ownerId = UUID.randomUUID();
		UUID tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();
		this.version1Id = UUID.randomUUID();
		UUID version2Id = UUID.randomUUID();

		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				this.ownerId, now, now);
		jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				tripId, this.ownerId, now, now);
		jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), tripId, this.ownerId, now);
		jdbcTemplate.update(
				// latest_version 을 2 로 시작한다 — 판 3(REVERT)은 appendVersion 이 조건부
				// UPDATE 로 옮긴다(baseVersion=2 에서만 움직인다).
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 2, ?)",
				this.itineraryId, tripId, now);
		jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed_1', ?)",
				this.version1Id, this.itineraryId, this.ownerId, now);
		jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 2, 1, 'LOCK_ITEM', ?, 'req_seed_2', ?)",
				version2Id, this.itineraryId, this.ownerId, now);

		// 판 3 은 시드 SQL 이 아니라 저장소를 통해 넣는다 — appendVersion 의 INSERT 매핑을
		// 검증하는 자리다.
		ItineraryContent version1Content = this.itineraryRepository
				.findContent(this.itineraryId.toString(), 1).orElseThrow();
		ItineraryVersion version3 = new ItineraryVersion(UUID.randomUUID().toString(),
				this.itineraryId.toString(), 3, 2, ItineraryVersion.Operation.REVERT,
				this.ownerId.toString(), "req_revert_" + UUID.randomUUID(), EMPTY_VERSIONS, Instant.now(),
				null, List.of(), 1);
		ItineraryRevision.Draft draft = ItineraryRevision.copyOf(version1Content,
				version3.itineraryVersionId(), Instant.now());
		this.itineraryRepository.appendVersion(version3, draft.items(), draft.legs(), draft.exclusions());
	}

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.ownerId.toString(), null, List.of());
	}

	@Test
	@DisplayName("OWNER 가 판 목록을 조회하면 최신 판이 먼저고 REVERT 판이 reverted_from_version 을 싣는다")
	void ownerListsVersionsNewestFirst() throws Exception {
		mockMvc.perform(get("/api/v1/itineraries/{id}/versions", this.itineraryId).principal(asOwner()))
				.andExpect(status().isOk())
				// 목록은 배열이 아니라 봉투 안의 data.items 다.
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.count").value(3))
				.andExpect(jsonPath("$.data.hasMore").value(false))
				.andExpect(jsonPath("$.data.items[0].version").value(3))
				.andExpect(jsonPath("$.data.items[1].version").value(2))
				.andExpect(jsonPath("$.data.items[2].version").value(1))
				.andExpect(jsonPath("$.data.items[0].operation").value("REVERT"))
				.andExpect(jsonPath("$.data.items[0].revertedFromVersion").value(1))
				.andExpect(jsonPath("$.data.items[0].baseVersion").value(2))
				.andExpect(jsonPath("$.data.items[1].revertedFromVersion").doesNotExist())
				.andExpect(jsonPath("$.meta.requestId").exists())
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	/**
	 * 판은 일정을 고칠 때마다 쌓여 끝이 없다. 상한에 걸렸다는 사실이 응답에 실려야 화면이
	 * 목록을 조용히 자르지 않는다.
	 */
	@Test
	@DisplayName("🔴 size 로 자르면 hasMore 가 참이고, 다음 쪽은 이어지는 판을 준다")
	void pagingReportsMoreAndContinues() throws Exception {
		mockMvc.perform(get("/api/v1/itineraries/{id}/versions", this.itineraryId)
						.param("size", "2").principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.hasMore").value(true))
				.andExpect(jsonPath("$.data.items[0].version").value(3))
				.andExpect(jsonPath("$.data.items[1].version").value(2));

		// 다음 쪽 — 같은 판이 다시 나오거나 건너뛰어지지 않는다.
		mockMvc.perform(get("/api/v1/itineraries/{id}/versions", this.itineraryId)
						.param("size", "2").param("page", "1").principal(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.hasMore").value(false))
				.andExpect(jsonPath("$.data.items[0].version").value(1));
	}

	@Test
	@DisplayName("여행 회원이 아닌 사용자가 조회하면 404 ITINERARY_NOT_FOUND — 존재를 감춘다")
	void nonMemberGetsNotFound() throws Exception {
		UUID strangerId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'stranger', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				strangerId, now, now);

		Authentication stranger = new UsernamePasswordAuthenticationToken(strangerId.toString(), null, List.of());

		mockMvc.perform(get("/api/v1/itineraries/{id}/versions", this.itineraryId).principal(stranger))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));
	}

	@Test
	@DisplayName("없는 일정 id 로 조회하면 404")
	void unknownItineraryReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/itineraries/{id}/versions", UUID.randomUUID()).principal(asOwner()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_NOT_FOUND"));
	}

	@Test
	@DisplayName("REVERT 가 아닌데 revertedFromVersion 이 있으면 IllegalArgumentException")
	void revertedFromVersionOnNonRevertOperationIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ItineraryVersion(UUID.randomUUID().toString(),
				this.itineraryId.toString(), 4, 3, ItineraryVersion.Operation.LOCK_ITEM,
				this.ownerId.toString(), "req_bad_1", EMPTY_VERSIONS, Instant.now(), null, List.of(), 1));
	}

	@Test
	@DisplayName("REVERT 인데 revertedFromVersion 이 없으면 IllegalArgumentException")
	void revertWithoutRevertedFromVersionIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ItineraryVersion(UUID.randomUUID().toString(),
				this.itineraryId.toString(), 4, 3, ItineraryVersion.Operation.REVERT,
				this.ownerId.toString(), "req_bad_2", EMPTY_VERSIONS, Instant.now(), null, List.of(), null));
	}
}
