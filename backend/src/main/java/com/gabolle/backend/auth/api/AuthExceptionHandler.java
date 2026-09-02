package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class AuthExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException exception, HttpServletRequest request) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId(request)));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		List<String> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", fields), requestId(request)));
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
