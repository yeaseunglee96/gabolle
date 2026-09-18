package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 여행 밑에 붙는 일정 쪽 경로 전용. 없는 여행과 비회원을 같은 404 로 답한다 — 존재를 감춘다.
 *
 * <p>2026-09-08 — {@link TripItineraryController}(S15P21E201-738)를 함께 맡긴다. 두 경로가 같은
 * 규칙으로 404 를 내야 하는데 advice 를 따로 두면 한쪽만 고쳐지는 날이 온다.
 */
@RestControllerAdvice(assignableTypes = { TripActivityController.class, TripItineraryController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TripActivityExceptionHandler {

	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("TRIP_NOT_FOUND", "그 여행을 찾지 못했어요."), requestId()));
	}

	/** {@code limit} 범위 밖. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_ACTIVITY_INVALID_REQUEST", e.getMessage(), List.of("limit")), requestId()));
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
