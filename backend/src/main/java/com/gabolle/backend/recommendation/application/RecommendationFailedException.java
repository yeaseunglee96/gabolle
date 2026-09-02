package com.gabolle.backend.recommendation.application;

import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobStage;

/**
 * 추천을 만들지 못했다. 던져지기 <b>전에</b> Job 은 이미 FAILED 로 저장돼 있다.
 *
 * <p>🔴 실패를 조용히 빈 결과로 바꾸지 않는 이유: 빈 결과와 실패는 나중에 전혀 다른 것을
 * 뜻하는데, 응답만 보고는 구분할 수 없다. 부르는 쪽이 {@code requestId} 로 저장된 Job 을
 * 찾아 원인을 볼 수 있도록 키를 함께 들려 보낸다.
 */
public class RecommendationFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final UUID requestId;

	private final UUID jobId;

	private final String errorCode;

	private final JobStage failureStage;

	public RecommendationFailedException(UUID requestId, UUID jobId, String errorCode, JobStage failureStage,
			String message, Throwable cause) {
		super(message, cause);
		this.requestId = requestId;
		this.jobId = jobId;
		this.errorCode = errorCode;
		this.failureStage = failureStage;
	}

	public UUID getRequestId() {
		return this.requestId;
	}

	public UUID getJobId() {
		return this.jobId;
	}

	public String getErrorCode() {
		return this.errorCode;
	}

	public JobStage getFailureStage() {
		return this.failureStage;
	}
}
