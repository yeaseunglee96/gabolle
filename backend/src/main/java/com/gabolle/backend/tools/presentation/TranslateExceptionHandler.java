package com.gabolle.backend.tools.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.tools.application.TranslationVendorException;

/**
 * 번역 중계의 실패를 명확한 상태코드로 번역한다.
 *
 * <p>{@code assignableTypes} 로 {@link TranslateController} 에만 건다 — 다른 컨트롤러의 같은 예외
 * 타입까지 여기서 잡으면 그쪽 오류 코드가 통째로 바뀐다.
 *
 * <p>업체 호출 실패를 200 으로 숨기지 않는다. 화면이 502 를 보고 준비된 문장으로 넘어간다.
 */
@RestControllerAdvice(assignableTypes = TranslateController.class)
@Profile({ "db", "dev" })
public class TranslateExceptionHandler {

	/** 문장이 비었거나 너무 길거나, 방향을 모른다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRANSLATE_INVALID_REQUEST", exception.getMessage()), requestId()));
	}

	/** 번역 업체를 부르지 못했다 — 절대 200 으로 위장하지 않는다. */
	@ExceptionHandler(TranslationVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(TranslationVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
