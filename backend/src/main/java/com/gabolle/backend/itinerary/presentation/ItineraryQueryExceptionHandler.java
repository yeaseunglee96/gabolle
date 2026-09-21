package com.gabolle.backend.itinerary.presentation;

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
 * {@link ItineraryQueryController} 전용 오류 번역기.
 * 기존 {@code ItineraryExceptionHandler} 를 넓히지 않는다. 범위 없는 advice 가 남의 예외를
 * 가로챈 사고가 이미 있었다. {@code assignableTypes} 로 이 컨트롤러 하나만 좁히고
 * {@code @Order} 로 다른 advice 보다 먼저 보게 한다.
 * {@code message} 는 한국어 문장이다 — 프론트가 {@code error.message} 를 그대로 화면에 띄운다.
 */
@RestControllerAdvice(assignableTypes = ItineraryQueryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ItineraryQueryExceptionHandler {

	/** 없는 일정이거나 요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘 다 404. */
	@ExceptionHandler(ItineraryQueryController.ItineraryNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(
			ItineraryQueryController.ItineraryNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("ITINERARY_NOT_FOUND", "일정을 찾을 수 없습니다."), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
