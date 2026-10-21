package com.gabolle.backend.facility.api;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 근처 편의시설 조회의 요청 오류를 계약 모양(400 과 {@code error} 본문)으로 번역한다 (S15P21E201-2052). 안 잡으면 프레임워크 기본 응답이
 * 나가서 앱이 {@code INVALID_RESPONSE} 로 읽는다 — {@code PlaceExceptionHandler} 와 같은 이유다. 인증 모듈의 광범위한 처리기보다 먼저
 * 잡히도록 {@link Ordered#HIGHEST_PRECEDENCE} 를 건다.
 */
@RestControllerAdvice(basePackages = "com.gabolle.backend.facility.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FacilityExceptionHandler {

	@ExceptionHandler(FacilityRequestException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalid(FacilityRequestException exception) {
		return badRequest(exception.getMessage(), exception.getFields());
	}

	/** 숫자가 아닌 좌표·불리언이 아닌 필터 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return badRequest("요청 값의 형식이 올바르지 않습니다.", List.of(exception.getName()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissing(MissingServletRequestParameterException exception) {
		return badRequest("필수 값이 빠졌습니다.", List.of(exception.getParameterName()));
	}

	private ResponseEntity<ApiResponse<Void>> badRequest(String message, List<String> fields) {
		return ResponseEntity.badRequest().body(
				ApiResponse.failure(new ApiError("INVALID_REQUEST", message, fields), "req_" + UUID.randomUUID()));
	}
}
