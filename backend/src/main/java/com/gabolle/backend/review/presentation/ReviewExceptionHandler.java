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
 * <p>🔴 {@code assignableTypes} 로 컨트롤러 둘을 명시하고 {@link Ordered#HIGHEST_PRECEDENCE} 를
 * 준다. 인증 모듈의 {@code GlobalAuthExceptionHandler} 는 범위 제한이 없어 {@code @Order} 가
 * 없으면 이 클래스보다 먼저 걸릴 수 있고, 그러면 여기서 낼 404·422 가 인증 모듈의 500 으로
 * 나간다 — {@code PlaceExceptionHandler} 가 이미 겪은 것과 같은 함정이다.
 *
 * <p>{@code AuthException} 은 여기서 다루지 않는다 — {@code GlobalAuthExceptionHandler}(범위
 * 없는 전역)가 이미 처리한다. 여기 또 만들면 어느 쪽이 먼저 걸릴지가 등록 순서에 좌우된다.
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

	/** 장소에 좌표가 없어 거리를 잴 수 없다. 요청 모양은 맞으므로 400 이 아니라 422 다. */
	@ExceptionHandler(VisitVerificationService.CoordinatesMissingException.class)
	public ResponseEntity<ApiResponse<Void>> handleCoordinatesMissing(
			VisitVerificationService.CoordinatesMissingException exception) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.failure(
				new ApiError("PLACE_COORDINATES_MISSING", exception.getMessage()), requestId()));
	}

	/** 점수가 1~5 밖이다. 어느 항목인지 응답에 담는다. */
	@ExceptionHandler(PlaceReview.ScoreOutOfRangeException.class)
	public ResponseEntity<ApiResponse<Void>> handleScoreOutOfRange(PlaceReview.ScoreOutOfRangeException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("SCORE_OUT_OF_RANGE", exception.getMessage(), List.of(exception.field())), requestId()));
	}

	/** 점수도 글도 없다. */
	@ExceptionHandler(PlaceReview.EmptyReviewException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmptyReview(PlaceReview.EmptyReviewException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("EMPTY_REVIEW", exception.getMessage()), requestId()));
	}

	/** 요청 형식이 계약과 다르다(Bean Validation) — 예: 좌표 범위 밖, 정확도 음수. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
		List<String> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(field -> field.getField() + ": " + field.getDefaultMessage())
				.toList();
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값이 올바르지 않습니다.", fields), requestId()));
	}

	/** 경로 변수 타입이 안 맞는다. 예: {@code /api/v1/places/not-a-uuid/reviews}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값의 형식이 올바르지 않습니다.", List.of(exception.getName())),
				requestId()));
	}

	/** 본문 JSON 을 읽을 수 없다. 🔴 파싱 오류 원문을 응답에 싣지 않는다 — 내부 구조가 새어 나간다. */
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
