package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 씀씀이 성향 API 의 오류 응답.
 *
 * <p>{@code assignableTypes} 로 범위를 좁히고 {@code @Order} 를 명시한다 — 안 좁히면 이
 * 컨트롤러의 예외가 엉뚱한 advice 에 먼저 잡힌다.
 */
@RestControllerAdvice(assignableTypes = SpendProfileController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpendProfileExceptionHandler {

	/** 모르는 {@code answerStatus}, 또는 status 와 값 유무 불일치(SELECTED 인데 값 없음) — 400. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("SPEND_PROFILE_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
