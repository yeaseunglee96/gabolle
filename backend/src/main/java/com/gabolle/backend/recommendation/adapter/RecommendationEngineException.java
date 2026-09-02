package com.gabolle.backend.recommendation.adapter;

/** 추천 엔진이 결과를 만들지 못했다. */
public class RecommendationEngineException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String errorCode;

	private final boolean timeout;

	public RecommendationEngineException(String errorCode, String message, boolean timeout, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
		this.timeout = timeout;
	}

	public RecommendationEngineException(String errorCode, String message) {
		this(errorCode, message, false, null);
	}

	/** Job 의 {@code error_code} 로 그대로 남는다. */
	public String getErrorCode() {
		return this.errorCode;
	}

	public boolean isTimeout() {
		return this.timeout;
	}
}
