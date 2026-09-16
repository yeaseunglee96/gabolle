package com.gabolle.backend.assistant.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.assistant.application.AssistantRateLimitExceededException;
import com.gabolle.backend.assistant.application.AssistantVendorException;
import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;

/**
 * 여행 도우미의 실패를 명확한 상태코드로 번역한다 — S15P21E201-802.
 *
 * <p>{@code TranslateExceptionHandler} 와 같은 이유로 {@code assignableTypes} 를
 * {@link AssistantController} 하나에만 건다.
 */
@RestControllerAdvice(assignableTypes = AssistantController.class)
@Profile({ "db", "dev" })
public class AssistantExceptionHandler {

	/** 메시지가 비었거나 너무 길다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("ASSISTANT_INVALID_REQUEST", exception.getMessage()), requestId()));
	}

	/** AI 업체를 부르지 못했다 — 절대 200 으로 위장하지 않는다. */
	@ExceptionHandler(AssistantVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(AssistantVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	/** 이 사용자가 1분 한도를 넘겼다. */
	@ExceptionHandler(AssistantRateLimitExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(AssistantRateLimitExceededException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError("ASSISTANT_RATE_LIMITED", exception.getMessage()), requestId()));
	}

	/** itineraryId 로 넘겼는데 그 일정이 없거나 요청자가 회원이 아니다 — S15P21E201-987. */
	@ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleItineraryNotFound(
			ItineraryQueryController.ItineraryNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
