package com.gabolle.backend.notification.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 토큰 등록의 잘못된 요청을 400 으로 번역한다.
 *
 * <p>{@code assignableTypes} 로 {@link PushTokenController} 에만 건다 — 다른 컨트롤러의 같은 예외까지
 * 여기서 잡으면 그쪽 오류 코드가 통째로 바뀐다(이 저장소의 다른 처리기와 같은 이유).
 */
@RestControllerAdvice(assignableTypes = PushTokenController.class)
@Profile({ "db", "dev" })
public class PushTokenExceptionHandler {

	/** 토큰이 비었거나 너무 길거나, 갈래가 ios·android 가 아니다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("PUSH_TOKEN_INVALID_REQUEST", exception.getMessage()), "req_" + UUID.randomUUID()));
	}
}
