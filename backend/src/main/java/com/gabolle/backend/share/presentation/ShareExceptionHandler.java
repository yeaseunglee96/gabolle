package com.gabolle.backend.share.presentation;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.share.application.ShareLinkService;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * ShareLinkController·SharedItineraryController 전용 오류 번역기. assignableTypes 로 두
 * 컨트롤러만 좁히고 @Order 로 다른 advice 보다 먼저 보게 한다 — 범위 없는 advice 는 남의
 * 예외를 가로챈다.
 */
@RestControllerAdvice(assignableTypes = { ShareLinkController.class, SharedItineraryController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ShareExceptionHandler {

	/** 발급 경로 — 없는 여행이거나 요청자가 그 여행의 회원이 아니다. 둘을 구분해 응답하지 않는다. */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("TRIP_NOT_FOUND", "여행을 찾을 수 없습니다."), requestId()));
	}

	/** 발급 경로 — 회원이지만 OWNER 가 아니다. */
	@ExceptionHandler(ShareLinkService.ShareForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ShareLinkService.ShareForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.failure(new ApiError("TRIP_FORBIDDEN", e.getMessage()), requestId()));
	}

	/** 조회 경로 — 없는 표(token). */
	@ExceptionHandler(ShareLinkService.ShareLinkNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleShareLinkNotFound(ShareLinkService.ShareLinkNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("SHARE_LINK_NOT_FOUND", e.getMessage()), requestId()));
	}

	/** 조회 경로 — 표는 있으나 만료됐다. 404 가 아니라 410 이다. */
	@ExceptionHandler(ShareLinkService.ShareLinkExpiredException.class)
	public ResponseEntity<ApiResponse<Void>> handleExpired(ShareLinkService.ShareLinkExpiredException e) {
		return ResponseEntity.status(HttpStatus.GONE)
				.body(ApiResponse.failure(new ApiError("SHARE_LINK_EXPIRED", e.getMessage()), requestId()));
	}

	/** 조회 경로 — 표는 유효하나 가리키는 여행이 없거나 지워졌다. */
	@ExceptionHandler(ShareLinkService.SharedTripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleSharedTripNotFound(
			ShareLinkService.SharedTripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("SHARED_TRIP_NOT_FOUND", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
