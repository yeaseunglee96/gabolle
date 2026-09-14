package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.TripDto;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * 프론트-백엔드 API 계약 정합성 — S15P21E201-776.
 *
 * <h2>어느 쪽이 먼저 바뀌면 어느 테스트가 빨개지는가</h2>
 *
 * <p>이 파일은 {@code GET /api/v1/jobs/{jobId}}와 {@code GET /api/v1/itineraries/{id}}가
 * 실제로 내보내는 <b>날것의 JSON 칸 이름</b>을 본다({@code ResponseBodyContractFunctionalTest},
 * S15P21E201-789와 같은 이유 — DTO로 되읽으면 Jackson이 모양을 맞춰 줘서 이름이 바뀐 것이
 * 가려진다). 프론트 쪽 짝은 {@code frontend/src/plan/apiContracts.test.ts}의
 * {@code isRecommendationJobPollDto}·{@code isItineraryDto}이고, 그 파일의 fixture는
 * 바로 이 테스트가 실제로 받는 응답 모양을 옮겨 적은 것이다.
 *
 * <ul>
 * <li><b>백엔드가 먼저 필드 이름을 바꾸면</b> — 이 파일의 {@code assertThat(...).containsKeys(...)}가
 * 그 즉시 빨개진다. 프론트 쪽은 fixture가 그대로라 초록으로 남아 있다가, 실제 서버를 호출하는
 * 순간(수동 확인이든 앱 실행이든)에야 깨진다 — 그래서 백엔드가 필드를 바꿀 때는 이 파일을
 * 먼저 고치고, 그 다음 프론트 fixture와 타입을 맞춰 고쳐야 한다</li>
 * <li><b>프론트가 먼저 타입만 바꾸면</b>(백엔드는 그대로) — 이 파일은 그대로 초록이다(백엔드
 * 자신은 안 바뀌었으니까). 프론트의 {@code apiContracts.test.ts}가 fixture와 새 타입 가드가
 * 어긋나 빨개진다 — 그 실패가 "백엔드를 실제로 맞게 바꿨는지" 확인하라는 신호다</li>
 * </ul>
 *
 * <p>즉 둘 중 하나가 실제로 응답 모양을 바꾸면 <b>그 쪽의 계약 테스트가 먼저</b> 빨개지고,
 * 반대쪽은 fixture를 갱신하기 전까지 조용하다 — 그래서 필드를 바꾸는 사람은 반드시 반대쪽
 * 테스트 파일도 함께 고쳐야 한다는 것을 커밋 리뷰에서 확인할 수 있다.
 */
@TestPropertySource(properties = { "gabolle.recommendation.service-version=test-local",
		"gabolle.recommendation.deployment-environment=test" })
class ApiContractCrossCheckFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PlaceFixture placeFixture;

	private final List<UUID> seededPlaceIds = new ArrayList<>();

	/** {@link CoreJourneyFunctionalTest#cleanUpPlaces}와 같은 이유 — 그쪽 javadoc 참고. */
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
	@DisplayName("GET /api/v1/jobs/{jobId} 칸 이름이 프론트 RecommendationJobPollDto와 맞는다")
	void recommendationJobPollKeepsItsFieldNames() {
		AuthedClient client = loginAsNewUser("contract-job");
		String jobId = completedRecommendationJobId(client);

		ResponseEntity<String> response = client.get("/api/v1/jobs/" + jobId, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> data = JsonPath.read(response.getBody(), "$.data");
		// 🔴 frontend/src/plan/recommendationJob.ts의 RecommendationJobPollDto와 이름을
		//    맞춘다 — 하나라도 어긋나면 apiContracts.ts의 isRecommendationJobPollDto가
		//    실제 서버 앞에서는(fixture 갱신 전까지는 조용히) 거짓을 돌려준다.
		assertThat(data).containsKeys("jobId", "status", "progress", "failure", "retryable", "pollAfterSeconds");
		assertThat(data.get("jobId")).isInstanceOf(String.class);
		assertThat(data.get("status")).isInstanceOf(String.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> progress = (Map<String, Object>) data.get("progress");
		assertThat(progress).containsKeys("stage", "percent");
		assertThat(progress.get("stage")).isInstanceOf(String.class);
		assertThat(progress.get("percent")).isInstanceOf(Integer.class);
		assertThat(data.get("retryable")).isInstanceOf(Boolean.class);
	}

	@Test
	@DisplayName("GET /api/v1/itineraries/{id} 칸 이름이 프론트 ItineraryDto와 맞는다")
	void itineraryDetailKeepsItsFieldNames() {
		AuthedClient client = loginAsNewUser("contract-itinerary");
		String jobId = completedRecommendationJobId(client);

		ResponseEntity<String> jobResult = client.get("/api/v1/recommendation-jobs/" + jobId, String.class);
		String itineraryId = JsonPath.read(jobResult.getBody(), "$.data.itineraryId");
		assertThat(itineraryId).as("응답 본문: %s", jobResult.getBody()).isNotBlank();

		ResponseEntity<String> response = client.get("/api/v1/itineraries/" + itineraryId, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> data = JsonPath.read(response.getBody(), "$.data");
		// 🔴 frontend/src/plan/itinerary.ts의 ItineraryDto와 이름을 맞춘다. warningCodes는
		//    프론트 타입에는 없다 — 프론트가 아직 안 쓰는 칸이 하나 더 있는 것은 계약 위반이
		//    아니다(isItineraryDto는 초과 칸을 허용한다). 반대로 여기 없는 칸을 프론트가
		//    필수로 요구하면 그것이 계약 위반이다.
		assertThat(data).containsKeys("id", "title", "version", "days");
		assertThat(data.get("id")).isInstanceOf(String.class);
		assertThat(data.get("title")).isInstanceOf(String.class);
		assertThat(data.get("version")).isInstanceOf(Integer.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> days = (List<Map<String, Object>>) data.get("days");
		assertThat(days).as("일정이 하루도 없다").isNotEmpty();
		Map<String, Object> firstDay = days.get(0);
		assertThat(firstDay).containsKeys("date", "items");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) firstDay.get("items");
		assertThat(items).as("첫날 일정 항목이 하나도 없다 — 후보 시딩을 확인해야 한다").isNotEmpty();
		Map<String, Object> firstItem = items.get(0);
		// 🔴 placeId는 S15P21E201-744(다녀오셨나요 평가)가 그대로 쓰는 칸이다 — 빠지면
		//    그 기능이 조용히 죽는다.
		assertThat(firstItem).containsKeys("id", "startsAt", "title", "locked", "placeId");
		assertThat(firstItem.get("locked")).isInstanceOf(Boolean.class);
	}

	/** {@link CoreJourneyFunctionalTest}(S15P21E201-780)와 같은 시딩·요청 모양 — 그쪽 실측 주석 참고. */
	private String completedRecommendationJobId(AuthedClient client) {
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		double originLat = 35.1152;
		double originLng = 129.0423;
		for (int i = 0; i < 12; i++) {
			UUID placeId = this.placeFixture.insertPlace("계약테스트장소" + i, "ContractTestPlace" + i, "CAFE_HEALING",
					originLat + (i * 0.001), originLng + (i * 0.001));
			this.placeFixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
			this.seededPlaceIds.add(placeId);
		}

		LocalDate start = LocalDate.now().plusDays(10);
		LocalDate finish = start.plusDays(1);
		CreateTripRequest.PreferenceAnswerInput categoryAnswer = new CreateTripRequest.PreferenceAnswerInput(
				"CATEGORY", "{\"codes\": [\"CAFE_HEALING\"]}", "SELECTED");
		CreateTripRequest.ConstraintInput walkingConstraint = new CreateTripRequest.ConstraintInput(
				"MOBILITY", "MAX_WALKING_METERS", "SOFT", "LTE", null, 2000.0, "SELECTED", null);
		CreateTripRequest tripRequest = new CreateTripRequest(start, finish, originLat, originLng, null, 2, null,
				null, List.of(categoryAnswer), null, List.of(walkingConstraint), null, null, null, null, null);
		ResponseEntity<ApiResponse<TripDto>> tripResponse = client.post("/api/v1/trips", tripRequest,
				new ParameterizedTypeReference<ApiResponse<TripDto>>() {
				});
		assertThat(tripResponse.getStatusCode()).as("응답 본문: %s", tripResponse.getBody()).isEqualTo(HttpStatus.CREATED);
		String tripId = tripResponse.getBody().data().tripId();

		ResponseEntity<ApiResponse<RecommendationJobResponse>> jobCreated = client.post(
				"/api/v1/trips/" + tripId + "/recommendation-jobs", null,
				new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
				});
		assertThat(jobCreated.getStatusCode()).as("응답 본문: %s", jobCreated.getBody()).isEqualTo(HttpStatus.ACCEPTED);
		String jobId = jobCreated.getBody().data().jobId().toString();

		RecommendationJobResponse finishedJob = pollJobUntilTerminal(client, jobId);
		assertThat(finishedJob.status()).as("추천 Job이 실패로 끝났다 — failure=%s", finishedJob.failure())
				.isNotEqualTo(JobStatus.FAILED);
		return jobId;
	}

	/** {@link CoreJourneyFunctionalTest#pollJobUntilTerminal}과 같은 이유 — 15초까지 늘린다. */
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
