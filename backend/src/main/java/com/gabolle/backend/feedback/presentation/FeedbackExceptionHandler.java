package com.gabolle.backend.feedback.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.feedback.application.FeedbackService;

/** 의견 보내기의 오류 응답 — S15P21E201-2049. */
@RestControllerAdvice(assignableTypes = FeedbackController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeedbackExceptionHandler {

	@ExceptionHandler(FeedbackService.InvalidFeedbackException.class)
	public ResponseEntity<ApiResponse<Void>> invalid(FeedbackService.InvalidFeedbackException e) {
		return fail(HttpStatus.BAD_REQUEST, "FEEDBACK_INVALID", e.getMessage(), e.fields());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiResponse<Void>> unreadable(Exception e) {
		return fail(HttpStatus.BAD_REQUEST, "FEEDBACK_INVALID", "요청 값을 읽을 수 없습니다.", List.of());
	}

	@ExceptionHandler(FeedbackService.InvalidScreenshotException.class)
	public ResponseEntity<ApiResponse<Void>> screenshot(RuntimeException e) {
		return fail(HttpStatus.BAD_REQUEST, "FEEDBACK_SCREENSHOT_INVALID", e.getMessage(), List.of("screenshotUrls"));
	}

	@ExceptionHandler(FeedbackService.RateLimitedException.class)
	public ResponseEntity<ApiResponse<Void>> limited(RuntimeException e) {
		return fail(HttpStatus.TOO_MANY_REQUESTS, "FEEDBACK_RATE_LIMITED", e.getMessage(), List.of());
	}

	@ExceptionHandler(FeedbackService.FeedbackNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> notFound(RuntimeException e) {
		return fail(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND", e.getMessage(), List.of());
	}

	private static ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, String code, String message,
			List<String> fields) {
		return ResponseEntity.status(status)
			.body(ApiResponse.failure(new ApiError(code, message, fields), "req_" + UUID.randomUUID()));
	}
}
