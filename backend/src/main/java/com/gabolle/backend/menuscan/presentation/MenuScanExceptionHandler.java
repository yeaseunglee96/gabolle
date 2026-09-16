package com.gabolle.backend.menuscan.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.menuscan.adapter.GmsMenuReader;
import com.gabolle.backend.menuscan.application.MenuScanRateLimiter;
import com.gabolle.backend.menuscan.application.MenuScanService;

/**
 * 메뉴판 읽기의 실패를 HTTP 로 번역한다 — S15P21E201-1025.
 *
 * <h2>🔴 어떤 실패도 «빈 결과» 로 바꾸지 않는다</h2>
 *
 * 이 API 에서 빈 결과는 사용자에게 <b>「알레르기 낱말이 없구나」</b> 로 읽힌다. 그래서
 * 여기 있는 모든 자리가 <b>실패를 실패로</b> 답한다. 다른 API 라면 «조용히 넘어가도 되는»
 * 것들도 여기서는 안 된다 — 사람이 다칠 수 있는 자리다.
 *
 * <p>{@code message} 는 번역 키가 아니라 <b>사람이 읽는 문장</b>이다. 프론트가 이 값을
 * 그대로 화면에 띄우는 것이 이 저장소에서 이미 실측됐다.
 */
@RestControllerAdvice(assignableTypes = MenuScanController.class)
public class MenuScanExceptionHandler {

	/** 하루·분 한도를 넘었다. 화면은 「오늘 쓸 수 있는 횟수를 다 썼어요」로 그린다. */
	@ExceptionHandler(MenuScanRateLimiter.TooManyScansException.class)
	public ResponseEntity<ApiResponse<Void>> handleTooMany(MenuScanRateLimiter.TooManyScansException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.failure(
				new ApiError("MENU_SCAN_RATE_LIMITED", e.getMessage()), requestId()));
	}

	/**
	 * 설정이 없어 지금은 못 읽는다.
	 *
	 * <p>🔴 <b>503 이다.</b> 200 + 빈 목록으로 답하면 «읽었는데 못 찾았다» 와 구분되지 않는다.
	 */
	@ExceptionHandler(MenuScanService.MenuScanUnavailableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnavailable(MenuScanService.MenuScanUnavailableException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.failure(
				new ApiError("MENU_SCAN_UNAVAILABLE", "메뉴판 읽기를 지금은 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요."),
				requestId()));
	}

    /** 모델이 답을 줬는데 알아볼 수 없거나, 중계가 실패했다. 역시 빈 결과로 바꾸지 않는다. */
	@ExceptionHandler(GmsMenuReader.MenuReadFailedException.class)
	public ResponseEntity<ApiResponse<Void>> handleReadFailed(GmsMenuReader.MenuReadFailedException e) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.failure(
				new ApiError("MENU_SCAN_FAILED", "사진에서 글자를 읽지 못했어요. 더 밝은 곳에서 다시 찍어 주세요."),
				requestId()));
	}

	/** 사진이 없거나·너무 크거나·이미지가 아니다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalid(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("MENU_SCAN_INVALID_IMAGE", e.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
