package com.gabolle.backend.itinerary.presentation;

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
import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;

/**
 * {@link ItineraryJobController} 전용 오류 번역기.
 * {@code ItineraryExceptionHandler} 를 재사용하지 않는다. 그 클래스는
 * {@code assignableTypes = ItineraryEditController.class} 로 이미 좁혀져 있고(범위 없는 advice
 * 가 남의 예외를 가로챈 사고가 있었다), {@code assignableTypes} 에 컨트롤러를 추가하는 것
 * 자체가 "이 handler 는 이제 두 컨트롤러를 함께 본다" 는 결합을 만든다.
 * 코드·메시지·{@code fields} 문자열 모양은 {@code ItineraryExceptionHandler} 와 그대로 맞춘다 —
 * 앱이 {@code error.fields} 를 {@code /^latestVersion=/} 로 훑어 최신 판 번호를 뽑는 계약이 이
 * 경로에도 똑같이 적용된다.
 */
@RestControllerAdvice(assignableTypes = ItineraryJobController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ItineraryJobExceptionHandler {

	@ExceptionHandler(StaleItineraryVersionException.class)
	public ResponseEntity<ApiResponse<Void>> handleStaleVersion(StaleItineraryVersionException e) {
		List<String> fields = List.of(
				"latestVersion=" + e.latestVersion(),
				"attemptedBaseVersion=" + e.attemptedBaseVersion());

		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_VERSION_CONFLICT", "다른 변경이 먼저 반영됐습니다. 최신 일정을 불러와 다시 시도해 주세요.",
								fields),
						requestId()));
	}

	/** 재계산할 대상(fromItemId·dayIndex)이 둘 다 없다 — 400. */
	@ExceptionHandler(ItineraryJobController.MissingRecalculationTargetException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingTarget(
			ItineraryJobController.MissingRecalculationTargetException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_JOB_VALIDATION_FAILED", e.getMessage(), List.of("fromItemId", "dayIndex")),
						requestId()));
	}

	/** 바탕 판에 그 항목이 없다 — 404. */
	@ExceptionHandler(ItineraryRevision.ItemNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleItemNotFound(ItineraryRevision.ItemNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_ITEM_NOT_FOUND", "그 일정 항목을 찾을 수 없습니다.",
								List.of("itemId=" + e.itemKey())),
						requestId()));
	}

	/**
	 * 없는 일정이거나, 있어도 요청자가 그 일정이 속한 여행의 회원이 아니다.
	 * {@link ItineraryAccess#requireEditor} 가 이 예외를 던진다 — 존재를 감춘다.
	 */
	@ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessNotFound(
			ItineraryQueryController.ItineraryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	/** 회원이지만 VIEWER 라 편집 권한이 없다. 존재를 감출 필요가 없는 회원이라 404 가 아니다. */
	@ExceptionHandler(ItineraryAccess.ItineraryForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ItineraryAccess.ItineraryForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_FORBIDDEN", e.getMessage(), List.of("role=" + e.role())),
						requestId()));
	}

	/** {@code baseVersion} 등 필수 항목이 빠졌거나 모양이 틀렸다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<String> fields = e.getBindingResult().getFieldErrors().stream()
				.map((f) -> f.getField() + ": " + f.getDefaultMessage())
				.toList();

		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(
						new ApiError("ITINERARY_JOB_VALIDATION_FAILED", "요청 값을 확인해 주세요.", fields),
						requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
