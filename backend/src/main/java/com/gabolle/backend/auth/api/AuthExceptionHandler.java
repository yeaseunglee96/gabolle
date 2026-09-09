package com.gabolle.backend.auth.api;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * auth 컨트롤러({@link AuthController}·{@link EmailVerificationLinkController})만의 오류 번역.
 *
 * <p>🔴 2026-09-07 (S15P21E201-294) — {@code assignableTypes} 를 준다. 예전에는 스코프가
 * 없어서 <b>이 클래스가 사실상 앱 전역 예외 처리기</b>였다. {@code @ExceptionHandler(Exception.class)}
 * 캐치올이 다른 도메인(예: {@code RecommendationJobController})이 자기 컨트롤러 전용으로
 * 등록해 둔 더 구체적인 핸들러를 <b>가로챘다</b> — Spring 은 여러 {@code @ControllerAdvice} 빈
 * 사이에서 "가장 구체적인 타입" 을 전역으로 찾지 않고, 적용 가능한 advice 빈을 먼저 정한 뒤
 * 그 안에서만 타입을 맞춘다. 스코프 없는 이 advice 가 모든 컨트롤러에 "적용 가능"했으므로
 * 다른 곳의 {@code IllegalStateException} 핸들러가 아예 시도되지도 않고 여기 {@code
 * handleUnexpected} 로 떨어져 500 이 났다(이예승 님이 운영 로그로 확정, S15P21E201-294).
 *
 * <p>{@code AuthException} 은 이 클래스에서 뺐다 — 다른 도메인들이({@code
 * common.security.AuthenticatedUsers} 경유) 그 예외를 공용으로 던진다. 그건
 * {@link com.gabolle.backend.common.security.GlobalAuthExceptionHandler}(진짜 전역, 캐치올
 * 없음)가 대신 맡는다.
 */
@RestControllerAdvice(assignableTypes = { AuthController.class, EmailVerificationLinkController.class })
public class AuthExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<String> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", fields), requestId(request)));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 형식이 올바르지 않습니다."), requestId(request)));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception,
			HttpServletRequest request) {
		return ResponseEntity.status(409).body(ApiResponse.failure(
				new ApiError("CONFLICT", "요청한 데이터가 이미 존재하거나 현재 상태와 충돌합니다."), requestId(request)));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception,
			HttpServletRequest request) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값이 올바르지 않습니다."), requestId(request)));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
		String requestId = requestId(request);
		log.error("인증 요청 처리 중 예기치 않은 오류가 발생했습니다. requestId={}", requestId, exception);
		return ResponseEntity.internalServerError().body(ApiResponse.failure(
				new ApiError("INTERNAL_ERROR", "요청을 처리하지 못했습니다."), requestId));
	}

	private String requestId(HttpServletRequest request) {
		String requestId = request.getHeader("X-Request-Id");
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
