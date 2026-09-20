package com.gabolle.backend.exchangerate.application;

import org.springframework.http.HttpStatus;

/**
 * 한국수출입은행을 부르지 못했다. 이 예외를 잡고 지어낸 값으로 대신 답하지 않는다 — 환율은
 * 대신할 추정이 없다. {@code ExchangeRateExceptionHandler}가 502로 번역한다.
 */
public class ExchangeRateVendorException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public ExchangeRateVendorException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public ExchangeRateVendorException(String code, String message, HttpStatus status, Throwable cause) {
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
