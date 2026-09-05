package com.gabolle.backend.recommendation.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
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
 *
 * <p>🔴 {@code @ConditionalOnBean(RecommendationJobRunner.class)} — {@link RecommendationJobRunner}
 * 자체도 {@code TripQueryService} 가 없는 배포(추천 전용 테스트 슬라이스)에서는 안 만들어진다
 * (그 클래스의 javadoc 참고). 그 러너가 없으면 이 컨트롤러도 생기면 안 된다 — 생성자
 * 주입이 실패해 컨텍스트 전체가 못 뜬다.
 */
@RestController
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
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
			Authentication authentication) {

		CreateRecommendationJobRequest body = (request != null) ? request
				: new CreateRecommendationJobRequest(null, null);
		// 🔴 S15P21E201-604 — 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다.
		//    헤더는 부르는 쪽이 마음대로 정하는 값이라 소유·참여 검사가 그 주장 위에서 돈다.
		//    게다가 앱은 그 헤더를 아예 안 보낸다(frontend/src/api/client.ts 는 Authorization
		//    만 싣는다) — 헤더 방식으로는 실제 클라이언트에서 이 API 가 동작할 수 없었다.
		String requester = AuthenticatedUsers.requireId(authentication).toString();

		RecommendationJob job = this.runner.enqueue(tripId, requester, body.preferenceSnapshotVersion(),
				body.topK());

		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(RecommendationJobResponse.of(job), "req_" + UUID.randomUUID()));
	}

	/**
	 * JOB-01 — 작업 번호로 진행 상황을 묻는다.
	 *
	 * <p>🔴 S15P21E201-604 — 소유권 검증을 더한다. 지금까지 이 메서드에 검증이 아예 없어서
	 * 남의 {@code jobId} 를 알기만 하면 진행 상황이 그대로 읽혔다. {@code RecommendationResultController}
	 * 만 잠그면 결과 API 옆에 이 조회 API 가 옆문으로 남으므로 같이 막는다.
	 *
	 * <p>없는 작업 번호와 남의 작업 번호를 <b>같은 404</b> 로 답한다 — {@code TripQueryService}
	 * 가 여행 조회에서 쓰는 것과 같은 논리다(있는데 너는 못 본다 를 알려주면 존재 자체가 샌다).
	 */
	@GetMapping("/api/v1/jobs/{jobId}")
	public ApiResponse<RecommendationJobResponse> get(@PathVariable String jobId,
			Authentication authentication) {
		RecommendationJob job = this.runner.findJob(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (!isOwner(job, authentication)) {
			throw new JobNotFoundException(jobId);
		}
		return ApiResponse.success(RecommendationJobResponse.of(job), "req_" + UUID.randomUUID());
	}

	/**
	 * 요청자가 이 Job 의 주인인가.
	 *
	 * <p>🔴 신원을 <b>인증 주체</b>에서만 읽는다. 요청 헤더로 받으면 부르는 쪽이 그 값을
	 * 정할 수 있어서 이 검사가 이름만 남는다. 인증이 없거나 주체가 UUID 모양이 아니면
	 * 주인이 아닌 것으로 보고 404 로 답한다 — 예외를 그대로 흘리면 이 자리만 401·400 이
	 * 나가서 "없는 것" 과 "남의 것" 의 경계가 응답 모양으로 샌다.
	 */
	static boolean isOwner(RecommendationJob job, Authentication authentication) {
		return AuthenticatedUsers.optionalId(authentication)
				.map(id -> id.equals(job.getUserId()))
				.orElse(false);
	}

	/** 없는 작업 번호로 조회했다(또는 남의 작업 번호다 — 둘을 구분해 응답하지 않는다). */
	public static class JobNotFoundException extends RuntimeException {
		public JobNotFoundException(String jobId) {
			super("Job 을 찾을 수 없습니다: " + jobId);
		}
	}
}
