package com.gabolle.backend.tools.application;

import org.springframework.http.HttpStatus;

/**
 * 번역 업체를 부르지 못했다 — S15P21E201-343.
 *
 * <p>🔴 <b>이 예외를 잡고 미리 정해 둔 문장으로 대신 답하지 않는다.</b> 그렇게 하면 화면은
 * 번역이 된 줄 알고 그리는데 실제로는 업체를 부르지도 못한 것이다 — 이 티켓이 참고 코드에서
 * 실제로 봤다며 명시적으로 금지한 바로 그 실수다. 이 예외는 {@code TranslateExceptionHandler}
 * 가 502 로 번역해 화면에 그대로 전달한다. {@code AuthException} 과 같은 모양(code+message+status)
 * 이다.
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
