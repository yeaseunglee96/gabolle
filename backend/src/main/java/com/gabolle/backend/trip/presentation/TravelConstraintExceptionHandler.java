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
 * 여행 조건 API 의 오류 응답 — S15P21E201-1231.
 *
 * <p>🔴 {@code assignableTypes} 로 범위를 {@link TravelConstraintController} 하나로 좁히고
 * {@code @Order} 를 명시한다 — {@link SpendProfileExceptionHandler} 와 같은 이유다. 이 저장소는
 * 예외 처리기 범위·순서를 안 좁혀서 엉뚱한 advice 에 잡히는 사고를 이미 여러 번 겪었다.
 */
@RestControllerAdvice(assignableTypes = TravelConstraintController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TravelConstraintExceptionHandler {

	/**
	 * {@code answerStatus} 를 모르거나, {@code SAVED} 인데 값이 없을 때 — 400.
	 *
	 * <p>🔴 <b>{@code SAVED} 가 아닌데 값을 보낸 것은 오류가 아니다.</b> 화면이 모달에 적힌
	 * 내용을 들고 있다가 「나중에」를 누르는 것이 정상 흐름이라, 그때 400 을 주면 앱이
	 * 사용자에게 오류를 보여주게 된다. 그 값은 조용히 버린다 —
	 * {@code TravelConstraintJpaEntity.update} 가 그 자리다.
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
