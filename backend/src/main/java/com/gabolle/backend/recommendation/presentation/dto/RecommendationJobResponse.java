package com.gabolle.backend.recommendation.presentation.dto;

import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

/** 추천 Job 상태 응답. Job 생성 202 응답과 Job 조회 200 응답이 함께 쓴다. */
public record RecommendationJobResponse(
		UUID jobId,
		/** 이 추천 요청의 정본 키. 앱이 노출·저장·제외 이벤트에 그대로 실어 보낸다. */
		UUID requestId,
		JobType type,
		JobStatus status,
		Progress progress,
		Failure failure,
		boolean retryable,
		Integer pollAfterSeconds) {

	public record Progress(JobStage stage, int percent) {
	}

	/** @param detail 지금은 failureStage 이름을 그대로 보여준다. 설명 문구 사전이 아직 없다 */
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
