package com.gabolle.backend.review.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.review.application.PlaceReviewService;
import com.gabolle.backend.review.application.VisitVerificationService;
import com.gabolle.backend.review.domain.PlaceReview;

/**
 * 방문 인증·리뷰 컨트롤러의 오류를 HTTP 로 번역한다.
 *
 * {@link Ordered#HIGHEST_PRECEDENCE} 가 필요하다. 범위 제한이 없는
 * {@code GlobalAuthExceptionHandler} 가 먼저 걸리면 여기서 낼 404·422 가 500 으로 나간다.
 *
 * {@code AuthException} 은 여기서 다루지 않는다 — 전역 핸들러가 이미 처리하고, 여기 또 만들면
 * 어느 쪽이 먼저 걸릴지가 등록 순서에 좌우된다.
 */
@RestControllerAdvice(assignableTypes = { VisitVerificationController.class, PlaceReviewController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewExceptionHandler {

	@ExceptionHandler(VisitVerificationService.PlaceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleVisitPlaceNotFound(
			VisitVerificationService.PlaceNotFoundException exception) {
		return notFound(exception.getMessage());
	}

	@ExceptionHandler(PlaceReviewService.PlaceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleReviewPlaceNotFound(
			PlaceReviewService.PlaceNotFoundException exception) {
		return notFound(exception.getMessage());
	}

	/** 요청 모양은 맞으므로 400 이 아니라 422 다. */
	@ExceptionHandler(VisitVerificationService.CoordinatesMissingException.class)
	public ResponseEntity<ApiResponse<Void>> handleCoordinatesMissing(
			VisitVerificationService.CoordinatesMissingException exception) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.failure(
				new ApiError("PLACE_COORDINATES_MISSING", exception.getMessage()), requestId()));
	}

	/** 어느 항목이 범위를 벗어났는지 응답에 담는다. */
	@ExceptionHandler(PlaceReview.ScoreOutOfRangeException.class)
	public ResponseEntity<ApiResponse<Void>> handleScoreOutOfRange(PlaceReview.ScoreOutOfRangeException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("SCORE_OUT_OF_RANGE", exception.getMessage(), List.of(exception.field())), requestId()));
	}

	@ExceptionHandler(PlaceReview.EmptyReviewException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmptyReview(PlaceReview.EmptyReviewException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("EMPTY_REVIEW", exception.getMessage()), requestId()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
		List<String> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(field -> field.getField() + ": " + field.getDefaultMessage())
				.toList();
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값이 올바르지 않습니다.", fields), requestId()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값의 형식이 올바르지 않습니다.", List.of(exception.getName())),
				requestId()));
	}

	/** 파싱 오류 원문은 응답에 싣지 않는다 — 내부 구조가 새어 나간다. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다."), requestId()));
	}

	private ResponseEntity<ApiResponse<Void>> notFound(String message) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("PLACE_NOT_FOUND", message), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
