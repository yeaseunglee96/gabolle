package com.gabolle.backend.assistant.application;

import org.springframework.http.HttpStatus;

/**
 * 같은 사용자가 짧은 시간에 너무 많이 물었다 — S15P21E201-802.
 *
 * <p>무료 티어(Gemini) 호출 한도를 한 사용자가 다 써버리는 것을 막는다. 429(Too Many
 * Requests)로 내려간다 — {@code AssistantExceptionHandler} 참고.
 */
public class AssistantRateLimitExceededException extends RuntimeException {

	public AssistantRateLimitExceededException(String message) {
		super(message);
	}

	public HttpStatus getStatus() {
		return HttpStatus.TOO_MANY_REQUESTS;
	}
}
