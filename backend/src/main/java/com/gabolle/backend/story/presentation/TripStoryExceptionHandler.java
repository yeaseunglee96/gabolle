package com.gabolle.backend.story.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * {@link TripStoryController} 전용 오류 응답.
 *
 * <p>없는 여행과 그 여행의 회원이 아닌 요청자를 같은 404 로 답한다 — 갈라 두면 응답 차이만으로 「그 여행은
 * 있다」를 알아낼 수 있다. 코드와 문장은 여행 쪽 다른 경로와 같은 {@code TRIP_NOT_FOUND} 를 쓴다.
 *
 * <p>메시지 자리에는 키가 아니라 문장을 넣는다 — 프런트가 받은 값을 화면에 그대로 띄운다.
 */
@RestControllerAdvice(assignableTypes = TripStoryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TripStoryExceptionHandler {

	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("TRIP_NOT_FOUND", "그 여행을 찾지 못했어요."), requestId()));
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
