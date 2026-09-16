package com.gabolle.backend.exchangerate.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.exchangerate.application.ExchangeRateVendorException;

/**
 * 환율 조회의 실패를 명확한 상태코드로 번역한다 — S15P21E201-1079.
 *
 * <p>🔴 {@code assignableTypes}로 {@link ExchangeRateController}에만 건다 — {@code
 * WeatherExceptionHandler}와 같은 이유로 다른 컨트롤러의 같은 예외 타입까지 가로채지 않는다.
 *
 * <p>🔴 <b>벤더 호출 실패를 200으로 숨기지 않는다.</b> {@link ExchangeRateVendorException}은
 * 502(Bad Gateway)로 내려간다.
 */
@RestControllerAdvice(assignableTypes = ExchangeRateController.class)
@Profile({ "db", "dev" })
public class ExchangeRateExceptionHandler {

	/** 한국수출입은행을 부르지 못했다 — 절대 200으로 위장하지 않는다. */
	@ExceptionHandler(ExchangeRateVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(ExchangeRateVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
