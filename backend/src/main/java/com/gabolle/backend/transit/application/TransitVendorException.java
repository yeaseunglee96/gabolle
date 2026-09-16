package com.gabolle.backend.transit.application;

import org.springframework.http.HttpStatus;

/**
 * TAGO를 부르지 못했다 — S15P21E201-988.
 *
 * <p>🔴 <b>이 예외를 잡고 지어낸 값으로 대신 답하지 않는다.</b> {@code WeatherVendorException}과
 * 같은 이유 — 도착 예정 시간은 대신할 추정이 없다. {@code TransitExceptionHandler}가 502로
 * 번역해 화면에 그대로 전달한다.
 */
public class TransitVendorException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public TransitVendorException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public TransitVendorException(String code, String message, HttpStatus status, Throwable cause) {
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
