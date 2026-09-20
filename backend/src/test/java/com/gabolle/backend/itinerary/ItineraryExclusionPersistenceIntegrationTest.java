package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.ItineraryJobController;
import com.gabolle.backend.itinerary.presentation.ItineraryJobExceptionHandler;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 장소 제외·판 경고 코드가 실제 표에 남는가. 진짜 PostgreSQL 위에서 돈다 —
 * {@code uq_itinerary_excluded} 위반과 배열 칸({@code warning_codes}) 저장은 인메모리
 * 저장소로 검증할 수 없다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryExclusionPersistenceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	private static final ItineraryVersion.Versions EMPTY_VERSIONS =
			new ItineraryVersion.Versions(null, null, null, null, null);

	@Autowired
	private ItineraryRepository itineraryRepository;

	@Autowired
	private ItineraryJobController itineraryJobController;

	@Autowired
	private ItineraryJobExceptionHandler itineraryJobExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID ownerId;
	private UUID viewerId;
	private UUID tripId;
	private UUID itineraryId;
	private UUID version1Id;
	private UUID placeId;
	private UUID itemKey;

	@BeforeEach
	void seedItineraryAtVersion1() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.itineraryJobController)
				.setControllerAdvice(this.itineraryJobExceptionHandler)
				.build();

		this.ownerId = PersonalizationFixture.insertUser(this.jdbcTemplate);
		this.viewerId = PersonalizationFixture.insertUser(this.jdbcTemplate);
		this.tripId = PersonalizationFixture.insertTrip(this.jdbcTemplate, this.ownerId);
		PersonalizationFixture.insertPreferenceSnapshot(this.jdbcTemplate, this.ownerId, this.tripId);
		PersonalizationFixture.insertConstraintSnapshot(this.jdbcTemplate, this.ownerId, this.tripId);

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'OWNER', ?)",
				UUID.randomUUID(), this.tripId, this.ownerId, now);
		this.jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, 'VIEWER', ?)",
				UUID.randomUUID(), this.tripId, this.viewerId, now);

		this.itineraryId = UUID.randomUUID();
		this.version1Id = UUID.randomUUID();
		this.placeId = UUID.randomUUID();
		this.itemKey = UUID.randomUUID();

		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				this.itineraryId, this.tripId, now);
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, operation, "
						+ "created_by, request_id, created_at) VALUES (?, ?, 1, NULL, 'CREATE', ?, 'req_seed', ?)",
				this.version1Id, this.itineraryId, this.ownerId, now);
		this.jdbcTemplate.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, ?, ?)",
				this.placeId, "해운대해수욕장", now);
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, "
						+ "sequence, place_id, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, '2026-09-10', 1, ?, FALSE, 'UNKNOWN', ?)",
				UUID.randomUUID(), this.version1Id, this.itemKey, this.placeId, now);
	}

	private Authentication asOwner() {
		return new UsernamePasswordAuthenticationToken(this.ownerId.toString(), null, List.of());
	}

	private Authentication asViewer() {
		return new UsernamePasswordAuthenticationToken(this.viewerId.toString(), null, List.of());
	}

	private ItineraryVersion nextVersion(int version, Integer baseVersion, ItineraryVersion.Operation operation) {
		return new ItineraryVersion(UUID.randomUUID().toString(), this.itineraryId.toString(), version, baseVersion,
				operation, this.ownerId.toString(), "req_test_" + UUID.randomUUID(), EMPTY_VERSIONS, Instant.now());
	}

	@Test
	@DisplayName("① 제외 하나를 실어 저장하면 findContent 로 그대로 읽힌다")
	void appendVersionPersistsExclusionAndFindContentReadsItBack() {
		ItineraryVersion v2 = nextVersion(2, 1, ItineraryVersion.Operation.REMOVE_ITEM);
		ItineraryExclusion exclusion = new ItineraryExclusion(UUID.randomUUID().toString(),
				v2.itineraryVersionId(), this.placeId.toString(), this.itemKey.toString(),
				this.ownerId.toString(), ItineraryExclusion.REASON_USER_REMOVED, "택시를 놓쳤어요", Instant.now());

		this.itineraryRepository.appendVersion(v2, List.of(), List.of(), List.of(exclusion));

		ItineraryContent content = this.itineraryRepository.findContent(this.itineraryId.toString(), 2)
				.orElseThrow();
		assertThat(content.exclusions()).hasSize(1);
		ItineraryExclusion saved = content.exclusions().get(0);
		assertThat(saved.placeId()).isEqualTo(this.placeId.toString());
		assertThat(saved.itemKey()).isEqualTo(this.itemKey.toString());
		assertThat(saved.reasonCode()).isEqualTo(ItineraryExclusion.REASON_USER_REMOVED);
	}

	@Test
	@DisplayName("② ItineraryRevision.copyOf 로 다음 판을 만들면 제외가 새 판에도 있다")
	void copyOfCarriesExclusionIntoNextVersion() {
		ItineraryVersion v2 = nextVersion(2, 1, ItineraryVersion.Operation.REMOVE_ITEM);
		ItineraryExclusion exclusion = new ItineraryExclusion(UUID.randomUUID().toString(),
				v2.itineraryVersionId(), this.placeId.toString(), this.itemKey.toString(),
				this.ownerId.toString(), ItineraryExclusion.REASON_USER_REMOVED, null, Instant.now());
		this.itineraryRepository.appendVersion(v2, List.of(), List.of(), List.of(exclusion));

		ItineraryContent v2Content = this.itineraryRepository.findContent(this.itineraryId.toString(), 2)
				.orElseThrow();

		ItineraryVersion v3 = nextVersion(3, 2, ItineraryVersion.Operation.REGENERATE_DAY);
		ItineraryRevision.Draft draft = ItineraryRevision.copyOf(v2Content, v3.itineraryVersionId(), Instant.now());
		this.itineraryRepository.appendVersion(v3, draft.items(), draft.legs(), draft.exclusions());

		ItineraryContent v3Content = this.itineraryRepository.findContent(this.itineraryId.toString(), 3)
				.orElseThrow();
		assertThat(v3Content.exclusions()).hasSize(1);
		ItineraryExclusion carried = v3Content.exclusions().get(0);
		// 새 판을 가리키는 새 행이다 — PK 도 itineraryVersionId 도 바뀐다.
		assertThat(carried.itineraryVersionId()).isEqualTo(v3.itineraryVersionId());
		assertThat(carried.itineraryExclusionId()).isNotEqualTo(exclusion.itineraryExclusionId());
		// 나머지 사실은 그대로 물려받는다.
		assertThat(carried.placeId()).isEqualTo(this.placeId.toString());
		assertThat(carried.reasonCode()).isEqualTo(ItineraryExclusion.REASON_USER_REMOVED);
	}

	@Test
	@DisplayName("③ 같은 판에 같은 place_id 를 두 번 넣으면 uq_itinerary_excluded 위반")
	void duplicatePlaceInSameVersionViolatesUniqueConstraint() {
		ItineraryVersion v2 = nextVersion(2, 1, ItineraryVersion.Operation.REMOVE_ITEM);
		ItineraryExclusion first = new ItineraryExclusion(UUID.randomUUID().toString(),
				v2.itineraryVersionId(), this.placeId.toString(), this.itemKey.toString(),
				this.ownerId.toString(), ItineraryExclusion.REASON_USER_REMOVED, null, Instant.now());
		ItineraryExclusion duplicate = new ItineraryExclusion(UUID.randomUUID().toString(),
				v2.itineraryVersionId(), this.placeId.toString(), null,
				this.ownerId.toString(), ItineraryExclusion.REASON_USER_REMOVED, null, Instant.now());

		assertThrows(DataIntegrityViolationException.class,
				() -> this.itineraryRepository.appendVersion(v2, List.of(), List.of(), List.of(first, duplicate)));
	}

	@Test
	@DisplayName("④ warning_codes 를 저장하면 findVersion 으로 그대로 읽힌다")
	void warningCodesRoundTripThroughFindVersion() {
		ItineraryVersion v2 = new ItineraryVersion(UUID.randomUUID().toString(), this.itineraryId.toString(), 2, 1,
				ItineraryVersion.Operation.REGENERATE_DAY, this.ownerId.toString(),
				"req_test_" + UUID.randomUUID(), EMPTY_VERSIONS, Instant.now(), null,
				List.of("RECALC_NO_CANDIDATE"));

		this.itineraryRepository.appendVersion(v2, List.of(), List.of(), List.of());

		ItineraryVersion saved = this.itineraryRepository.findVersion(this.itineraryId.toString(), 2).orElseThrow();
		assertThat(saved.warningCodes()).containsExactly("RECALC_NO_CANDIDATE");
	}

	@Test
	@DisplayName("⑤ OWNER 가 항목을 빼면 202 와 recommendation_job 행이 생긴다")
	void ownerRemovingItemReturns202AndCreatesJobRow() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/remove", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"baseVersion\":1}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.jobId").exists());

		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM recommendation_job WHERE job_type = 'ITEM_REMOVE' "
						+ "AND resource_id = ? AND base_version = 1",
				Integer.class, this.itineraryId);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	@DisplayName("⑤ 낡은 baseVersion 은 409 이고 fields 에 latestVersion=1 이 있다")
	void staleBaseVersionReturnsConflict() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/remove", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"baseVersion\":0}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_VERSION_CONFLICT"))
				.andExpect(jsonPath("$.error.fields").value(org.hamcrest.Matchers.hasItem("latestVersion=1")));
	}

	@Test
	@DisplayName("⑤ VIEWER 는 403")
	void viewerCannotRemoveItem() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/remove", this.itineraryId, this.itemKey)
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asViewer())
						.content("{\"baseVersion\":1}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_FORBIDDEN"));
	}

	@Test
	@DisplayName("⑤ 없는 itemId 는 404")
	void unknownItemIdReturns404() throws Exception {
		this.mockMvc.perform(post("/api/v1/itineraries/{id}/items/{itemId}/remove", this.itineraryId,
						UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.principal(asOwner())
						.content("{\"baseVersion\":1}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ITINERARY_ITEM_NOT_FOUND"));
	}
}
