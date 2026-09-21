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
 * <p>{@code assignableTypes} 로 범위를 반드시 좁혀 둔다. Spring 은 여러 advice 빈 사이에서 "가장
 * 구체적인 타입" 을 전역으로 찾지 않고 적용 가능한 advice 를 먼저 정한 뒤 그 안에서 타입을
 * 맞추므로, 범위가 없으면 아래 {@code Exception.class} 캐치올이 다른 도메인의 더 구체적인
 * 핸들러를 가로채 그쪽 4xx 가 500 이 된다.
 *
 * <p>{@code AuthException} 은 여기 없다 — 다른 도메인도 그 예외를 공용으로 던지므로
 * {@link com.gabolle.backend.common.security.GlobalAuthExceptionHandler}(캐치올 없는 진짜 전역)가
 * 맡는다.
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
