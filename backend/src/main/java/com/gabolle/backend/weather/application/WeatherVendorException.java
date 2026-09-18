package com.gabolle.backend.weather.application;

import org.springframework.http.HttpStatus;

/**
 * 기상청을 부르지 못했다 — S15P21E201-366.
 *
 * <p>🔴 <b>이 예외를 잡고 지어낸 값으로 대신 답하지 않는다.</b> 이 티켓이 참고 코드에서 실제로
 * 본 버그가 바로 이것이다 — 날짜 글자의 문자코드를 더해 기온·강수확률을 지어낸 자리. 이
 * 예외는 {@code WeatherExceptionHandler} 가 502 로 번역해 화면에 그대로 전달한다.
 * {@code TranslationVendorException} 과 같은 모양(code+message+status)이다.
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
