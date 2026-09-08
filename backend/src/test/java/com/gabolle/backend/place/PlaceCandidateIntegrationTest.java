package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 후보 사전 필터 — S15P21E201-102.
 *
 * <p>완료 기준 셋 중 <b>"질의 개수가 장소 수에 비례하지 않는다"</b> 를 실제로 재는 것이 이
 * 클래스의 핵심이다. 응답만 보면 N+1 이 나도 정상으로 보이므로, Hibernate 통계로 문장 수를 센다.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PlaceCandidateIntegrationTest extends PlacePostgresIntegrationTest {

	private static final double CENTER_LAT = 35.1000;

	private static final double CENTER_LNG = 129.0000;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceCandidateQueryService candidateQueryService;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

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
	@DisplayName("🔴 장소가 3건일 때와 30건일 때 질의 수가 같다 — 장소 수에 비례하면 안 된다")
	void queryCountDoesNotGrowWithPlaceCount() {
		insertNearbyPlaces(3, "SEA");
		long withThree = countStatementsWhileFinding(request(200, 0));

		insertNearbyPlaces(27, "SEA");
		long withThirty = countStatementsWhileFinding(request(200, 0));

		assertThat(withThree).isEqualTo(withThirty);
		// 장소 하나와 그 장소들의 피처 — 둘이다.
		assertThat(withThirty).isEqualTo(2);
	}

	@Test
	@DisplayName("반경 밖 장소는 후보에 안 들어간다 — 경계상자는 넉넉하고 거리는 자바가 다시 잰다")
	void placesOutsideRadiusAreDropped() {
		UUID near = this.fixture.insertPlace("가까운곳", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(near, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		// 위도 0.05도는 약 5.5km 다.
		UUID far = this.fixture.insertPlace("먼곳", null, "CAFE", CENTER_LAT + 0.05, CENTER_LNG);
		this.fixture.insertTagFeature(far, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(request(2_000, 0));

		assertThat(ids(response)).contains(near).doesNotContain(far);
	}

	@Test
	@DisplayName("🔴 UNKNOWN 표식은 조건을 만족한 것으로 세지 않는다")
	void unknownFeatureDoesNotSatisfyRequirement() {
		UUID verified = this.fixture.insertPlace("확인됨", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(verified, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		UUID unknown = this.fixture.insertPlace("모름", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(unknown, "INTEREST_TAG", "SEA", "UNKNOWN", null);

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(request(2_000, 0));

		assertThat(ids(response)).contains(verified).doesNotContain(unknown);
	}

	@Test
	@DisplayName("🔴 뺄 표식이 있으면 뺀다 — 알레르기 같은 하드 필터가 이 길로 돈다")
	void excludedFeatureRemovesCandidate() {
		UUID safe = this.fixture.insertPlace("안전", null, "RESTAURANT", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(safe, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		UUID risky = this.fixture.insertPlace("위험", null, "RESTAURANT", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(risky, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		this.fixture.insertTagFeature(risky, "ALLERGEN_TAG", "PEANUT", "VERIFIED", "{\"present\": true}");

		PlaceCandidateRequest request = new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), 2_000,
				null,
				List.of(new PlaceCandidateRequest.FeatureMatch("INTEREST_TAG", "SEA")),
				List.of(new PlaceCandidateRequest.FeatureMatch("ALLERGEN_TAG", "PEANUT")),
				null, 0, 200);

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(request);

		assertThat(ids(response)).contains(safe).doesNotContain(risky);
		assertThat(response.appliedFilters()).contains("EXCLUDED_FEATURES");
	}

	@Test
	@DisplayName("종류를 바꾸면 후보가 달라진다")
	void categoryFilterChangesCandidates() {
		UUID cafe = this.fixture.insertPlace("카페", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(cafe, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		UUID museum = this.fixture.insertPlace("박물관", null, "MUSEUM", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(museum, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceCandidateRequest onlyCafe = new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), 2_000,
				List.of("CAFE"),
				List.of(new PlaceCandidateRequest.FeatureMatch("INTEREST_TAG", "SEA")),
				null, null, 0, 200);

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(onlyCafe);

		assertThat(ids(response)).contains(cafe).doesNotContain(museum);
		assertThat(response.appliedFilters()).contains("CATEGORY");
	}

	@Test
	@DisplayName("🔴 최소 개수를 못 채우면 조건을 풀지 않고 못 채웠다고 알린다")
	void belowMinimumIsReportedNotPapered() {
		UUID one = this.fixture.insertPlace("하나뿐", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(one, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(request(2_000, 20));

		assertThat(response.belowMinimum()).isTrue();
		assertThat(response.minimumRequired()).isEqualTo(20);
		assertThat(response.generatedCount()).isLessThan(20);
		assertThat(ids(response)).contains(one);
	}

	@Test
	@DisplayName("🔴 영업시간은 못 걸렀다고 응답에 적는다 — 조용히 무시하면 문 닫은 곳이 추천된다")
	void openingHoursFilterIsReportedAsNotApplied() {
		UUID place = this.fixture.insertPlace("영업시간", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(place, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceCandidateRequest request = new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), 2_000, null,
				List.of(new PlaceCandidateRequest.FeatureMatch("INTEREST_TAG", "SEA")),
				null, OffsetDateTime.now(), 0, 200);

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(request);

		assertThat(response.notApplied())
				.extracting(PlaceCandidateResponse.NotApplied::filter)
				.contains("OPENING_HOURS");
		assertThat(response.notApplied())
				.extracting(PlaceCandidateResponse.NotApplied::reason)
				.contains("NOT_COLLECTED");
	}

	@Test
	@DisplayName("후보가 거리순으로 오고 datasetVersion 이 실린다")
	void candidatesAreOrderedByDistanceAndCarryDatasetVersion() {
		UUID far = this.fixture.insertPlace("조금먼곳", null, "CAFE", CENTER_LAT + 0.01, CENTER_LNG);
		this.fixture.insertTagFeature(far, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		UUID near = this.fixture.insertPlace("바로옆", null, "CAFE", CENTER_LAT, CENTER_LNG);
		this.fixture.insertTagFeature(near, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceCandidateResponse response = this.candidateQueryService.findCandidates(request(5_000, 0));

		List<UUID> ordered = ids(response);
		assertThat(ordered.indexOf(near)).isLessThan(ordered.indexOf(far));
		assertThat(response.datasetVersions()).contains("fixture-test");
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private PlaceCandidateRequest request(int radiusM, int minimumCount) {
		return new PlaceCandidateRequest(
				new PlaceCandidateRequest.Center(CENTER_LAT, CENTER_LNG), radiusM, null,
				List.of(new PlaceCandidateRequest.FeatureMatch("INTEREST_TAG", "SEA")),
				null, null, minimumCount, 200);
	}

	private void insertNearbyPlaces(int count, String featureKey) {
		for (int index = 0; index < count; index++) {
			UUID placeId = this.fixture.insertPlace("후보" + index, null, "CAFE",
					CENTER_LAT + index * 0.0001, CENTER_LNG);
			this.fixture.insertTagFeature(placeId, "INTEREST_TAG", featureKey, "VERIFIED", "{\"present\": true}");
		}
	}

	private long countStatementsWhileFinding(PlaceCandidateRequest request) {
		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		long before = statistics.getPrepareStatementCount();
		this.candidateQueryService.findCandidates(request);
		return statistics.getPrepareStatementCount() - before;
	}

	private List<UUID> ids(PlaceCandidateResponse response) {
		return response.candidates().stream().map(PlaceCandidateResponse.Candidate::placeId).toList();
	}
}
