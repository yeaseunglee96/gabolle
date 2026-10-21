package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/** 별점의 실패를 HTTP 로 옮긴다. 다른 여행 컨트롤러와 번역표를 섞지 않으려 따로 둔다. */
@RestControllerAdvice(assignableTypes = TripRatingController.class)
public class TripRatingExceptionHandler {

	/** 없는 여행이거나 구성원이 아니다. 구분해 답하지 않는다 — 남의 여행이 있는지 알려 주게 된다. */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_NOT_FOUND", "그 여행을 찾지 못했어요."), requestId()));
	}

	/** 점수가 비었거나 1~5 밖이다. 여행 ID 가 UUID 꼴이 아닐 때도 여기로 온다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_RATING_INVALID", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
