package com.gabolle.backend.place;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.AccommodationCategories;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 숙소는 두 길에서 정반대로 다뤄진다 — 일반 후보에서는 빠지고, 숙소 전용 조회에서는 나온다.
 *
 * <p>2026-09-21 까지 숙박 65곳은 {@code place.category} 가 비어 있었고, 그래서 숙소 목록이 0건이었다.
 * 그 칸을 채우자 숙소가 살아나는 대신 <b>일반 후보에서 숙소를 막아 주던 유일한 방벽</b>이 사라졌다 —
 * 후보 조회는 갈래가 빈 장소만 무조건 빼고, 요청이 갈래를 안 좁히면 나머지는 다 통과시킨다.
 * 가드가 없으면 호텔이 관광지처럼 하루 일정에 섞인다.
 *
 * <p>🔴 <b>두 방향을 한 시험에서 함께 증명한다.</b> 「안 나온다」만 시험하면 다음 사람이 가드를
 * 「숙소는 어디서도 안 나온다」로 잘못 넓혀도 빨간불이 안 켜지고, 숙소 목록이 조용히 0건으로
 * 돌아간다. 그게 이 작업이 고치려던 바로 그 증상이다.
 */
class AccommodationNotInCandidatesTest extends PlacePostgresIntegrationTest {

	private static final double CENTER_LAT = 35.1000;

	private static final double CENTER_LNG = 129.0000;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceCandidateQueryService candidateQueryService;

	@Autowired
	private PlaceSearchService placeSearchService;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("🔴 숙소는 일반 후보에 안 나오고, 숙소 전용 조회로는 나온다 — 한쪽만 막으면 목록이 0건으로 돌아간다")
	void lodgingIsHiddenFromCandidatesYetVisibleToTheAccommodationQuery() {
		String token = this.fixture.token();
		UUID hotel = this.fixture.insertPlace("호텔" + token, null, "LODGING", CENTER_LAT, CENTER_LNG);
		UUID beach = this.fixture.insertPlace("해변" + token, null, "SEA_BEACH", CENTER_LAT, CENTER_LNG);

		// 1. 갈래를 안 좁힌 일반 후보 — 여기서 호텔이 새면 하루 일정에 호텔이 관광지로 들어간다.
		PlaceCandidateResponse candidates = this.candidateQueryService.findCandidates(noCategoryNarrowing());

		assertThat(ids(candidates))
				.as("갈래를 안 좁힌 요청에 숙소가 섞였다 — 일정에 호텔이 관광지처럼 들어간다")
				.doesNotContain(hotel)
				.as("숙소를 빼려다 관광지까지 뺐다")
				.contains(beach);
		assertThat(candidates.appliedFilters())
				.as("숙소를 뺐다는 사실이 응답에 안 남으면 호출자는 안 뺀 목록으로 안다")
				.contains("NOT_ACCOMMODATION");

		// 2. 숙소 전용 조회 — 같은 장소가 여기서는 나와야 한다. 이 길이 숙소 지정이 쓰는 길이다.
		PlacePageResponse page = this.placeSearchService.listByCategories(AccommodationCategories.CODES, null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId)
				.as("후보에서 뺀 가드가 숙소 전용 조회까지 막았다 — 숙소 목록이 다시 0건이 된다")
				.contains(hotel)
				.doesNotContain(beach);
	}

	@Test
	@DisplayName("숙소를 콕 집어 달라고 해도 일반 후보로는 안 준다 — 숙소는 후보가 아니라 지정하는 것이다")
	void askingForLodgingByCategoryStillGivesNoCandidates() {
		String token = this.fixture.token();
		this.fixture.insertPlace("호텔" + token, null, "LODGING", CENTER_LAT, CENTER_LNG);

		PlaceCandidateRequest onlyLodging = new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), 2_000,
				AccommodationCategories.CODES,
				null, null, null, 0, 200);

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(onlyLodging);

		// 빈 목록이 답이다. 후보 조회는 추천 엔진이 쓰는 길이고 숙소는 그 길로 오지 않는다.
		assertThat(response.candidates()).isEmpty();
		assertThat(response.appliedFilters()).contains("NOT_ACCOMMODATION");
	}

	@Test
	@DisplayName("대소문자가 달라도 뺀다 — place.category 는 자유 문자열이라 소문자로 들어올 수 있다")
	void lodgingIsExcludedRegardlessOfLetterCase() {
		String token = this.fixture.token();
		UUID lowercase = this.fixture.insertPlace("소문자호텔" + token, null, "lodging", CENTER_LAT, CENTER_LNG);

		PlaceCandidateResponse candidates = this.candidateQueryService.findCandidates(noCategoryNarrowing());

		assertThat(ids(candidates)).doesNotContain(lowercase);
	}

	/** 갈래를 안 좁히고 표식도 안 거는 요청. 가드가 없으면 반경 안 모든 갈래가 통과한다. */
	private PlaceCandidateRequest noCategoryNarrowing() {
		return new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), 2_000,
				null, null, null, null, 0, 200);
	}

	private static List<UUID> ids(PlaceCandidateResponse response) {
		return response.candidates().stream().map(PlaceCandidateResponse.Candidate::placeId).toList();
	}
}
