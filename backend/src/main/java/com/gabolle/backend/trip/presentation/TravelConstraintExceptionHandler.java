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
 * 여행 조건 API 의 오류 응답.
 *
 * <p>{@code assignableTypes} 로 범위를 좁히고 {@code @Order} 를 명시한다 —
 * {@link SpendProfileExceptionHandler} 와 같은 이유다.
 */
@RestControllerAdvice(assignableTypes = TravelConstraintController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TravelConstraintExceptionHandler {

	/**
	 * 모르는 {@code answerStatus}, 또는 {@code SAVED} 인데 값이 없을 때 — 400.
	 *
	 * <p>{@code SAVED} 가 아닌데 값을 보낸 것은 오류가 아니다. 모달 내용을 든 채 「나중에」를
	 * 누르는 것이 정상 흐름이라, 그 값은 {@code TravelConstraintJpaEntity.update} 가 버린다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("TRAVEL_CONSTRAINT_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
