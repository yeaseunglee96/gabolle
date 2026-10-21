package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.api.AnonymousClaimResponse;
import com.gabolle.backend.auth.api.AnonymousSessionResponse;
import com.gabolle.backend.auth.service.AnonymousSessionCleanupService;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationResultResponse;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.TripDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 비회원 여정. 출입증 하나로 여행을 만들고 일정을 받고, 로그인하면 그 여행이 계정으로 넘어온다.
 *
 * <p>실제 소켓으로 돈다 — 신원을 정하는 것이 필터 둘(JWT·익명 세션)의 순서라서, MockMvc 로 주체를
 * 손으로 꽂으면 이 테스트가 재려는 것을 건너뛴다.
 *
 * <p>프로퍼티는 {@link CoreJourneyFunctionalTest} 와 같게 둔다. 다르면 컨텍스트를 하나 더 띄운다.
 */
@TestPropertySource(properties = { "gabolle.recommendation.service-version=test-local",
		"gabolle.recommendation.deployment-environment=test" })
class AnonymousTripJourneyFunctionalTest extends FunctionalJourneyTest {

	private static final String SESSION_HEADER = "X-Session-Token";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AnonymousSessionCleanupService cleanupService;

	private PlaceFixture placeFixture;

	private final List<UUID> seededPlaceIds = new ArrayList<>();

	@AfterEach
	void cleanUpPlaces() {
		// CoreJourneyFunctionalTest 와 같은 이유 — 일정이 심은 장소를 참조하므로 그 행을 먼저 끊는다.
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
	@DisplayName("비회원이 만든 여행으로 일정이 나오고, 로그인하면 그 여행·일정·작업이 계정으로 넘어온다")
	void guestTripIsUsableAndHandedOverOnLogin() {
		seedPlaces();
		String token = issueSessionToken();

		String tripId = createTrip(token);

		ResponseEntity<ApiResponse<RecommendationJobResponse>> jobCreated = rest.exchange(
				"/api/v1/trips/" + tripId + "/recommendation-jobs", HttpMethod.POST, guest(token, null),
				new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
				});
		assertThat(jobCreated.getStatusCode()).as("응답 본문: %s", jobCreated.getBody()).isEqualTo(HttpStatus.ACCEPTED);
		String jobId = jobCreated.getBody().data().jobId().toString();

		RecommendationJobResponse finished = pollJob(token, jobId);
		// 외래키가 남아 있으면 여기서 FAILED 다 — 일정 판의 created_by 에 세션 ID 를 적다 롤백된다.
		assertThat(finished.status()).as("비회원 일정 생성 실패 — failure=%s", finished.failure())
				.isNotEqualTo(JobStatus.FAILED);

		ResponseEntity<ApiResponse<RecommendationResultResponse>> result = rest.exchange(
				"/api/v1/recommendation-jobs/" + jobId, HttpMethod.GET, guest(token, null),
				new ParameterizedTypeReference<ApiResponse<RecommendationResultResponse>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		String itineraryId = result.getBody().data().itineraryId();
		assertThat(itineraryId).isNotBlank();

		assertThat(rest.exchange("/api/v1/itineraries/" + itineraryId, HttpMethod.GET, guest(token, null),
				String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(listTripIds(guest(token, null))).contains(tripId);

		// 남의 출입증으로는 없는 여행과 같다.
		String stranger = issueSessionToken();
		assertThat(rest.exchange("/api/v1/trips/" + tripId, HttpMethod.GET, guest(stranger, null), String.class)
				.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		// 로그인 토큰이 깨졌으면 출입증이 있어도 익명으로 받아 주지 않는다 — 401 이어야 화면이 토큰을 새로 받는다.
		HttpHeaders broken = new HttpHeaders();
		broken.set(SESSION_HEADER, token);
		broken.setBearerAuth("expired-or-forged");
		assertThat(rest.exchange("/api/v1/trips", HttpMethod.GET, new HttpEntity<>(broken), String.class)
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// 회원 전용은 그대로 막힌다.
		assertThat(rest.exchange("/api/v1/trips/" + tripId + "/share-links", HttpMethod.POST, guest(token, Map.of()),
				String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		AuthedClient member = loginAsNewUser("guest-handover");
		HttpHeaders withSession = new HttpHeaders();
		withSession.set(SESSION_HEADER, token);
		ResponseEntity<ApiResponse<AnonymousClaimResponse>> claim = member.post("/api/v1/auth/anonymous/claim", null,
				withSession, new ParameterizedTypeReference<ApiResponse<AnonymousClaimResponse>>() {
				});
		assertThat(claim.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(claim.getBody().data().claimedTrips()).isEqualTo(1);

		assertThat(member.get("/api/v1/trips/" + tripId, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
		// 작업의 주인도 옮겨져야 일정 생성 화면이 로그인 직후에도 열린다.
		assertThat(member.get("/api/v1/jobs/" + jobId, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(member.get("/api/v1/itineraries/" + itineraryId, String.class).getStatusCode())
				.isEqualTo(HttpStatus.OK);
		Integer leftBehind = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM itinerary_versions v JOIN itineraries i ON i.itinerary_id = v.itinerary_id
				WHERE i.trip_id = ?::uuid AND v.created_by NOT IN (SELECT user_id FROM app_user)
				""", Integer.class, tripId);
		assertThat(leftBehind).as("세션 ID 를 가리킨 채 남은 일정 판").isZero();

		// 넘긴 출입증은 못 쓴다 — 같은 기기의 다음 사람이 이 세션을 물려받지 않게.
		assertThat(rest.exchange("/api/v1/trips", HttpMethod.GET, guest(token, null), String.class)
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		// 두 번 넘겨도 오류가 아니다.
		assertThat(member.post("/api/v1/auth/anonymous/claim", null, withSession,
				new ParameterizedTypeReference<ApiResponse<AnonymousClaimResponse>>() {
				}).getBody().data().claimedTrips()).isZero();
	}

	@Test
	@DisplayName("비회원은 살아 있는 여행을 다섯 개까지 만든다 — 여섯 번째는 429")
	void guestTripCountIsCapped() {
		String token = issueSessionToken();
		for (int i = 0; i < 5; i++) {
			createTrip(token);
		}
		ResponseEntity<String> sixth = rest.exchange("/api/v1/trips", HttpMethod.POST, guest(token, tripRequest()),
				String.class);
		assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(sixth.getBody()).contains("ANONYMOUS_LIMIT_REACHED");

		// 회원은 이 상한과 무관하다.
		AuthedClient member = loginAsNewUser("guest-cap-member");
		for (int i = 0; i < 6; i++) {
			assertThat(member.post("/api/v1/trips", tripRequest(), String.class).getStatusCode())
					.isEqualTo(HttpStatus.CREATED);
		}
	}

	@Test
	@DisplayName("정리 배치는 오래 안 쓴 세션과 그 여행을 지우되, 아직 안 끝난 여행이 있는 세션은 남긴다")
	void cleanupRemovesIdleSessionsButKeepsUpcomingTrips() {
		String idleToken = issueSessionToken();
		String idleTrip = createTrip(idleToken);
		String waitingToken = issueSessionToken();
		String waitingTrip = createTrip(waitingToken);

		// 둘 다 40일 전에 마지막으로 썼다. 첫째의 여행은 이미 끝났고, 둘째의 여행은 아직 오지 않았다.
		jdbcTemplate.update("UPDATE trip SET start_date = CURRENT_DATE - 20, end_date = CURRENT_DATE - 19 WHERE trip_id = ?::uuid",
				idleTrip);
		jdbcTemplate.update("""
				UPDATE anonymous_session SET last_seen_at = now() - interval '40 days'
				WHERE session_id IN (SELECT owner_user_id FROM trip WHERE trip_id IN (?::uuid, ?::uuid))
				""", idleTrip, waitingTrip);

		cleanupService.cleanup(30, 7, 180, 500);

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trip WHERE trip_id = ?::uuid", Integer.class,
				idleTrip)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trip WHERE trip_id = ?::uuid", Integer.class,
				waitingTrip)).isEqualTo(1);
		assertThat(rest.exchange("/api/v1/trips", HttpMethod.GET, guest(idleToken, null), String.class)
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(listTripIds(guest(waitingToken, null))).contains(waitingTrip);
	}

	private void seedPlaces() {
		this.placeFixture = new PlaceFixture(this.jdbcTemplate);
		for (int i = 0; i < 12; i++) {
			UUID placeId = this.placeFixture.insertPlace("비회원여정장소" + i, "GuestJourneyPlace" + i, "CAFE_HEALING",
					35.1152 + (i * 0.001), 129.0423 + (i * 0.001));
			this.placeFixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
			this.seededPlaceIds.add(placeId);
		}
	}

	private String issueSessionToken() {
		ResponseEntity<ApiResponse<AnonymousSessionResponse>> issued = rest.exchange("/api/v1/auth/anonymous",
				HttpMethod.POST, null, new ParameterizedTypeReference<ApiResponse<AnonymousSessionResponse>>() {
				});
		assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return issued.getBody().data().sessionToken();
	}

	private String createTrip(String token) {
		ResponseEntity<ApiResponse<TripDto>> created = rest.exchange("/api/v1/trips", HttpMethod.POST,
				guest(token, tripRequest()), new ParameterizedTypeReference<ApiResponse<TripDto>>() {
				});
		assertThat(created.getStatusCode()).as("응답 본문: %s", created.getBody()).isEqualTo(HttpStatus.CREATED);
		return created.getBody().data().tripId();
	}

	/** {@link CoreJourneyFunctionalTest} 와 같은 최소 조건 — 그쪽 주석에 이유가 있다. */
	private static CreateTripRequest tripRequest() {
		LocalDate start = LocalDate.now().plusDays(7);
		CreateTripRequest.PreferenceAnswerInput categoryAnswer = new CreateTripRequest.PreferenceAnswerInput(
				"CATEGORY", "{\"codes\": [\"CAFE_HEALING\"]}", "SELECTED");
		CreateTripRequest.ConstraintInput walkingConstraint = new CreateTripRequest.ConstraintInput(
				"MOBILITY", "MAX_WALKING_METERS", "SOFT", "LTE", null, 2000.0, "SELECTED", null);
		return new CreateTripRequest(start, start.plusDays(1), 35.1152, 129.0423, null, 2, null, null,
				List.of(categoryAnswer), null, List.of(walkingConstraint), null, null, null, null, null,
				null, null, null, "NAMPO");
	}

	private List<String> listTripIds(HttpEntity<?> request) {
		ResponseEntity<ApiResponse<List<Map<String, Object>>>> trips = rest.exchange("/api/v1/trips", HttpMethod.GET,
				request, new ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>>() {
				});
		assertThat(trips.getStatusCode()).isEqualTo(HttpStatus.OK);
		return trips.getBody().data().stream().map(trip -> String.valueOf(trip.get("tripId"))).toList();
	}

	private RecommendationJobResponse pollJob(String token, String jobId) {
		for (int i = 0; i < 50; i++) {
			ResponseEntity<ApiResponse<RecommendationJobResponse>> res = rest.exchange("/api/v1/jobs/" + jobId,
					HttpMethod.GET, guest(token, null),
					new ParameterizedTypeReference<ApiResponse<RecommendationJobResponse>>() {
					});
			assertThat(res.getStatusCode()).as("비회원이 자기 작업을 못 본다").isEqualTo(HttpStatus.OK);
			if (res.getBody().data().status().isTerminal()) {
				return res.getBody().data();
			}
			try {
				Thread.sleep(300);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		throw new AssertionError("15초 안에 Job(" + jobId + ")이 끝나지 않았다");
	}

	private static <B> HttpEntity<B> guest(String token, B body) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(SESSION_HEADER, token);
		return new HttpEntity<>(body, headers);
	}
}
