package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 종 점 조회 전용 (S15P21E201-1699). {@code since} 가 시각으로 안 읽히면 우리 봉투의 400 이다 — 받은 값은 되돌려 적지 않는다.
 */
@RestControllerAdvice(assignableTypes = NotificationSummaryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationSummaryExceptionHandler {

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleBadSince(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("NOTIFICATION_SUMMARY_INVALID_REQUEST", "since 는 ISO-8601 시각이어야 해요.", List.of("since")),
				"req_" + UUID.randomUUID()));
	}
}
