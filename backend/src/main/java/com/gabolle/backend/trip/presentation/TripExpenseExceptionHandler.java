package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripExpenseService;
import com.gabolle.backend.trip.application.TripQueryService;

/** 여행 돈의 실패를 HTTP 로 옮긴다. 다른 여행 컨트롤러와 번역표를 섞지 않으려 따로 둔다. */
@RestControllerAdvice(assignableTypes = TripExpenseController.class)
public class TripExpenseExceptionHandler {

	/** 없는 여행이거나 구성원이 아니다. 구분해 답하지 않는다 — 남의 여행이 있는지 알려 주게 된다. */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_NOT_FOUND", "그 여행을 찾지 못했어요."), requestId()));
	}

	@ExceptionHandler(TripExpenseService.ExpenseNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleExpenseNotFound(TripExpenseService.ExpenseNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_EXPENSE_NOT_FOUND", "그 줄을 찾지 못했어요. 이미 지워졌을 수 있어요."), requestId()));
	}

	/** 구성원이지만 권한이 없다 — 남이 적은 줄 지우기, 보기 전용 동행자의 예산 고치기. 존재를 감출 이유가 없어 403. */
	@ExceptionHandler(TripExpenseService.ExpenseForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(TripExpenseService.ExpenseForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(
				new ApiError("TRIP_EXPENSE_FORBIDDEN", e.getMessage()), requestId()));
	}

	/** 금액·갈래·글자 수가 맞지 않거나, 낸 사람이 구성원이 아니다. 여행 ID 가 UUID 꼴이 아닐 때도 여기로 온다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_EXPENSE_INVALID", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
