package com.gabolle.backend.recommendation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.application.JobProgressBroker;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.application.RecommendationResultQueryService;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.RecommendationJobController;
import com.gabolle.backend.recommendation.presentation.RecommendationJobExceptionHandler;
import com.gabolle.backend.recommendation.presentation.RecommendationResultController;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationResultResponse;
import com.gabolle.backend.trip.application.TripQueryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소유권 검증. 남의 {@code jobId} 를 알아도 진행 상황도 결과도 못 본다. 진행 상태와 결과
 * 경로를 둘 다 본다 — 결과만 잠그면 진행 상태가 옆문으로 남는다.
 *
 * DB 없이 도는 슬라이스 테스트다 — {@link RecommendationJobRunner}·
 * {@link RecommendationResultQueryService} 를 mock 으로 세운다.
 */
class RecommendationResultAuthorizationTest {

	private RecommendationJobRunner runner;
	private RecommendationResultQueryService resultQueryService;
	private MockMvc mockMvc;

	private final UUID ownerId = UUID.randomUUID();
	private final UUID jobId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.runner = mock(RecommendationJobRunner.class);
		this.resultQueryService = mock(RecommendationResultQueryService.class);

		this.mockMvc = MockMvcBuilders
				.standaloneSetup(
						// 진행률 통로. 이 검사는 쓰지 않지만 컨트롤러가 요구하고, 상태를
						// 갖지 않아 mock 으로 대신할 이유가 없다.
						new RecommendationJobController(this.runner, new JobProgressBroker()),
						new RecommendationResultController(this.runner, this.resultQueryService))
				.setControllerAdvice(new RecommendationJobExceptionHandler())
				.build();
	}

	/**
	 * 신원을 요청 헤더가 아니라 인증 주체로 준다 — 헤더로 신원을 주장할 수 있으면 이 검사
	 * 자체가 무의미하다.
	 */
	private static Authentication principal(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private RecommendationJob jobOwnedBy(UUID userId) {
		return RecommendationJob.start(this.jobId, UUID.randomUUID(), userId, JobType.ITINERARY_GENERATION,
				OffsetDateTime.now());
	}

	@Test
	@DisplayName("🔴 진행 상황(GET /api/v1/jobs/{id}) — 남의 jobId 는 404")
	void progressEndpointHidesOthersJob() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(jobOwnedBy(this.ownerId)));

		mockMvc.perform(get("/api/v1/jobs/{jobId}", this.jobId)
						.principal(principal(UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
	}

	@Test
	@DisplayName("진행 상황 — 주인이 조회하면 200")
	void progressEndpointAllowsOwner() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(jobOwnedBy(this.ownerId)));

		mockMvc.perform(get("/api/v1/jobs/{jobId}", this.jobId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.jobId").value(this.jobId.toString()));
	}

	@Test
	@DisplayName("🔴 결과(GET /api/v1/recommendation-jobs/{id}) — 남의 jobId 는 404")
	void resultEndpointHidesOthersJob() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(jobOwnedBy(this.ownerId)));

		mockMvc.perform(get("/api/v1/recommendation-jobs/{jobId}", this.jobId)
						.principal(principal(UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));

		// 소유권 검사가 서비스 호출보다 먼저다 — 남의 결과를 조립조차 하지 않는다.
		verify(this.resultQueryService, never()).buildResult(any());
	}

	@Test
	@DisplayName("결과 — 주인이 조회하면 200")
	void resultEndpointAllowsOwner() throws Exception {
		RecommendationJob job = jobOwnedBy(this.ownerId);
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job));
		when(this.resultQueryService.buildResult(job)).thenReturn(new RecommendationResultResponse(
				"COMPLETED", List.of(), null, null, List.of(), null, 0, null, UUID.randomUUID().toString(),
				UUID.randomUUID().toString()));

		mockMvc.perform(get("/api/v1/recommendation-jobs/{jobId}", this.jobId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"));
	}

	// ── 여행 번호로 되찾기 ──────────────────────────────────

	@Test
	@DisplayName("🔴 여행별 목록 — 남의 여행은 404. 요청(POST)과 같은 관문을 지난다")
	void tripJobListHidesOthersTrip() throws Exception {
		String tripId = UUID.randomUUID().toString();
		UUID stranger = UUID.randomUUID();
		when(this.runner.findJobsByTrip(tripId, stranger.toString()))
				.thenThrow(new TripQueryService.TripNotFoundException(tripId));

		mockMvc.perform(get("/api/v1/trips/{tripId}/recommendation-jobs", tripId)
						.principal(principal(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	/**
	 * 아직 추천을 안 만들었다를 404 로 답하면 화면이 그것을 없는 여행과 구분할 수 없고,
	 * 사용자는 추천을 만들 수 있는 여행에서도 오류 화면을 본다.
	 */
	@Test
	@DisplayName("🔴 추천을 만든 적 없는 내 여행은 빈 목록이다 — 404 가 아니다")
	void tripWithoutJobsIsEmptyListNotNotFound() throws Exception {
		String tripId = UUID.randomUUID().toString();
		when(this.runner.findJobsByTrip(tripId, this.ownerId.toString())).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/trips/{tripId}/recommendation-jobs", tripId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data").isEmpty());
	}

	/**
	 * 순서를 정하는 것은 조회이고 컨트롤러는 그대로 내보낸다. 컨트롤러가 순서를 뒤집거나
	 * 다시 정렬하지 않는지를 본다 — 화면은 맨 앞을 가장 최근으로 읽는다.
	 */
	@Test
	@DisplayName("여행별 목록 — 주인이 조회하면 받은 순서(최신순) 그대로 나간다")
	void tripJobListKeepsNewestFirstOrder() throws Exception {
		String tripId = UUID.randomUUID().toString();
		UUID newest = UUID.randomUUID();
		UUID older = UUID.randomUUID();
		when(this.runner.findJobsByTrip(tripId, this.ownerId.toString()))
				.thenReturn(List.of(jobWithId(newest, this.ownerId), jobWithId(older, this.ownerId)));

		mockMvc.perform(get("/api/v1/trips/{tripId}/recommendation-jobs", tripId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].jobId").value(newest.toString()))
				.andExpect(jsonPath("$.data[1].jobId").value(older.toString()));
	}

	private RecommendationJob jobWithId(UUID id, UUID userId) {
		return RecommendationJob.start(id, UUID.randomUUID(), userId, JobType.ITINERARY_GENERATION,
				OffsetDateTime.now());
	}

	@Test
	@DisplayName("🔴 인증 주체가 없으면 주인일 수 없다 — 404. 헤더로 신원을 주장할 수 없다")
	void missingPrincipalIsNeverOwner() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(jobOwnedBy(this.ownerId)));

		mockMvc.perform(get("/api/v1/jobs/{jobId}", this.jobId))
				.andExpect(status().isNotFound());
	}
}
