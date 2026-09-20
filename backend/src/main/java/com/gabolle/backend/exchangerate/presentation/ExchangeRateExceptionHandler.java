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
 * 환율 조회 실패를 상태코드로 번역한다. {@code assignableTypes}로 {@link ExchangeRateController}
 * 에만 걸어 다른 컨트롤러의 같은 예외 타입까지 가로채지 않는다. 벤더 호출 실패는 502로 내려간다.
 */
@RestControllerAdvice(assignableTypes = ExchangeRateController.class)
@Profile({ "db", "dev" })
public class ExchangeRateExceptionHandler {

	@ExceptionHandler(ExchangeRateVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(ExchangeRateVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
