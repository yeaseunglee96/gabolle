package com.gabolle.backend.recommendation.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.recommendation.application.RecommendationBusyException;
import com.gabolle.backend.recommendation.application.RecommendationCodes;

/**
 * 추천 실행기가 꽉 차 작업을 못 받았다 → 503 · 우리 봉투 · 사용자 문장 (S15P21E201-1685).
 *
 * <p>범위를 두지 않는다 — 추천 작업은 추천 만들기뿐 아니라 일정 다시 짜기({@code ItineraryJobController})·공유 여행
 * 가져오기에서도 만든다. 순서를 맨 앞에 두는 것은 그 컨트롤러들의 범위 있는 핸들러가 넓은 타입({@code RuntimeException}
 * 등)으로 이 예외를 먼저 가로채지 않게 하려는 것이다. 이 타입 하나만 잡으므로 다른 오류와 겹치지 않는다.
 *
 * <p>작업은 이미 「실패 · 다시 시도 가능」으로 저장돼 있다({@code RecommendationJobRunner}). 문장은 다른 도메인 오류처럼
 * 한국어뿐이다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RecommendationBusyExceptionHandler {

	static final String MESSAGE = "지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.";

	@ExceptionHandler(RecommendationBusyException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusy(RecommendationBusyException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.failure(
				new ApiError(RecommendationCodes.ERROR_SERVER_BUSY, MESSAGE), "req_" + UUID.randomUUID()));
	}
}
