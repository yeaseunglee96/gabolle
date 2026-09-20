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
 * 계정 기본 취향 API 의 오류 응답.
 *
 * <p>이 advice 가 없으면 거절이 400 이 아니라 500 으로 나간다. Spring 은 예외를 advice
 * 단위로 훑는데 이 저장소의 advice 는 전부 {@code assignableTypes} 로 범위가 좁혀져 있어,
 * 이 컨트롤러를 맡는 것이 없으면 Spring Boot 기본 오류 처리로 떨어진다.
 *
 * <p>{@code assignableTypes} 와 {@code @Order} 를 명시하는 것은
 * {@link SpendProfileExceptionHandler} 와 같은 이유다.
 */
@RestControllerAdvice(assignableTypes = TastePreferencesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TastePreferencesExceptionHandler {

	/**
	 * 계정 기본값으로 둘 수 없는 차원 · 모르는 차원 이름 · 모르는 {@code answerStatus} ·
	 * status 와 값 유무 불일치 — 전부 400.
	 *
	 * <p>{@code PreferenceDimensions.UnknownPreferenceDimensionException} 도
	 * {@link IllegalArgumentException} 의 하위 타입이라 여기 같이 걸린다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("TASTE_PREFERENCES_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
