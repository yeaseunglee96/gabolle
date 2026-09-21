package com.gabolle.backend.assistant.application;

import org.springframework.http.HttpStatus;

/**
 * 같은 사용자가 짧은 시간에 너무 많이 물었다. 무료 티어 호출 한도를 한 사람이 다 쓰는 것을
 * 막는다. 429 로 내려간다.
 */
public class AssistantRateLimitExceededException extends RuntimeException {

	public AssistantRateLimitExceededException(String message) {
		super(message);
	}

	public HttpStatus getStatus() {
		return HttpStatus.TOO_MANY_REQUESTS;
	}
}
