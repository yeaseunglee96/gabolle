package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;
import com.gabolle.backend.share.presentation.dto.CloneTripResponse;
import com.gabolle.backend.share.presentation.dto.ShareLinkResponse;
import com.gabolle.backend.share.presentation.dto.SharedItineraryResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 공유 링크 여정 — S15P21E201-783. 생성 → 비로그인 공개 조회 → 복제까지 실제 HTTP로 잇는다.
 *
 * <p>배경 — S15P21E201-330(공유 조회는 인증 없이 열림)이 만든 경로들이다. 공유 링크 발급은
 * 인증이 필요하고 공개 조회는 인증이 없어야 한다는 비대칭 규칙이 {@code SecurityConfig} 목록
 * 하나에만 있었다 — 실제 HTTP로 그 비대칭을 확인하는 테스트가 이 클래스 이전엔 없었다.
 *
 * <p>🔴 실측(2026-09-10) — 복제는 원본에 실제 일정(장소가 든 itinerary)이 있어야 한다.
 * 없으면 {@code SHARED_ITINERARY_EMPTY}(422)로 막는다. 그래서 {@link
 * CoreJourneyFunctionalTest}(S15P21E201-780)와 같은 순서로 장소를 심고 추천 Job을 완료까지
 * 돌린 뒤에야 공유 링크를 발급한다 — 그 클래스에서 실측해 둔 세 가지(원점 좌표 필수·
 * service-version/deployment-environment 공백 문제·CATEGORY 코드가 place.category와
 * 글자 그대로 비교되는 것)를 그대로 물려받는다.
 */
@TestPropertySource(properties = { "gabolle.recommendation.service-version=test-local",
		"gabolle.recommendation.deployment-environment=test" })
class ShareLinkJourneyFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PlaceFixture placeFixture;

	private final List<UUID> seededPlaceIds = new ArrayList<>();

	/**
	 * {@link CoreJourneyFunctionalTest#cleanUpPlaces}와 같은 이유 — 그쪽 javadoc 참고.
	 *
	 * <p>🔴 실측(2026-09-10) — 이 여정은 그쪽과 달리 <b>복제</b>까지 한다. 복제가
	 * {@code trip_seed_place}(원본에서 가져온 장소, {@code CloneTripResponse.seedPlaceCount}가
	 * 이 표에서 나온다)에도 참조를 남기고, 그 표도 {@code place}에 cascade 없는 외래키를 걸어
	 * 둬서 하나 더 먼저 지워야 한다.
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
			this.jdbcTemplate.batchUpdate("DELETE FROM trip_seed_place WHERE place_id = ?", this.seededPlaceIds,
					this.seededPlaceIds.size(), (ps, placeId) -> ps.setObject(1, placeId));
		}
		if (this.placeFixture != null) {
			this.placeFixture.cleanUp();
		}
	}

	@Test
	@DisplayName("공유 링크 생성 → 비로그인 공개 조회 성공 → 복제는 로그인해야만 → 다른 계정으로 복제까지 이어진다")
	void shareLinkJourneyEndToEnd() {
		AuthedClient owner = loginAsNewUser("share-owner");

		// 0) 후보 장소 시딩 — CoreJourneyFunctionalTest(S15P21E201-780)와 같은 이유.
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		double originLat = 35.1152;
		double originLng = 129.0423;
		for (int i = 0; i < 12; i++) {
			UUID placeId = this.placeFixture.insertPlace("공유테스트장소" + i, "ShareTestPlace" + i, "CAFE_HEALING",
					originLat + (i * 0.001), originLng + (i * 0.001));
			this.placeFixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
			this.seededPlaceIds.add(placeId);
		}

		// 1) 원본 여행 생성.
		LocalDate start = LocalDate.now().plusDays(10);
		LocalDate finish = start.plusDays(1);
		CreateTripRequest.PreferenceAnswerInput categoryAnswer = new CreateTripRequest.PreferenceAnswerInput(
				"CATEGORY", "{\"codes\": [\"CAFE_HEALING\"]}", "SELECTED");
		CreateTripRequest.ConstraintInput walkingConstraint = new CreateTripRequest.ConstraintInput(
				"MOBILITY", "MAX_WALKING_METERS", "SOFT", "LTE", null, 2000.0, "SELECTED", null);
		CreateTripRequest tripRequest = new CreateTripRequest(start, finish, originLat, originLng, null, 2, null,
				null, List.of(categoryAnswer), null, List.of(walkingConstraint), null, null, null, null, null);
		ResponseEntity<ApiResponse<TripDto>> tripResponse = owner.post("/api/v1/trips", tripRequest,
				new ParameterizedTypeReference<ApiResponse<TripDto>>() {
				});
		assertThat(tripResponse.getStatusCode()).as("응답 본문: %s", tripResponse.getBody()).isEqualTo(HttpStatus.CREATED);
		String sourceTripId = tripResponse.getBody().data().tripId();

		// 2) 추천 Job을 완료까지 돌린다 — 원본에 실제 일정이 있어야 복제할 수 있다
		//    (SHARED_ITINERARY_EMPTY, 실측).
		ResponseEntity<ApiResponse<RecommendationJobResponse>> jobCreated = owner.post(
				"/api/v1/trips/" + sourceTripId + "/recommendation-jobs", null,
				new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
				});
		assertThat(jobCreated.getStatusCode()).as("응답 본문: %s", jobCreated.getBody()).isEqualTo(HttpStatus.ACCEPTED);
		String jobId = jobCreated.getBody().data().jobId().toString();
		RecommendationJobResponse finishedJob = pollJobUntilTerminal(owner, jobId);
		assertThat(finishedJob.status()).as("추천 Job이 실패로 끝났다 — failure=%s", finishedJob.failure())
				.isNotEqualTo(JobStatus.FAILED);

		// 3) 공유 링크 발급 — 로그인(OWNER)해야 한다.
		ResponseEntity<ApiResponse<ShareLinkResponse>> linkResponse = owner.post(
				"/api/v1/trips/" + sourceTripId + "/share-links", null,
				new ParameterizedTypeReference<ApiResponse<ShareLinkResponse>>() {
				});
		assertThat(linkResponse.getStatusCode()).as("응답 본문: %s", linkResponse.getBody()).isEqualTo(HttpStatus.CREATED);
		String token = linkResponse.getBody().data().token();
		assertThat(token).isNotBlank();

		// 4) 비로그인 공개 조회 — 🔴 여기서 401이 나면 -330이 다시 깨진 것이다. Authorization
		//    헤더를 아예 안 실은 요청을 만들려고 AuthedClient가 아니라 공유 빈(rest)을 직접 쓴다.
		ResponseEntity<ApiResponse<SharedItineraryResponse>> shareResponse = this.rest.exchange(
				"/api/v1/shares/" + token, HttpMethod.GET, HttpEntity.EMPTY,
				new ParameterizedTypeReference<ApiResponse<SharedItineraryResponse>>() {
				});
		assertThat(shareResponse.getStatusCode())
				.as("비로그인 공유 조회가 실패했다 — S15P21E201-330 이 깨졌을 수 있다. 응답 본문: %s", shareResponse.getBody())
				.isEqualTo(HttpStatus.OK);
		assertThat(shareResponse.getBody().data().startDate()).isEqualTo(start.toString());
		assertThat(shareResponse.getBody().data().days()).as("공유 조회에 일정 항목이 없다").isNotEmpty();

		// 5) 복제는 비로그인으로는 안 된다 — 401.
		CreateTripRequest cloneRequest = new CreateTripRequest(start, finish, originLat, originLng, null, 2, null,
				null, List.of(categoryAnswer), null, List.of(walkingConstraint), null, null, null, null, null);
		ResponseEntity<String> unauthedClone = this.rest.exchange(
				"/api/v1/shares/" + token + "/clone", HttpMethod.POST, new HttpEntity<>(cloneRequest), String.class);
		assertThat(unauthedClone.getStatusCode())
				.as("로그인 없이 복제가 통과했다 — 복제는 인증이 필요해야 한다. 응답 본문: %s", unauthedClone.getBody())
				.isEqualTo(HttpStatus.UNAUTHORIZED);

		// 6) 다른 계정으로 로그인해 복제 — 성공(200/201/202 중 하나)하고, 새 여행은 원본과
		//    다른 tripId를 받는다.
		AuthedClient cloner = loginAsNewUser("share-cloner");
		ResponseEntity<ApiResponse<CloneTripResponse>> cloneResponse = cloner.post(
				"/api/v1/shares/" + token + "/clone", cloneRequest,
				new ParameterizedTypeReference<ApiResponse<CloneTripResponse>>() {
				});
		assertThat(cloneResponse.getStatusCode())
				.as("복제가 실패했다. 응답 본문: %s", cloneResponse.getBody())
				.isIn(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.ACCEPTED);
		CloneTripResponse cloneBody = cloneResponse.getBody().data();
		assertThat(cloneBody.tripId()).as("복제된 새 여행 id가 없다").isNotBlank();
		assertThat(cloneBody.tripId()).as("복제가 원본과 같은 여행을 가리킨다").isNotEqualTo(sourceTripId);
		assertThat(cloneBody.sourceTripId()).isEqualTo(sourceTripId);
		assertThat(cloneBody.seedPlaceCount()).as("원본에서 가져온 장소가 0곳이다").isGreaterThan(0);

		// 7) 🔴 실측(2026-09-10) — 복제 Job은 비동기라, 여기서 끝까지 기다리지 않으면 테스트가
		//    먼저 끝나고 @AfterEach의 place 정리가 먼저 돌 수 있다. 그러면 정리가 지운 뒤에
		//    Job이 새 itinerary_item 행을 써서 외래키가 걸린다(경합, 처음 이 테스트를 돌렸을 때
		//    실제로 이렇게 죽었다). 끝까지 기다리는 것은 경합을 없앨 뿐 아니라 복제 Job 자체가
		//    성공하는지도 확인해 준다.
		if (cloneBody.jobId() != null) {
			RecommendationJobResponse finishedCloneJob = pollJobUntilTerminal(cloner, cloneBody.jobId());
			assertThat(finishedCloneJob.status()).as("복제 Job이 실패로 끝났다 — failure=%s", finishedCloneJob.failure())
					.isNotEqualTo(JobStatus.FAILED);
		}
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
