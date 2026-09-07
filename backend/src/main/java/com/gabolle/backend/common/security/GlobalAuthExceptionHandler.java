package com.gabolle.backend.common.security;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * {@link AuthException} 을 <b>어느 컨트롤러에서 던져도</b> 401/403 등으로 번역한다 — S15P21E201-294.
 *
 * <p>{@link AuthenticatedUsers}(이 클래스와 같은 패키지)가 그 예외를 던지고, 여러 도메인
 * 컨트롤러(TripController·ItineraryEditController·RecommendationJobController 등)가
 * {@code AuthenticatedUsers.requireId(authentication)} 를 통해 그것을 간접적으로 쓴다.
 * 그래서 이 advice 는 진짜로 스코프가 없어야 한다 — {@code assignableTypes} 를 주면
 * auth 패키지 밖 컨트롤러의 인증 실패가 500 으로 샌다.
 *
 * <p>🔴 <b>그래서 여기에는 캐치올을 두지 않는다.</b> {@code auth.api.AuthExceptionHandler}
 * 가 예전에 스코프 없이 {@code @ExceptionHandler(Exception.class)} 를 가졌던 것이 이번
 * 버그의 원인이었다({@code AuthExceptionHandler} 의 2026-09-07 javadoc 참고) — 전역
 * advice 에 넓은 타입(특히 {@code Exception}·{@code RuntimeException})을 걸면 다른
 * 도메인의 더 구체적인 핸들러가 시도되지도 못한다. 이 클래스는 딱 {@link AuthException}
 * 하나만 잡는다.
 */
@RestControllerAdvice
public class GlobalAuthExceptionHandler {

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException exception, HttpServletRequest request) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId(request)));
	}

	private String requestId(HttpServletRequest request) {
		String requestId = request.getHeader("X-Request-Id");
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
