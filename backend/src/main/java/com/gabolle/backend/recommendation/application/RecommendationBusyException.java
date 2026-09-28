package com.gabolle.backend.recommendation.application;

import java.util.UUID;

/**
 * 추천 실행기가 꽉 차 작업을 못 받았다 (S15P21E201-1685). 작업은 이미 「실패 · 다시 시도 가능」
 * ({@link RecommendationCodes#ERROR_SERVER_BUSY})으로 저장돼 있다 — 이 예외는 요청한 사람에게 「지금 요청이 많다」고
 * 알리는 일만 한다. 응답은 {@code RecommendationBusyExceptionHandler} 가 503 봉투로 바꾼다.
 */
public class RecommendationBusyException extends RuntimeException {

	private final UUID jobId;

	public RecommendationBusyException(UUID jobId, Throwable cause) {
		super("추천 실행기가 꽉 차 작업을 못 받았다: jobId=" + jobId, cause);
		this.jobId = jobId;
	}

	public UUID jobId() {
		return this.jobId;
	}
}
