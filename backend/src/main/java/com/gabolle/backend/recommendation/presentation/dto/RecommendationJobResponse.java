package com.gabolle.backend.recommendation.presentation.dto;

import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

/**
 * GB-API-001 4.2 JobDto — S15P21E201-192. REC-01 202 응답과 JOB-01 200 응답이 함께 쓴다.
 *
 * <h2>🔴 2026-09-07 — {@code requestId} 를 더했다 (S15P21E201-735)</h2>
 * 이 응답과 {@link RecommendationResultResponse} 둘 다 {@code jobId} 만 주고 있었다. 그런데
 * 행동 이벤트({@code POST /api/v1/events})는 <b>{@code requestId} 로만</b> "무엇을 보여줬고
 * 그중 무엇을 골랐나" 를 잇는다(-542 14장). 즉 앱은 이을 열쇠를 <b>받을 방법이 없는 채로</b>
 * 그 열쇠를 요구받고 있었다.
 *
 * <p>🔴 이것을 앱이 UUID 를 하나 지어내는 것으로 때울 수 없다. 지어낸 값은 어느 추천 요청과도
 * 안 맞으므로 조인이 <b>되는 척하면서 0건</b>이 되고, 빈 것이 아니라 <b>틀린 짝</b>이 된다.
 * 안 보내면 비는 것이 보이지만 지어내면 아무도 못 알아챈다.
 *
 * <p>{@code recommendation_job.request_id} 는 {@code UUID NOT NULL UNIQUE} 로 이미 있다.
 * 없던 값을 만든 것이 아니라 <b>있던 값을 공개한 것</b>이다.
 */
public record RecommendationJobResponse(
		UUID jobId,
		/** 🔴 이 추천 요청의 정본 키. 앱이 노출·저장·제외 이벤트에 그대로 실어 보낸다 (-735). */
		UUID requestId,
		JobType type,
		JobStatus status,
		Progress progress,
		Failure failure,
		boolean retryable,
		Integer pollAfterSeconds) {

	public record Progress(JobStage stage, int percent) {
	}

	/**
	 * @param detail 🔴 지금은 failureStage 이름을 그대로 보여준다 — 사람이 읽을 설명 문구
	 *     사전은 아직 없다(그 사전은 온톨로지·오류코드 쪽 몫이라 이 티켓 범위 밖이다)
	 */
	public record Failure(String code, String detail) {
	}

	public static RecommendationJobResponse of(RecommendationJob job) {
		Failure failure = (job.getJobStatus() == JobStatus.FAILED)
				? new Failure(job.getErrorCode(), job.getFailureStage() == null ? null : job.getFailureStage().name())
				: null;
		return new RecommendationJobResponse(
				job.getJobId(), job.getRequestId(), job.getJobType(), job.getJobStatus(),
				new Progress(job.getJobStage(), job.getProgressPercent()),
				failure, job.isRetryable(), job.getPollAfterSeconds());
	}
}
