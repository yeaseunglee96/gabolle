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
 * 씀씀이 성향 API 의 오류 응답 — S15P21E201-709.
 *
 * <p>🔴 {@code assignableTypes} 로 범위를 {@link SpendProfileController} 하나로 좁히고
 * {@code @Order} 를 명시한다 — {@code EventIngestExceptionHandler}·{@code
 * AnalyticsExceptionHandler} 와 같은 이유다. 이 저장소는 예외 처리기 범위·순서를 안 좁혀서
 * 엉뚱한 advice 에 잡히는 사고를 이미 여러 번 겪었다.
 */
@RestControllerAdvice(assignableTypes = SpendProfileController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpendProfileExceptionHandler {

	/**
	 * {@code answerStatus} 를 모르거나, {@code status} 와 값 유무가 안 맞을 때(예: SELECTED
	 * 인데 값이 없음) — 400.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("SPEND_PROFILE_REJECTED", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
