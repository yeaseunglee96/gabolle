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
 * 추천 결과 조회 — S15P21E201-604.
 *
 * <h2>🔴 이 경로는 확정된 결정을 뒤집는다 — 조용히 뒤집지 않는다</h2>
 *
 * {@code RecommendationService} 의 javadoc(2026-09-02 확정, "공개 경로 — 정해졌다")은
 * "기능·화면 상세설계서의 {@code GET /api/v1/recommendation-jobs/{id}} 는 쓰지 않는다"고
 * 적어 뒀다. 이 컨트롤러가 정확히 그 경로를 연다. 근거는 둘이다.
 *
 * <ol>
 * <li>프론트가 이미 이 경로에 코드로 붙어 있다. 경로를 바꾸려면 프론트와 조정을 거쳐야
 * 하는데, 그 왕복이 지금 이 기능의 완성을 막고 있다.</li>
 * <li>이것은 {@link RecommendationJobController#get} (= {@code GET /api/v1/jobs/{id}},
 * "지금 몇 % 왔나")의 중복이 <b>아니다.</b> 그쪽은 진행 상태를 돌려주고, 이 컨트롤러는
 * <b>결과</b>(추천된 장소 목록·일정)를 돌려준다 — 다른 자원이다. {@code GET /api/v1/jobs/{id}}
 * 는 이 작업에서 그대로 둔다.</li>
 * </ol>
 *
 * <p>배선은 {@link RecommendationJobController} 와 같다 — {@code @Profile({"db","dev"})}
 * ({@link RecommendationJobRunner} 가 그 프로필에만 있다) ·
 * {@code @ConditionalOnBean(RecommendationJobRunner.class)}(그 러너가 없는 슬라이스에서는
 * 이 컨트롤러도 없어야 생성자 주입이 안 깨진다).
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
	 * 🔴 소유권 검증 — 남의 {@code jobId} 를 알아도 읽히면 안 된다. 없는 작업 번호와
	 * 같은 404 로 답한다({@code RecommendationJobController.JobNotFoundException} 을 그대로
	 * 쓴다 — 두 컨트롤러가 각자 다른 404 모양을 만들면 그 차이 자체가 "있는데 너는 못 본다"
	 * 는 신호가 된다).
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
