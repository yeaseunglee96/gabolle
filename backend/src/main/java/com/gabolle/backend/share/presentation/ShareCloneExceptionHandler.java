package com.gabolle.backend.share.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.share.application.ShareCloneService;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * ShareCloneController 전용. 복제 본문이 여행 생성 본문과 같으므로 오류 코드도 그쪽 것을
 * 그대로 쓴다 — 앱이 여행 생성 화면에서 이미 처리하는 코드다. 공유 주소 쪽 오류 코드는
 * 비로그인 조회와 같은 이름을 쓴다.
 */
@RestControllerAdvice(assignableTypes = ShareCloneController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShareCloneExceptionHandler {

	@ExceptionHandler(ShareCloneService.ShareLinkNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleLinkNotFound(ShareCloneService.ShareLinkNotFoundException e) {
		return failure(HttpStatus.NOT_FOUND, "SHARE_LINK_NOT_FOUND", e.getMessage());
	}

	@ExceptionHandler(ShareCloneService.ShareLinkExpiredException.class)
	public ResponseEntity<ApiResponse<Void>> handleExpired(ShareCloneService.ShareLinkExpiredException e) {
		return failure(HttpStatus.GONE, "SHARE_LINK_EXPIRED", e.getMessage());
	}

	@ExceptionHandler(ShareCloneService.SharedTripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripGone(ShareCloneService.SharedTripNotFoundException e) {
		return failure(HttpStatus.NOT_FOUND, "SHARED_TRIP_NOT_FOUND", e.getMessage());
	}

	@ExceptionHandler(ShareCloneService.SharedItineraryEmptyException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmpty(ShareCloneService.SharedItineraryEmptyException e) {
		return failure(HttpStatus.UNPROCESSABLE_ENTITY, "SHARED_ITINERARY_EMPTY", e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<String> fields = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.toList();
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_VALIDATION_FAILED", "입력한 조건 중 서버가 받지 못한 것이 있어요.", fields), requestId()));
	}

	/** 도메인 생성자·번역 계층이 거부한 것 — 종료일이 시작일보다 앞, 알 수 없는 severity 등. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_VALIDATION_FAILED", "입력한 조건 중 서버가 받지 못한 것이 있어요.", List.of(e.getMessage())),
				requestId()));
	}

	@ExceptionHandler(TripConstraint.SensitiveConstraintNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleSensitive(
			TripConstraint.SensitiveConstraintNotSupportedException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("SENSITIVE_CONSTRAINT_NOT_SUPPORTED", "건강·신념처럼 민감한 조건은 아직 받지 않아요.",
						List.of(e.type())), requestId()));
	}

	@ExceptionHandler(TripRepository.IdempotencyKeyConflictException.class)
	public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(
			TripRepository.IdempotencyKeyConflictException e) {
		return failure(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "같은 요청이 다른 내용으로 다시 왔어요. 잠시 후 다시 시도해 주세요.");
	}

	private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(ApiResponse.failure(new ApiError(code, message), requestId()));
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
