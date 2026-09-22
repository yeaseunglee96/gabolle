package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.gabolle.backend.place.api.PlaceDetailController;
import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.service.PlaceDetailService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 장소 상세의 일정 포함 여부.
 * 판정 구현({@code ItineraryPlaceMembershipService})이 {@code itinerary} 패키지에 있어서 장소
 * 슬라이스에서는 언제나 "모른다" 가 나온다 — 실제 판정은 두 도메인이 함께 있는
 * {@link ItinerarySliceApplication} 에서만 확인할 수 있다.
 * 핵심은 응답이 남의 일정 내용을 알려주지 않는가다. 남의 일정 식별자를 넣었을 때 그 일정에
 * 그 장소가 있든 없든 응답이 완전히 같아야 한다 — 하나라도 갈리면 장소 목록을 훑으며 남의
 * 일정을 재구성할 수 있다.
 * standalone MockMvc 에 컨트롤러 빈만 올리고 서비스·저장소는 컨텍스트의 진짜 구현을 쓴다.
 * 인증 필터 체인만 안 태우고 {@code Authentication} 을 직접 준다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryPlaceInclusionIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceDetailService placeDetailService;

	@Autowired
	private PlaceDetailController placeDetailController;

	private PlaceFixture fixture;

	private MockMvc mockMvc;

	private UUID ownerId;
	private UUID viewerId;
	private UUID strangerId;

	private UUID tripId;
	private UUID itineraryId;

	/** 최신 판(v2)에 있는 장소. */
	private UUID placeInLatest;

	/** v1 에는 있었지만 최신 판에서 빠진 장소. */
	private UUID placeDroppedInLatest;

	/** 최신 판에서 새로 들어온 장소. */
	private UUID placeAddedInLatest;

	/** 이 일정에 한 번도 들어간 적 없는 장소. */
	private UUID placeNeverInItinerary;

	/**
	 * 판을 둘 만든다 — v1 은 {@code placeInLatest}·{@code placeDroppedInLatest}, v2 는
	 * {@code placeInLatest}·{@code placeAddedInLatest}. 판 하나로는 "최신 판만 본다" 를 잴 수 없다 —
	 * 낡은 판을 보는 구현도 판이 하나면 똑같이 초록이 된다.
	 */
	@BeforeEach
	void seedTripItineraryAndPlaces() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(this.placeDetailController)
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();

		this.fixture = new PlaceFixture(this.jdbcTemplate);

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		this.ownerId = UUID.randomUUID();
		this.viewerId = UUID.randomUUID();
		this.strangerId = UUID.randomUUID();
		createUser(this.ownerId, now);
		createUser(this.viewerId, now);
		// 비회원(strangerId)은 app_user 에는 있지만 trip_member 에는 없다 — "가입은 했지만
		// 이 여행의 회원은 아니다" 를 재현한다.
		createUser(this.strangerId, now);

		this.tripId = UUID.randomUUID();
		this.itineraryId = UUID.randomUUID();

		this.jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-10', '2026-09-12', 1, ?, ?)",
				this.tripId, this.ownerId, now, now);
		insertMember(this.tripId, this.ownerId, "OWNER", now);
		insertMember(this.tripId, this.viewerId, "VIEWER", now);

		this.placeInLatest = this.fixture.insertPlace("최신판에있는곳", null, "ATTRACTION", 35.1, 129.0);
		this.placeDroppedInLatest = this.fixture.insertPlace("최신판에서빠진곳", null, "ATTRACTION", 35.2, 129.1);
		this.placeAddedInLatest = this.fixture.insertPlace("최신판에더한곳", null, "ATTRACTION", 35.3, 129.2);
		this.placeNeverInItinerary = this.fixture.insertPlace("일정밖의곳", null, "ATTRACTION", 35.4, 129.3);

		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 2, ?)",
				this.itineraryId, this.tripId, now);

		UUID firstVersionId = insertVersion(1, null, "CREATE", now);
		insertItem(firstVersionId, 1, this.placeInLatest, now);
		insertItem(firstVersionId, 2, this.placeDroppedInLatest, now);

		UUID latestVersionId = insertVersion(2, 1, "REMOVE_ITEM", now);
		insertItem(latestVersionId, 1, this.placeInLatest, now);
		insertItem(latestVersionId, 2, this.placeAddedInLatest, now);
	}

	/**
	 * {@code itinerary_item.place_id} 가 {@code place} 를 참조하므로 항목을 먼저 지워야 픽스처가
	 * 장소를 지울 수 있다. 판을 지우면 항목·구간은 함께 사라진다.
	 */
	@AfterEach
	void tearDown() {
		this.jdbcTemplate.update("DELETE FROM itinerary_versions WHERE itinerary_id = ?", this.itineraryId);
		this.jdbcTemplate.update("DELETE FROM itineraries WHERE itinerary_id = ?", this.itineraryId);
		this.jdbcTemplate.update("DELETE FROM trip_member WHERE trip_id = ?", this.tripId);
		this.jdbcTemplate.update("DELETE FROM trip WHERE trip_id = ?", this.tripId);
		this.fixture.cleanUp();
		this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id IN (?, ?, ?)",
				this.ownerId, this.viewerId, this.strangerId);
	}


	@Test
	@DisplayName("그 일정에 든 장소를 그 일정 식별자와 함께 조회하면 INCLUDED 다")
	void placeInTheItineraryIsIncluded() {
		PlaceDetailResponse detail = detailFor(this.ownerId, this.placeInLatest, this.itineraryId);

		assertThat(detail.itineraryInclusion().state()).isEqualTo("INCLUDED");
		assertThat(detail.itineraryInclusion().reason()).isNull();
	}

	@Test
	@DisplayName("회원이면 VIEWER 도 포함 여부를 볼 수 있다 — 읽기 권한은 편집 권한과 다른 문턱이다")
	void viewerMemberAlsoSeesInclusion() {
		PlaceDetailResponse detail = detailFor(this.viewerId, this.placeInLatest, this.itineraryId);

		assertThat(detail.itineraryInclusion().state()).isEqualTo("INCLUDED");
	}


	@Test
	@DisplayName("그 일정에 없는 장소는 NOT_INCLUDED 다")
	void placeOutsideTheItineraryIsNotIncluded() {
		PlaceDetailResponse detail = detailFor(this.ownerId, this.placeNeverInItinerary, this.itineraryId);

		assertThat(detail.itineraryInclusion().state()).isEqualTo("NOT_INCLUDED");
		assertThat(detail.itineraryInclusion().reason()).isNull();
	}

	/**
	 * 판정 기준이 최신 판이라는 것을 잰다. 두 장소를 한 메서드에서 나란히 보는 이유는 낡은 판을
	 * 보는 구현이면 둘 다 반대로 나오기 때문이다 — 하나만 보면 어느 판을 읽고 있는지 구분되지 않는다.
	 */
	@Test
	@DisplayName("낡은 판의 내용은 보지 않는다 — v1 에서만 있던 곳은 NOT_INCLUDED, v2 에서 더한 곳은 INCLUDED")
	void onlyTheLatestVersionDecides() {
		PlaceDetailResponse dropped = detailFor(this.ownerId, this.placeDroppedInLatest, this.itineraryId);
		PlaceDetailResponse added = detailFor(this.ownerId, this.placeAddedInLatest, this.itineraryId);

		assertThat(dropped.itineraryInclusion().state()).isEqualTo("NOT_INCLUDED");
		assertThat(added.itineraryInclusion().state()).isEqualTo("INCLUDED");
	}


	@Test
	@DisplayName("itineraryId 를 주지 않으면 UNAVAILABLE 이고 reason 이 채워져 있다")
	void withoutItineraryIdTheAnswerIsUnavailableWithAReason() {
		PlaceDetailResponse detail = detailFor(this.ownerId, this.placeInLatest, null);

		assertThat(detail.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");
		assertThat(detail.itineraryInclusion().reason()).isEqualTo("ITINERARY_NOT_SPECIFIED");
	}


	/**
	 * 남의 일정 식별자를 넣었을 때 그 장소가 실제로 들어 있는 경우와 아닌 경우의 응답이 완전히
	 * 같다는 것을 한 메서드에서 나란히 단정한다. 두 경우를 따로 보는 테스트로는 증명할 수 없다 —
	 * 각각 UNAVAILABLE 이기만 하면 통과하고 {@code reason} 이 갈리는 것을 놓친다.
	 */
	@Test
	@DisplayName("🔴 남의 일정 식별자로는 그 장소가 들어 있든 없든 완전히 같은 응답이 나온다")
	void strangerSeesTheSameAnswerWhetherThePlaceIsInTheItineraryOrNot() {
		PlaceDetailResponse forPlaceInside =
				detailFor(this.strangerId, this.placeInLatest, this.itineraryId);
		PlaceDetailResponse forPlaceOutside =
				detailFor(this.strangerId, this.placeNeverInItinerary, this.itineraryId);

		// 둘 다 판정 자체를 하지 않는다.
		assertThat(forPlaceInside.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");
		assertThat(forPlaceOutside.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");

		// 그리고 두 응답의 포함 여부 칸이 값까지 같다 — reason 으로도 갈리지 않는다.
		assertThat(forPlaceInside.itineraryInclusion())
				.isEqualTo(forPlaceOutside.itineraryInclusion());

		// 회원이 같은 질문을 하면 두 값이 갈린다 — 위의 "같다" 가 판정을 못 해서가 아니라
		// 권한 때문이라는 것을 여기서 대조한다.
		assertThat(detailFor(this.ownerId, this.placeInLatest, this.itineraryId).itineraryInclusion())
				.isNotEqualTo(detailFor(this.ownerId, this.placeNeverInItinerary, this.itineraryId)
						.itineraryInclusion());
	}

	/**
	 * 없는 일정과 남의 일정이 {@code reason} 까지 같아야 한다. 다르면 식별자를 넣어 보는 것만으로
	 * "그 일정이 존재하는가" 를 가릴 수 있다.
	 */
	@Test
	@DisplayName("없는 일정 식별자의 응답은 남의 일정 식별자의 응답과 reason 까지 같다")
	void unknownItineraryIsIndistinguishableFromSomeoneElsesItinerary() {
		PlaceDetailResponse unknown =
				detailFor(this.strangerId, this.placeInLatest, UUID.randomUUID());
		PlaceDetailResponse someoneElses =
				detailFor(this.strangerId, this.placeInLatest, this.itineraryId);

		assertThat(unknown.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");
		assertThat(unknown.itineraryInclusion().reason()).isEqualTo("ITINERARY_NOT_VISIBLE");
		assertThat(unknown.itineraryInclusion()).isEqualTo(someoneElses.itineraryInclusion());
	}

	/**
	 * 회원이 없는 일정을 물어도 같은 답이다. 회원에게만 구분해 주면 회원 하나만 있으면 다른
	 * 여행의 일정 존재 여부를 가릴 수 있다.
	 */
	@Test
	@DisplayName("회원이 없는 일정을 물어도 UNAVAILABLE ITINERARY_NOT_VISIBLE 이다")
	void unknownItineraryIsUnavailableEvenForAMember() {
		PlaceDetailResponse detail = detailFor(this.ownerId, this.placeInLatest, UUID.randomUUID());

		assertThat(detail.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");
		assertThat(detail.itineraryInclusion().reason()).isEqualTo("ITINERARY_NOT_VISIBLE");
	}


	/**
	 * 포함 여부는 이 응답의 한 칸이다. 판정에 실패했다고 장소 정보가 비거나 요청이 실패하면 장소
	 * 상세 화면 전체가 깨진다 — 네 경우를 한 메서드에서 보는 이유는 그중 하나만 비어도 실패해야
	 * 하기 때문이다.
	 */
	@Test
	@DisplayName("포함 여부를 모르는 경우에도 장소 정보 본체는 정상적으로 나온다")
	void placeBodyIsIntactWhateverTheInclusionIs() {
		List<PlaceDetailResponse> answers = List.of(
				detailFor(this.ownerId, this.placeInLatest, this.itineraryId),
				detailFor(this.ownerId, this.placeInLatest, null),
				detailFor(this.strangerId, this.placeInLatest, this.itineraryId),
				detailFor(this.ownerId, this.placeInLatest, UUID.randomUUID()));

		assertThat(answers).hasSize(4);
		assertThat(answers).allSatisfy((detail) -> {
			assertThat(detail.placeId()).isEqualTo(this.placeInLatest);
			assertThat(detail.nameKo()).endsWith("최신판에있는곳");
			assertThat(detail.provenance().sourceType()).isEqualTo("FIXTURE");
			assertThat(detail.features()).isNotEmpty();
			assertThat(detail.resolvedLanguage()).isEqualTo("ko");
			assertThat(detail.itineraryInclusion()).isNotNull();
		});

		// 네 응답이 모두 같은 장소를 담고 있으면서 포함 여부만 서로 다르다.
		assertThat(answers).extracting((detail) -> detail.itineraryInclusion().state())
				.containsExactly("INCLUDED", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE");
	}


	@Test
	@DisplayName("HTTP 로 itineraryId 를 주면 200 에 INCLUDED 와 장소 정보가 함께 온다")
	void httpRequestWithItineraryIdCarriesInclusionAndPlaceBody() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeInLatest)
						.principal(as(this.ownerId))
						.param("itineraryId", this.itineraryId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").exists())
				.andExpect(jsonPath("$.data.placeId").value(this.placeInLatest.toString()))
				.andExpect(jsonPath("$.data.nameKo").value(org.hamcrest.Matchers.endsWith("최신판에있는곳")))
				.andExpect(jsonPath("$.data.itineraryInclusion.state").value("INCLUDED"));
	}

	/**
	 * 남의 일정 식별자에도 200 이다. 404·403 을 주면 그 응답 코드가 "그 일정이 존재하는가" 를
	 * 알려주는 신호가 되고, 동시에 장소 상세 화면이 통째로 깨진다.
	 */
	@Test
	@DisplayName("HTTP 로 남의 일정 식별자를 줘도 200 이고 장소 정보는 그대로 온다")
	void httpRequestWithSomeoneElsesItineraryIsStillOkWithPlaceBody() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeInLatest)
						.principal(as(this.strangerId))
						.param("itineraryId", this.itineraryId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").exists())
				.andExpect(jsonPath("$.data.placeId").value(this.placeInLatest.toString()))
				.andExpect(jsonPath("$.data.itineraryInclusion.state").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.data.itineraryInclusion.reason").value("ITINERARY_NOT_VISIBLE"));
	}


	private PlaceDetailResponse detailFor(UUID viewerId, UUID placeId, UUID itineraryId) {
		PlaceDetailResponse detail = this.placeDetailService.get(placeId, viewerId, null, itineraryId);
		// 응답을 들여다보는 단정 앞에 응답 자체가 있다는 것을 먼저 확인한다 — 아래 단정들이
		// 빈 응답 위에서 조용히 통과하지 않게 한다.
		assertThat(detail).isNotNull();
		assertThat(detail.placeId()).isEqualTo(placeId);
		assertThat(detail.itineraryInclusion()).isNotNull();
		return detail;
	}

	private Authentication as(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private void createUser(UUID userId, OffsetDateTime now) {
		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
						+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				userId, now, now);
	}

	private void insertMember(UUID tripId, UUID userId, String role, OffsetDateTime now) {
		this.jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), tripId, userId, role, now);
	}

	private UUID insertVersion(int version, Integer baseVersion, String operation, OffsetDateTime now) {
		UUID versionId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
						+ "operation, created_by, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				versionId, this.itineraryId, version, baseVersion, operation, this.ownerId,
				"req_seed_" + version, now);
		return versionId;
	}

	private void insertItem(UUID versionId, int sequence, UUID placeId, OffsetDateTime now) {
		this.jdbcTemplate.update(
				"INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
						+ "visit_date, sequence, place_id, locked, data_status, created_at) "
						+ "VALUES (?, ?, ?, 0, '2026-09-10', ?, ?, FALSE, 'UNKNOWN', ?)",
				UUID.randomUUID(), versionId, UUID.randomUUID(), sequence, placeId, now);
	}
}
