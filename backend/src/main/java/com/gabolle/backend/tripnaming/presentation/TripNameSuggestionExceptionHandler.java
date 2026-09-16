package com.gabolle.backend.tripnaming.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 이름 짓기의 실패를 HTTP 로 옮긴다 — S15P21E201-1025.
 *
 * <p>🔴 <b>여기 자리가 하나뿐인 것이 설계다.</b> 모델이 죽었을 때·키가 없을 때·지어낸
 * 이름이 전부 버려졌을 때는 <b>실패가 아니라</b> 템플릿 이름으로 200 이 나간다. 이름을
 * 못 지은 것은 사람을 다치게 하지 않기 때문이다 — 응답의 {@code source} 가 그것이 모델이
 * 지은 것이 아님을 말한다.
 *
 * <p>그래서 남는 실패는 <b>「그 여행을 볼 수 없다」</b> 하나뿐이다.
 */
@RestControllerAdvice(assignableTypes = TripNameSuggestionController.class)
public class TripNameSuggestionExceptionHandler {

	/** 없는 여행이거나 요청자가 그 여행의 회원이 아니다. 둘을 구분해 답하지 않는다. */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_NOT_FOUND", "error.trip.notFound"), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
