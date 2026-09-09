package com.gabolle.backend.event.presentation;

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
 * 지표 조회의 오류 응답 — S15P21E201-160.
 *
 * <p>🔴 {@code assignableTypes} 로 범위를 {@link AnalyticsController} 하나로 좁히고
 * {@code @Order} 를 명시한다 — {@link EventIngestExceptionHandler} 와 같은 이유다. 이
 * 저장소는 예외 처리기 범위·순서를 안 좁혀서 엉뚱한 advice 에 잡히는 사고를 이미 여러 번
 * 겪었다({@code INC-AUTH-007} 등). 범위를 안 좁히면 이 advice 가 다른 컨트롤러의 예외까지
 * 가로챌 수 있고, 좁혀도 순서가 없으면 다른 넓은 advice 에 먼저 잡힐 수 있다.
 */
@RestControllerAdvice(assignableTypes = AnalyticsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalyticsExceptionHandler {

	/**
	 * {@code from} 이 {@code to} 보다 뒤거나 같다 — 400.
	 *
	 * <p>이 처리기가 없으면 응용 계층({@code AnalyticsQueryService}) 의 범위 검사가 던지는
	 * {@link IllegalArgumentException} 이 500 {@code INTERNAL_ERROR} 로 나간다. 잘못된
	 * 조회 범위는 클라이언트가 고칠 수 있는 것이라 400 이 맞다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidRange(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.failure(new ApiError("INVALID_KPI_RANGE", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
