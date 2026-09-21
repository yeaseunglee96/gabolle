package com.gabolle.backend.weather.application;

import org.springframework.http.HttpStatus;

/**
 * 기상청을 부르지 못했다. 이 예외를 잡고 지어낸 값으로 대신 답하지 않는다 —
 * WeatherExceptionHandler 가 502 로 번역해 화면에 그대로 전달한다.
 */
public class WeatherVendorException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public WeatherVendorException(String code, String message, HttpStatus status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public WeatherVendorException(String code, String message, HttpStatus status, Throwable cause) {
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
