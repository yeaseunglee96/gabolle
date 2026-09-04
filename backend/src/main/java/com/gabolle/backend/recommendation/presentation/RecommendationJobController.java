package com.gabolle.backend.recommendation.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.dto.CreateRecommendationJobRequest;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;

/**
 * 일정 생성 Job — S15P21E201-192 · REC-01 · JOB-01.
 *
 * <p>🔴 경로는 {@code RecommendationService} 의 javadoc(2026-09-02 확정)을 그대로 따른다 —
 * 기능·화면 상세설계서에 있는 다른 경로({@code tripId} 없는 {@code POST
 * /api/v1/recommendation-jobs})는 쓰지 않는다.
 *
 * <p>🔴 <b>생성 요청은 계산을 기다리지 않는다.</b> {@code 202} 로 작업 번호만 즉시 돌려주고,
 * 실제 계산은 {@link RecommendationJobRunner} 가 뒤에서 이어간다.
 *
 * <p>🔴 {@code @Profile({"db","dev"})} — {@link RecommendationJobRunner} 가 그 프로필에만
 * 있다({@code RecommendationService} 와 같은 이유). no-db 프로필에는 이 컨트롤러도 없다.
 */
@RestController
@Profile({ "db", "dev" })
public class RecommendationJobController {

	private final RecommendationJobRunner runner;

	public RecommendationJobController(RecommendationJobRunner runner) {
		this.runner = runner;
	}

	/**
	 * REC-01 — {@code 202} + 작업 번호. 계산이 끝나기 전에 돌아온다.
	 *
	 * <p>🔴 <b>아직 없는 것</b> — {@code Idempotency-Key}. 재시도로 같은 요청이 두 번 오면
	 * Job 이 두 개 생긴다.
	 */
	@PostMapping("/api/v1/trips/{tripId}/recommendation-jobs")
	public ResponseEntity<ApiResponse<RecommendationJobResponse>> create(
			@PathVariable String tripId,
			@RequestBody(required = false) CreateRecommendationJobRequest request,
			@RequestHeader(value = "X-User-Id", required = false) String userId) {

		CreateRecommendationJobRequest body = (request != null) ? request
				: new CreateRecommendationJobRequest(null, null);
		String requester = (userId != null) ? userId : "usr_unknown";

		RecommendationJob job = this.runner.enqueue(tripId, requester, body.preferenceSnapshotVersion(),
				body.topK());

		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(RecommendationJobResponse.of(job), "req_" + UUID.randomUUID()));
	}

	/** JOB-01 — 작업 번호로 진행 상황을 묻는다. */
	@GetMapping("/api/v1/jobs/{jobId}")
	public ApiResponse<RecommendationJobResponse> get(@PathVariable String jobId) {
		RecommendationJob job = this.runner.findJob(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		return ApiResponse.success(RecommendationJobResponse.of(job), "req_" + UUID.randomUUID());
	}

	/** 없는 작업 번호로 조회했다. */
	public static class JobNotFoundException extends RuntimeException {
		public JobNotFoundException(String jobId) {
			super("Job 을 찾을 수 없습니다: " + jobId);
		}
	}
}
