package com.gabolle.backend.recommendation.presentation.dto;

import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

/**
 * GB-API-001 4.2 JobDto — S15P21E201-192. REC-01 202 응답과 JOB-01 200 응답이 함께 쓴다.
 */
public record RecommendationJobResponse(
		UUID jobId,
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
				job.getJobId(), job.getJobType(), job.getJobStatus(),
				new Progress(job.getJobStage(), job.getProgressPercent()),
				failure, job.isRetryable(), job.getPollAfterSeconds());
	}
}
