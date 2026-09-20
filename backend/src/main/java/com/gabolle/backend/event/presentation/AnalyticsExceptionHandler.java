package com.gabolle.backend.event.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 지표 조회의 오류 응답. {@code assignableTypes} 를 안 쓰면 이 advice 가 다른 컨트롤러의
 * 예외까지 가로채고, {@code @Order} 가 없으면 다른 넓은 advice 에 먼저 잡힌다.
 */
@RestControllerAdvice(assignableTypes = AnalyticsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalyticsExceptionHandler {

	/**
	 * {@code from} 이 {@code to} 보다 뒤거나 같다 — 400. 번역하지 않으면 응용 계층의 범위
	 * 검사가 500 으로 나가는데, 클라이언트가 고칠 수 있는 것이라 400 이 맞다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRange(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("INVALID_KPI_RANGE", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
