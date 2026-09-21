package com.gabolle.backend.tools.application;

import org.springframework.http.HttpStatus;

/**
 * 번역 업체를 부르지 못했다. 이 예외를 잡고 미리 정해 둔 문장으로 대신 답하지 않는다 — 그러면 화면은
 * 번역이 된 줄 알고 그린다. {@code TranslateExceptionHandler} 가 502 로 번역해 그대로 전달한다.
 */
public class TranslationVendorException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public TranslationVendorException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public TranslationVendorException(String code, String message, HttpStatus status, Throwable cause) {
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
