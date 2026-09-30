package com.gabolle.backend.help.presentation;

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
 * 가까운 도움 조회의 요청 오류 — 400 과 계약 모양의 본문(PlaceExceptionHandler 와 같은 이유: 안 잡으면 프레임워크 기본
 * 응답이 나가 앱이 {@code INVALID_RESPONSE} 로 읽는다).
 */
@RestControllerAdvice(basePackages = "com.gabolle.backend.help.presentation")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HelpPlaceExceptionHandler {

	/** 모르는 갈래(「kind=HOSPITALS」)나 숫자가 아닌 좌표 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return badRequest("요청 값의 형식이 올바르지 않습니다.", List.of(exception.getName()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissing(MissingServletRequestParameterException exception) {
		return badRequest("필수 값이 빠졌습니다.", List.of(exception.getParameterName()));
	}

	/** 좌표가 범위 밖 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegal(IllegalArgumentException exception) {
		return badRequest("좌표가 올바르지 않습니다.", List.of("lat", "lng"));
	}

	private ResponseEntity<ApiResponse<Void>> badRequest(String message, List<String> fields) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(new ApiError("INVALID_REQUEST", message, fields), "req_" + UUID.randomUUID()));
	}
}
