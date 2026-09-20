package com.gabolle.backend.recommendation.presentation;

import java.util.List;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.recommendation.application.JobProgressBroker;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.presentation.dto.CreateRecommendationJobRequest;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationJobResponse;

/**
 * 일정 생성 Job.
 *
 * <p>생성 요청은 계산을 기다리지 않는다. {@code 202} 로 작업 번호만 즉시 돌려주고 실제
 * 계산은 {@link RecommendationJobRunner} 가 뒤에서 이어간다.
 *
 * <p>{@link RecommendationJobRunner} 가 없는 배포(no-db 프로필, {@code TripQueryService} 가
 * 없는 추천 전용 테스트 슬라이스)에서는 이 컨트롤러도 생기면 안 된다 — 생성자 주입이 실패해
 * 컨텍스트 전체가 못 뜬다. 프로필과 조건이 그래서 러너와 같다.
 */
@RestController
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationJobRunner.class)
public class RecommendationJobController {

	private final RecommendationJobRunner runner;

	/** 열려 있는 진행률 통로를 들고 있는 쪽. */
	private final JobProgressBroker progressBroker;

	public RecommendationJobController(RecommendationJobRunner runner, JobProgressBroker progressBroker) {
		this.runner = runner;
		this.progressBroker = progressBroker;
	}

	/**
	 * {@code 202} + 작업 번호. 계산이 끝나기 전에 돌아온다.
	 *
	 * <p>{@code Idempotency-Key} 헤더를 주면 재시도가 안전해진다. 같은 키 + 같은 본문이면 새
	 * Job 을 만들지 않고 기존 Job 을 {@code 200} 으로 돌려주고, 같은 키 + 다른 본문이면
	 * {@code 409} 다. 헤더가 없으면 매번 새 Job 이다.
	 */
	@PostMapping("/api/v1/trips/{tripId}/recommendation-jobs")
	public ResponseEntity<ApiResponse<RecommendationJobResponse>> create(
			@PathVariable String tripId,
			@RequestBody(required = false) CreateRecommendationJobRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			Authentication authentication) {

		CreateRecommendationJobRequest body = (request != null) ? request
				: new CreateRecommendationJobRequest(null, null);
		// 요청자를 헤더가 아니라 인증 주체에서 정한다. 헤더는 부르는 쪽이 마음대로 정하는
		// 값이라 소유·참여 검사가 그 주장 위에서 돈다.
		String requester = AuthenticatedUsers.requireId(authentication).toString();

		RecommendationJobRunner.EnqueueOutcome outcome = this.runner.enqueue(tripId, requester,
				body.preferenceSnapshotVersion(), body.topK(), idempotencyKey);

		HttpStatus status = outcome.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
		return ResponseEntity.status(status)
				.body(ApiResponse.success(RecommendationJobResponse.of(outcome.job()), "req_" + UUID.randomUUID()));
	}

	/**
	 * 여행 번호로 그 여행의 추천 작업을 최신순으로 되찾는다. 화면이 쓰는 것은 대개 맨 앞
	 * 하나지만 목록으로 준다 — 이유는 {@link RecommendationJobRunner#findJobsByTrip} 의 상한
	 * 설명에 있다.
	 *
	 * <p>없는 여행·남의 여행은 404({@code TRIP_NOT_FOUND}), 내 여행인데 추천을 만든 적이
	 * 없으면 200 + 빈 목록이다. 둘을 같은 404 로 답하면 화면이 「아직 안 만들었으니 만들자」와
	 * 「이 여행은 없다」를 갈라 그릴 수 없다.
	 *
	 * <p>이 응답은 진행 상태이지 결과가 아니다. 결과는 여기서 얻은 {@code jobId} 로
	 * {@link RecommendationResultController} 를 부른다.
	 */
	@GetMapping("/api/v1/trips/{tripId}/recommendation-jobs")
	public ApiResponse<List<RecommendationJobResponse>> listByTrip(@PathVariable String tripId,
			Authentication authentication) {
		String requester = AuthenticatedUsers.requireId(authentication).toString();

		List<RecommendationJobResponse> jobs = this.runner.findJobsByTrip(tripId, requester).stream()
				.map(RecommendationJobResponse::of)
				.toList();

		return ApiResponse.success(jobs, "req_" + UUID.randomUUID());
	}

	/**
	 * 작업 번호로 진행 상황을 묻는다.
	 *
	 * <p>없는 작업 번호와 남의 작업 번호를 같은 404 로 답한다 — "있는데 너는 못 본다" 를
	 * 알려주면 존재 자체가 샌다.
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
	 * 진행률을 연결을 열어 둔 채 밀어 보낸다.
	 *
	 * <p>폴링을 없애지는 않는다 — 이 통로는 서버 한 대를 전제하고, 프록시나 이동통신망이
	 * 오래 열린 연결을 끊는 환경도 있다. 화면은 두 길을 다 가질 수 있어야 한다.
	 *
	 * <p>접속하자마자 지금 값을 한 번 보낸다. 다시 붙은 화면은 0%가 아니라 표에 저장된 지금
	 * 진행률을 먼저 받는다. 이미 끝난 작업이면 한 건을 보내고 바로 닫는다.
	 *
	 * <p>소유권 검사는 {@link #get} 과 같은 규칙이다. 여기에만 없으면 남의 {@code jobId} 를
	 * 알기만 하면 그 사람의 계산이 어디까지 갔는지 보인다.
	 */
	// 경로를 value 로 준다. path 는 같은 뜻이지만 인가 정책 표를 대조하는 검사가 value 를
	// 읽어서, path 로 쓰면 경로가 빈 값으로 잡혀 "정책 없는 경로" 로 걸린다.
	@GetMapping(value = "/api/v1/jobs/{jobId}/progress", produces = "text/event-stream")
	public SseEmitter progress(@PathVariable String jobId, Authentication authentication) {
		RecommendationJob job = this.runner.findJob(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (!isOwner(job, authentication)) {
			throw new JobNotFoundException(jobId);
		}

		SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
		JobProgressBroker.JobProgressSnapshot now = new JobProgressBroker.JobProgressSnapshot(
				job.getJobId(), job.getJobStatus(),
				job.getJobStage() == null ? null : job.getJobStage().name(),
				job.getProgressPercent(), job.getErrorCode());

		// 이미 끝난 작업이면 등록하지 않는다. 그 작업은 다시 진행률을 내보내지 않으므로
		// 기다릴 것이 없고, 한 건 보내고 닫는 것으로 끝이다.
		if (job.getJobStatus().isTerminal()) {
			this.progressBroker.send(now, emitter);
			return emitter;
		}

		// 순서가 중요하다. 먼저 등록하고 그다음에 지금 값을 보낸다. 반대로 하면 두 호출
		// 사이에 넘어간 단계 한 건을 못 받아 화면이 다음 단계까지 멈춘 것으로 보인다.
		// 등록이 먼저면 같은 값을 두 번 받을 수 있는데, 진행률은 그래도 화면이 안 달라진다.
		this.progressBroker.register(job.getJobId(), emitter);
		this.progressBroker.send(now, emitter);
		return emitter;
	}

	/**
	 * 연결 하나를 열어 두는 시간의 상한.
	 *
	 * <p>일정 생성이 이보다 오래 걸리면 연결이 한 번 끊기고, 화면은 다시 붙어 그 시점의
	 * 진행률부터 이어 받는다. 무한히 열어 두지 않는 이유는 죽은 연결이 스레드를 잡기
	 * 때문이다 — 브라우저가 조용히 사라지면 서버는 그것을 바로 알 수 없다.
	 */
	static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

	/**
	 * 요청자가 이 Job 의 주인인가. 신원을 인증 주체에서만 읽는다 — 요청 헤더로 받으면 부르는
	 * 쪽이 그 값을 정할 수 있어 검사가 이름만 남는다. 인증이 없거나 주체가 UUID 모양이 아니면
	 * 주인이 아닌 것으로 보고 404 로 답한다: 예외를 흘리면 이 자리만 401·400 이 나가서
	 * "없는 것" 과 "남의 것" 의 경계가 응답 모양으로 샌다.
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
