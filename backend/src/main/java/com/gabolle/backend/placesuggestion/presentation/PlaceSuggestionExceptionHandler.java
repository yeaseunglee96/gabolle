package com.gabolle.backend.placesuggestion.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService;

/**
 * 장소 제안의 오류 응답. 중복이면 {@code fields} 첫 칸에 이미 있는 장소 번호를 싣는다(대기 중인 제안이면 빈 목록) —
 * 화면이 「이미 있는 장소예요」에서 그 장소로 바로 보낸다.
 */
@RestControllerAdvice(assignableTypes = { PlaceSuggestionController.class, AdminPlaceSuggestionController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlaceSuggestionExceptionHandler {

	@ExceptionHandler(PlaceSuggestionService.InvalidSuggestionException.class)
	public ResponseEntity<ApiResponse<Void>> invalid(RuntimeException e) {
		return fail(HttpStatus.BAD_REQUEST, "PLACE_SUGGESTION_INVALID", e.getMessage(), List.of());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class })
	public ResponseEntity<ApiResponse<Void>> unreadable(Exception e) {
		return fail(HttpStatus.BAD_REQUEST, "PLACE_SUGGESTION_INVALID", "요청 값을 읽을 수 없습니다.", List.of());
	}

	@ExceptionHandler(PlaceSuggestionService.OutsideBusanException.class)
	public ResponseEntity<ApiResponse<Void>> outside(RuntimeException e) {
		return fail(HttpStatus.BAD_REQUEST, "PLACE_SUGGESTION_OUTSIDE_BUSAN", e.getMessage(), List.of("lat", "lng"));
	}

	@ExceptionHandler(PlaceSuggestionService.BlockedTextException.class)
	public ResponseEntity<ApiResponse<Void>> blocked(RuntimeException e) {
		return fail(HttpStatus.BAD_REQUEST, "PLACE_SUGGESTION_TEXT_BLOCKED", e.getMessage(), List.of());
	}

	@ExceptionHandler(PlaceSuggestionService.InvalidPhotoException.class)
	public ResponseEntity<ApiResponse<Void>> photo(RuntimeException e) {
		return fail(HttpStatus.BAD_REQUEST, "PLACE_SUGGESTION_PHOTO_INVALID", e.getMessage(), List.of("photoUrl"));
	}

	@ExceptionHandler(PlaceSuggestionService.RateLimitedException.class)
	public ResponseEntity<ApiResponse<Void>> limited(RuntimeException e) {
		return fail(HttpStatus.TOO_MANY_REQUESTS, "PLACE_SUGGESTION_RATE_LIMITED", e.getMessage(), List.of());
	}

	@ExceptionHandler(PlaceSuggestionService.DuplicateException.class)
	public ResponseEntity<ApiResponse<Void>> duplicate(PlaceSuggestionService.DuplicateException e) {
		UUID placeId = e.duplicate().placeId();
		return fail(HttpStatus.CONFLICT, "PLACE_SUGGESTION_DUPLICATE", e.getMessage(),
				placeId == null ? List.of() : List.of(placeId.toString()));
	}

	@ExceptionHandler(PlaceSuggestionService.SuggestionNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> notFound(RuntimeException e) {
		return fail(HttpStatus.NOT_FOUND, "PLACE_SUGGESTION_NOT_FOUND", e.getMessage(), List.of());
	}

	@ExceptionHandler(PlaceSuggestionService.AlreadyReviewedException.class)
	public ResponseEntity<ApiResponse<Void>> reviewed(RuntimeException e) {
		return fail(HttpStatus.CONFLICT, "PLACE_SUGGESTION_ALREADY_REVIEWED", e.getMessage(), List.of());
	}

	private static ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, String code, String message,
			List<String> fields) {
		return ResponseEntity.status(status)
				.body(ApiResponse.failure(new ApiError(code, message, fields), "req_" + UUID.randomUUID()));
	}
}
