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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S15P21E201-604 — 소유권 검증. 남의 {@code jobId} 를 알아도 진행 상황도 결과도 못 본다.
 *
 * <p>🔴 {@code GET /api/v1/jobs/{id}}(진행 상태)와 {@code GET /api/v1/recommendation-jobs/{id}}
 * (결과) 둘 다 본다 — 결과 API 만 잠그면 진행 상태 API 가 옆문으로 남는다는 게 이 작업의
 * 지적이었다.
 *
 * <p>DB 없이 도는 슬라이스 테스트다 — {@link RecommendationJobRunner}·
 * {@link RecommendationResultQueryService} 를 mock 으로 세운다({@code RecommendationJobRunnerTest}
 * 와 같은 방식).
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
						// 진행률 통로를 들고 있는 쪽(S15P21E201-193). 이 검사는 그 통로를
						// 쓰지 않지만 컨트롤러가 요구하므로 진짜 객체를 그대로 준다 —
						// 상태를 갖지 않아 mock 으로 대신할 이유가 없다.
						new RecommendationJobController(this.runner, new JobProgressBroker()),
						new RecommendationResultController(this.runner, this.resultQueryService))
				.setControllerAdvice(new RecommendationJobExceptionHandler())
				.build();
	}

	/**
	 * 🔴 신원을 요청 헤더가 아니라 인증 주체로 준다. 컨트롤러가 헤더를 안 보기 때문이기도
	 * 하고, 헤더로 신원을 주장할 수 있으면 이 검사 자체가 무의미하기 때문이기도 하다.
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

		// 🔴 소유권 검사가 서비스 호출보다 먼저다 — 남의 결과를 조립조차 하지 않는다.
		verify(this.resultQueryService, never()).buildResult(any());
	}

	@Test
	@DisplayName("결과 — 주인이 조회하면 200")
	void resultEndpointAllowsOwner() throws Exception {
		RecommendationJob job = jobOwnedBy(this.ownerId);
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(job));
		when(this.resultQueryService.buildResult(job)).thenReturn(new RecommendationResultResponse(
				"COMPLETED", List.of(), null, null, List.of(), null, 0, null, UUID.randomUUID().toString()));

		mockMvc.perform(get("/api/v1/recommendation-jobs/{jobId}", this.jobId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("COMPLETED"));
	}

	@Test
	@DisplayName("🔴 인증 주체가 없으면 주인일 수 없다 — 404. 헤더로 신원을 주장할 수 없다")
	void missingPrincipalIsNeverOwner() throws Exception {
		when(this.runner.findJob(this.jobId.toString())).thenReturn(Optional.of(jobOwnedBy(this.ownerId)));

		mockMvc.perform(get("/api/v1/jobs/{jobId}", this.jobId))
				.andExpect(status().isNotFound());
	}
}
