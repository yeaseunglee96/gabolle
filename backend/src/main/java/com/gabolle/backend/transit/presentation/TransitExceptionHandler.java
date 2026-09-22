package com.gabolle.backend.transit.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.transit.application.TransitVendorException;

/**
 * 대중교통 조회 실패를 상태코드로 번역한다. {@code assignableTypes}로 {@link TransitController}
 * 에만 걸어 다른 컨트롤러의 같은 예외 타입까지 가로채지 않는다. 벤더 호출 실패는 502로 내려간다.
 */
@RestControllerAdvice(assignableTypes = TransitController.class)
@Profile({ "db", "dev" })
public class TransitExceptionHandler {

	/** 숫자 자리에 그 형식이 아닌 것이 왔다 — {@code lat=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRANSIT_INVALID_REQUEST", "값의 형식이 올바르지 않습니다.",
						List.of(exception.getName())),
				requestId()));
	}

	/** 필수 칸(lat·lng)이 빠졌다. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissing(MissingServletRequestParameterException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRANSIT_INVALID_REQUEST", "필수 값이 빠졌습니다.",
						List.of(exception.getParameterName())),
				requestId()));
	}

	@ExceptionHandler(TransitVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(TransitVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
