package com.gabolle.backend.recommendation.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.application.RecommendationResultQueryService;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationResultResponse;

/**
 * 추천 결과 조회. {@link RecommendationJobController#get} 과 다른 자원이다 — 그쪽은 진행
 * 상태를, 이쪽은 결과(추천된 장소 목록·일정)를 돌려준다.
 *
 * 프로필과 {@code @ConditionalOnBean} 은 {@link RecommendationJobRunner} 가 없는
 * 슬라이스에서 생성자 주입이 깨지지 않게 한다.
 */
@RestController
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
public class RecommendationResultController {

	private final RecommendationJobRunner runner;

	private final RecommendationResultQueryService resultQueryService;

	public RecommendationResultController(RecommendationJobRunner runner,
			RecommendationResultQueryService resultQueryService) {
		this.runner = runner;
		this.resultQueryService = resultQueryService;
	}

	/**
	 * 남의 {@code jobId} 를 알아도 읽히면 안 되므로 없는 작업 번호와 같은 404 로 답한다.
	 * 두 컨트롤러가 각자 다른 404 모양을 만들면 그 차이 자체가 "있는데 너는 못 본다" 는
	 * 신호가 되므로 예외도 같은 것을 쓴다.
	 *
	 * @throws RecommendationJobController.JobNotFoundException 없거나(또는 남의 것이거나 —
	 *     둘을 구분해 응답하지 않는다)
	 * @throws JobNotReadyException Job 이 아직 {@code PENDING}·{@code RUNNING} 이라 결과가
	 *     없다 — 409. 프론트는 {@code GET /api/v1/jobs/{id}} 로 진행 상황을 본다
	 */
	@GetMapping("/api/v1/recommendation-jobs/{jobId}")
	public ApiResponse<RecommendationResultResponse> get(@PathVariable String jobId,
			Authentication authentication) {

		RecommendationJob job = this.runner.findJob(jobId)
				.orElseThrow(() -> new RecommendationJobController.JobNotFoundException(jobId));
		if (!RecommendationJobController.isOwner(job, authentication)) {
			throw new RecommendationJobController.JobNotFoundException(jobId);
		}

		RecommendationResultResponse response = this.resultQueryService.buildResult(job);
		return ApiResponse.success(response, "req_" + UUID.randomUUID());
	}

	/** 아직 결과가 없다 — Job 이 {@code PENDING}·{@code RUNNING} 이다. */
	public static class JobNotReadyException extends RuntimeException {
		public JobNotReadyException(String jobId) {
			super("추천 결과가 아직 준비되지 않았습니다: " + jobId);
		}
	}
}
