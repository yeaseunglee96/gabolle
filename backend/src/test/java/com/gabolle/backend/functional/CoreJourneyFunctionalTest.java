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
 * 핵심 여정. 회원가입 → 로그인 → 여행 생성 → 추천 Job → 폴링 → 일정 편집 Job 제출까지, 앞 단계
 * 응답을 그대로 다음 단계 요청에 넣어 한 테스트로 끝까지 돈다.
 *
 * <p>{@code service-version}·{@code deployment-environment} 를 여기서 채운다. 검사 환경에서는 둘 다
 * 빈 문자열이고, {@code RecommendationService.resolveMissingVersions} 는 비어 있으면 후보가 몇
 * 건이든 VERSION_UNRESOLVED 로 막는다 — 장소를 아무리 넉넉히 심어도 실패한다.
 *
 * <p>공통 하네스가 아니라 이 클래스에만 붙인다. 여정마다 프로퍼티가 다르면 Spring 이 컨텍스트를 새로
 * 캐시한다.
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
	 * {@code place} 를 지우기 전에 참조하는 세 표(itinerary_item·itinerary_leg·
	 * itinerary_excluded_place)에서 먼저 행을 지운다. 이 여정은 실제로 일정을 조립해
	 * {@code itinerary_item} 이 심은 장소를 참조하므로, 바로 지우면 외래 키에 걸려 일부 행이 남는다.
	 * 남은 행은 {@link RecommendationWithRealPlacesFunctionalTest} 가 «표본이 이미 있다»고 잘못
	 * 판단하게 만든다.
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

		// 0) 후보 장소 시딩 — functionaltest 스키마엔 place 행이 없어, 장소 없이 추천을
		//    돌리면 후보가 0건이라 dataset_version 을 못 구하고 VERSION_UNRESOLVED 로
		//    실패한다.
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		double originLat = 35.1152;
		double originLng = 129.0423;
		for (int i = 0; i < 12; i++) {
			UUID placeId = this.placeFixture.insertPlace("여정테스트장소" + i, "JourneyPlace" + i, "CAFE_HEALING",
					originLat + (i * 0.001), originLng + (i * 0.001));
			this.placeFixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
			this.seededPlaceIds.add(placeId);
		}

		// 1) 여행 생성.
		//    originLat/originLng 는 DTO 에 @NotNull 이 없지만 실제로는 필수다.
		//    "제약을 하나도 답하지 않았다" 오류는 preferences 가 아니라 constraints 가
		//    비어 있을 때 나므로 constraints 를 최소 하나 답한다.
		//    CATEGORY 답의 codes 는 place.category 와 글자 그대로 비교되므로 위에서 심는
		//    장소의 category 와 같은 문자열이어야 한다 — 다르면 후보 0건이 되어
		//    VERSION_UNRESOLVED 로 실패한다. 둘은 짝이라 한쪽만 바꾸면 안 된다.
		LocalDate start = LocalDate.now().plusDays(7);
		LocalDate finish = start.plusDays(1);
		CreateTripRequest.PreferenceAnswerInput categoryAnswer = new CreateTripRequest.PreferenceAnswerInput(
				"CATEGORY", "{\"codes\": [\"CAFE_HEALING\"]}", "SELECTED");
		CreateTripRequest.ConstraintInput walkingConstraint = new CreateTripRequest.ConstraintInput(
				"MOBILITY", "MAX_WALKING_METERS", "SOFT", "LTE", null, 2000.0, "SELECTED", null);
		CreateTripRequest tripRequest = new CreateTripRequest(start, finish, 35.1152, 129.0423, null, 2, null, null,
				List.of(categoryAnswer), null, List.of(walkingConstraint), null, null, null, null, null,
				// 1박이라 숙소가 있어야 한다(S15P21E201-1585). 출발지(부산역)에서 가까운 동네로.
				null, null, null, "NAMPO");
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
