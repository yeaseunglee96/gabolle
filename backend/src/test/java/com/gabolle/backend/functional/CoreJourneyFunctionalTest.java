package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryEditJobRequest;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationResultResponse;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.TripDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 핵심 여정 — S15P21E201-780. 이 서비스의 돈이 되는 경로를 실제 HTTP로 이어 붙인다.
 *
 * <p>회원가입 → 로그인 → 여행 생성 → 추천 Job 생성 → 폴링으로 완료 확인 → 일정 편집 Job
 * 제출까지, 앞 단계 응답을 그대로 다음 단계 요청에 넣어 한 테스트로 끝까지 돈다. 각 단계는
 * 지금까지 슬라이스 테스트에서만 각자 검증됐고, 이어 붙이는 시나리오는 한 번도 자동으로
 * 돈 적이 없었다(-780 배경).
 *
 * <p>🔴 {@link FunctionalJourneyTest#pollUntil}은 5초 상한이다 — 추천 계산이 실제 외부
 * 호출 없이(테스트 DB엔 place 데이터가 없다) 돌아 그 안에 끝나는지는 처음 돌려보기 전엔
 * 몰랐다. 여기서 그대로 재사용해 본다 — 상한이 부족하면 이 클래스 안에서 늘린다.
 *
 * <p>🔴 실측(2026-09-10) — {@code gabolle.recommendation.service-version}·
 * {@code deployment-environment}는 검사 환경에서 기본값이 빈 문자열이다(운영은 Jenkins가
 * {@code -e}로 채운다, application-dev.properties 112~125행). {@code RecommendationService
 * .resolveMissingVersions}는 이 둘이 비어 있으면 후보가 몇 건이든 무관하게 VERSION_UNRESOLVED로
 * 막는다 — 장소를 아무리 넉넉히 심어도 이 값이 없으면 그대로 실패한다. 처음엔 이것을 "후보가
 * 부족하다"로 오인해 장소 수를 3→12→30으로 계속 늘렸지만 원인이 아니었다.
 * {@link RecommendationWithRealPlacesFunctionalTest}(S15P21E201-804)가 이미 같은 문제를
 * 겪고 {@code @TestPropertySource}로 이 클래스만 채우는 방식으로 풀어 둔 것을 그대로 따른다 —
 * 하네스({@link FunctionalJourneyTest})의 공통 프로퍼티에 더하지 않는 이유도 그쪽 클래스
 * 주석과 같다(여정마다 프로퍼티가 다르면 Spring이 컨텍스트를 새로 캐시한다).
 */
@TestPropertySource(properties = { "gabolle.recommendation.service-version=test-local",
		"gabolle.recommendation.deployment-environment=test" })
class CoreJourneyFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PlaceFixture placeFixture;

	/** {@link #cleanUpPlaces} 가 참조를 먼저 끊는 데 쓴다 — 아래 javadoc 참고. */
	private final List<UUID> seededPlaceIds = new ArrayList<>();

	/**
	 * 🔴 실측(2026-09-10) — 이 여정은 실제로 일정을 조립해 {@code itinerary_item}이 방금 심은
	 * 장소를 참조한다(그것이 이 테스트가 확인하려는 것이다). {@link PlaceFixture#cleanUp}이
	 * 그 참조를 모르고 {@code place}를 바로 지우려다 {@code fk_itinerary_item_place}에 걸려
	 * 일부 행을 못 지우고 남기면, 그 남은 행이 {@link RecommendationWithRealPlacesFunctionalTest
	 * #ensurePlaces}를 속인다 — "{@code place}가 이미 있으니 표본 200곳을 안 넣어도 된다"고
	 * 잘못 판단해 그 검사가 실제로는 CI에서 실패했다(2026-09-10 실측, 파이프라인 188411).
	 * 그래서 베스트에포트로 삼키지 않고, {@code place}를 지우기 전에 참조하는 세 표
	 * (itinerary_item·itinerary_leg·itinerary_excluded_place)에서 먼저 행을 지운다 —
	 * 이 트리를 통째로 지우는 cascade가 없어(itinerary_versions까지 손으로 타고 내려가야
	 * 한다) 참조 쪽에서 바로 지우는 것이 더 안전하다.
	 */
	@AfterEach
	void cleanUpPlaces() {
		if (!this.seededPlaceIds.isEmpty()) {
			this.jdbcTemplate.batchUpdate("DELETE FROM itinerary_item WHERE place_id = ?", this.seededPlaceIds,
					this.seededPlaceIds.size(), (ps, placeId) -> ps.setObject(1, placeId));
			this.jdbcTemplate.batchUpdate(
					"DELETE FROM itinerary_leg WHERE from_place_id = ? OR to_place_id = ?", this.seededPlaceIds,
					this.seededPlaceIds.size(), (ps, placeId) -> {
						ps.setObject(1, placeId);
						ps.setObject(2, placeId);
					});
			this.jdbcTemplate.batchUpdate("DELETE FROM itinerary_excluded_place WHERE place_id = ?",
					this.seededPlaceIds, this.seededPlaceIds.size(), (ps, placeId) -> ps.setObject(1, placeId));
		}
		if (this.placeFixture != null) {
			this.placeFixture.cleanUp();
		}
	}

	@Test
	@DisplayName("회원가입→로그인→여행 생성→추천→일정 편집 Job 제출까지 실제 HTTP로 끝까지 이어진다")
	void coreJourneyEndToEnd() {
		AuthedClient client = loginAsNewUser("core-journey");

		// 0) 후보 장소 시딩 — 🔴 실측(2026-09-10) — functionaltest 스키마엔 place 행이
		//    하나도 없어서, 장소 없이 추천 Job을 돌리면 후보가 0건이라 dataset_version을
		//    구할 수 없고 VERSION_UNRESOLVED로 실패한다(BaselineRecommendationEngine
		//    javadoc — "unknown"을 지어내지 않는다는 설계). PlaceFixture(place 통합
		//    테스트들이 쓰는 것과 같은 픽스처)로 여행 출발지 근처에 실제 후보를 넣는다.
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		double originLat = 35.1152;
		double originLng = 129.0423;
		for (int i = 0; i < 12; i++) {
			UUID placeId = this.placeFixture.insertPlace("여정테스트장소" + i, "JourneyPlace" + i, "CAFE",
					originLat + (i * 0.001), originLng + (i * 0.001));
			this.placeFixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
			this.seededPlaceIds.add(placeId);
		}

		// 1) 여행 생성 — TRIP-01.
		//    🔴 실측(2026-09-10) — originLat/originLng는 DTO에는 @NotNull이 없지만
		//    실제로는 필수다(TRIP_VALIDATION_FAILED: "출발지 좌표가 없다"). 부산역 좌표를 쓴다.
		//    🔴 실측(2026-09-10) — "제약을 하나도 답하지 않았다" 오류는 preferences가
		//    아니라 constraints(별도 constraint_snapshot 표, JpaTripRepository.
		//    findLatestConstraintSnapshotId)가 비어 있을 때 난다. preferences만 채워서는
		//    안 풀렸다 — constraints를 최소 하나 답해야 한다.
		//    🔴 실측(2026-09-10) — CATEGORY 취향 답의 codes는 BaselineCandidateTranslator가
		//    place.category와 글자 그대로 비교한다(대조표 매핑 여부와 무관하게 원문 코드를
		//    그대로 쓴다). "SEA"로 줬더니 위에서 심은 place.category="CAFE"와 안 맞아 후보가
		//    0건이 되고 dataset_version을 못 구해 VERSION_UNRESOLVED로 실패했다 — 심은 장소
		//    category와 반드시 같은 문자열을 써야 한다.
		LocalDate start = LocalDate.now().plusDays(7);
		LocalDate finish = start.plusDays(1);
		CreateTripRequest.PreferenceAnswerInput categoryAnswer = new CreateTripRequest.PreferenceAnswerInput(
				"CATEGORY", "{\"codes\": [\"CAFE\"]}", "SELECTED");
		CreateTripRequest.ConstraintInput walkingConstraint = new CreateTripRequest.ConstraintInput(
				"MOBILITY", "MAX_WALKING_METERS", "SOFT", "LTE", null, 2000.0, "SELECTED", null);
		CreateTripRequest tripRequest = new CreateTripRequest(start, finish, 35.1152, 129.0423, null, 2, null, null,
				List.of(categoryAnswer), null, List.of(walkingConstraint), null, null, null, null, null);
		ResponseEntity<ApiResponse<TripDto>> tripResponse = client.post("/api/v1/trips", tripRequest,
				new ParameterizedTypeReference<ApiResponse<TripDto>>() {
				});
		assertThat(tripResponse.getStatusCode()).as("응답 본문: %s", tripResponse.getBody()).isEqualTo(HttpStatus.CREATED);
		String tripId = tripResponse.getBody().data().tripId();
		assertThat(tripId).isNotBlank();

		// 2) 추천 Job 생성 — REC-01. 202 + Job 번호만 즉시 온다.
		ResponseEntity<ApiResponse<RecommendationJobResponse>> jobCreated = client.post(
				"/api/v1/trips/" + tripId + "/recommendation-jobs", null,
				new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
				});
		assertThat(jobCreated.getStatusCode()).as("응답 본문: %s", jobCreated.getBody()).isEqualTo(HttpStatus.ACCEPTED);
		String jobId = jobCreated.getBody().data().jobId().toString();

		// 3) JOB-01 폴링 — PENDING/RUNNING을 벗어날 때까지 기다린다.
		RecommendationJobResponse finishedJob = pollJobUntilTerminal(client, jobId);
		assertThat(finishedJob.status()).as("추천 Job이 실패로 끝났다 — failure=%s", finishedJob.failure())
				.isNotEqualTo(JobStatus.FAILED);

		// 4) 추천 결과 조회 — 다음 단계(일정 편집)에 쓸 itineraryId를 여기서 받는다.
		ResponseEntity<ApiResponse<RecommendationResultResponse>> resultResponse = client.get(
				"/api/v1/recommendation-jobs/" + jobId,
				new ParameterizedTypeReference<ApiResponse<RecommendationResultResponse>>() {
				});
		assertThat(resultResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String itineraryId = resultResponse.getBody().data().itineraryId();
		assertThat(itineraryId).as("일정 계산이 끝났는데 itineraryId가 없다").isNotBlank();

		// 5) 방금 만든 일정표를 읽어 baseVersion을 얻는다 — 편집 Job은 API-09(낙관적 잠금)를
		//    요구한다.
		ResponseEntity<ApiResponse<ItineraryDetailResponse>> itineraryResponse = client.get(
				"/api/v1/itineraries/" + itineraryId,
				new ParameterizedTypeReference<ApiResponse<ItineraryDetailResponse>>() {
				});
		assertThat(itineraryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		int baseVersion = itineraryResponse.getBody().data().version();

		// 6) 일정 편집 Job 제출 — ITN-09, 0일차 전체 재계산. dayIndex만 주면 그 날의
		//    고정되지 않은 자리를 전부 다시 채운다(fromItemId는 이 시나리오에서 안 쓴다).
		ItineraryEditJobRequest editRequest = new ItineraryEditJobRequest(baseVersion, null, null, 0);
		ResponseEntity<ApiResponse<RecommendationJobResponse>> editJobCreated = client.post(
				"/api/v1/itineraries/" + itineraryId + "/recalculate", editRequest,
				new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
				});
		assertThat(editJobCreated.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		String editJobId = editJobCreated.getBody().data().jobId().toString();
		assertThat(editJobId).isNotBlank();

		// 7) 편집 Job도 실패로 끝나지 않는지 확인한다 — "제출"만으로 끝내면 접수만 되고
		//    실제로 재계산이 도는지는 아무도 모른다.
		RecommendationJobResponse finishedEditJob = pollJobUntilTerminal(client, editJobId);
		assertThat(finishedEditJob.status())
				.as("일정 재계산 Job이 실패로 끝났다 — failure=%s", finishedEditJob.failure())
				.isNotEqualTo(JobStatus.FAILED);
	}

	/**
	 * {@link FunctionalJourneyTest#pollUntil}은 5초 상한이라 실제 추천 계산에 부족할 수
	 * 있다 — 이 여정 전용으로 15초까지 늘린다(300ms x 50).
	 */
	private RecommendationJobResponse pollJobUntilTerminal(AuthedClient client, String jobId) {
		Supplier<ResponseEntity<ApiResponse<RecommendationJobResponse>>> poll = () -> client.get(
				"/api/v1/jobs/" + jobId, new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
				});
		Predicate<ApiResponse<RecommendationJobResponse>> terminal = body -> body.data().status().isTerminal();

		for (int i = 0; i < 50; i++) {
			ResponseEntity<ApiResponse<RecommendationJobResponse>> res = poll.get();
			assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
			if (terminal.test(res.getBody())) {
				return res.getBody().data();
			}
			sleepQuietly(300);
		}
		throw new AssertionError("15초 안에 Job(" + jobId + ")이 끝나지 않았다");
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
