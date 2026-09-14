package com.gabolle.backend.assistant.application;

import org.springframework.http.HttpStatus;

/**
 * AI 업체(Claude)를 부르지 못했다 — S15P21E201-802.
 *
 * <p>{@code TranslationVendorException} 과 같은 모양(code+message+status)이고 같은 이유다 —
 * 실패를 잡아 미리 정해 둔 답으로 대신하지 않는다. 대신할 일정 추천을 지어내면 그건 도움이
 * 아니라 창작이고, 화면은 그것을 실제 AI 응답인 줄 안다.
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
