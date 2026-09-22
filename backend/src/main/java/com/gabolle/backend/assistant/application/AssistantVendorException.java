package com.gabolle.backend.assistant.application;

import org.springframework.http.HttpStatus;

/**
 * AI 업체를 부르지 못했다. 실패를 잡아 미리 정해 둔 답으로 대신하지 않는다 — 화면이 그것을
 * 실제 AI 응답인 줄 안다.
 */
public class AssistantVendorException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public AssistantVendorException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public AssistantVendorException(String code, String message, HttpStatus status, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.status = status;
	}

	public String getCode() {
		return this.code;
	}

	public HttpStatus getStatus() {
		return this.status;
	}
}
