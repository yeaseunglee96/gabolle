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
 * {@link TripStoryController} 전용 오류 응답 — S15P21E201-829.
 *
 * <p>없는 여행과 그 여행의 회원이 아닌 요청자를 <b>같은 404</b> 로 답한다. 갈라 두면 응답
 * 차이만으로 "그 여행은 있다" 를 알아낼 수 있고, 그것이 곧 존재 사실의 유출이다
 * ({@code TripQueryService.get} 의 같은 규칙을 응답 층에서 지킨다).
 *
 * <p>코드와 문장은 여행 쪽 다른 경로와 같은 {@code TRIP_NOT_FOUND} · 「그 여행을 찾지
 * 못했어요.」 를 쓴다 — 같은 뜻에 다른 코드를 붙이면 화면이 경로마다 다르게 분기해야 한다.
 *
 * <p>🔴 <b>2026-09-18 정정.</b> 여기 원래 메시지 <b>키</b>({@code error.trip.notFound})를
 * 쓴다고 적혀 있었다. S15P21E201-1258 에서 문장으로 바꿨다 — 프런트가 그 키를 화면에
 * 그대로 띄워서 실기기에서 사용자에게 보였다.
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
