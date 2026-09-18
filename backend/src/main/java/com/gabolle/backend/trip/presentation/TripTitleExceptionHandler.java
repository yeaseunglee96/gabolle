package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.TripTitleService;

/**
 * 이름 바꾸기의 실패를 HTTP 로 옮긴다 — S15P21E201-1023.
 *
 * <p>{@code TripExceptionHandler} 는 {@code assignableTypes = TripController.class} 라
 * 이 컨트롤러까지 덮지 않는다. 그래서 같은 코드를 쓰되 자리를 따로 둔다 —
 * {@code TripCollaborationExceptionHandler}·{@code TripStoryExceptionHandler} 와 같다.
 */
@RestControllerAdvice(assignableTypes = TripTitleController.class)
public class TripTitleExceptionHandler {

	/**
	 * 없는 여행이거나, 요청자가 그 여행의 회원이 아니다. <b>둘을 구분해 답하지 않는다</b> —
	 * 구분하면 남의 여행 ID 를 넣어 보는 것만으로 «있는 여행인지» 를 알아낼 수 있다.
	 */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("TRIP_NOT_FOUND", "그 여행을 찾지 못했어요."), requestId()));
	}

	/**
	 * 보기 전용 동행자가 이름을 바꾸려 했다.
	 *
	 * <p>🔴 404 가 아니라 403 이다. 여기까지 온 사람은 <b>이미 그 여행을 보고 있다</b> —
	 * 404 로 답하면 방금 열어 본 여행이 없다는 말이 된다. 감출 것이 없는 자리다.
	 */
	@ExceptionHandler(TripTitleService.TitleChangeForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(TripTitleService.TitleChangeForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(
				new ApiError("TRIP_FORBIDDEN", e.getMessage()), requestId()));
	}

	/**
	 * 이름이 너무 길거나 줄바꿈이 들어 있다 ({@code Trip.rename} 이 던진다).
	 *
	 * <p>메시지를 그대로 싣는다 — 「60자를 넘을 수 없다: 74자」처럼 <b>무엇이 문제인지</b>
	 * 를 말한다. 번역 키로 바꾸면 그 숫자가 사라진다.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("TRIP_TITLE_INVALID", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
