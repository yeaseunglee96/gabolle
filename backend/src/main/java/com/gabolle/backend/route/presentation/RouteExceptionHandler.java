package com.gabolle.backend.route.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/**
 * 경로 조회의 잘못된 요청을 400 으로 번역한다.
 *
 * <p>🔴 {@code assignableTypes} 로 {@link RouteController} 에만 건다 — 다른 컨트롤러의
 * {@code IllegalArgumentException} 까지 여기서 잡으면 그쪽 오류 코드가 통째로 바뀐다.
 *
 * <p>🔴 <b>어느 칸이 문제인지 응답에 담는다.</b> "잘못된 요청" 만 돌려주면 부르는 쪽이
 * 좌표를 잘못 넣었는지 이동수단을 잘못 넣었는지 알 수 없다 — 이 저장소의 다른 처리기들이
 * 같은 이유로 필드 이름을 싣는다.
 */
@RestControllerAdvice(assignableTypes = RouteController.class)
public class RouteExceptionHandler {

	/** 좌표 범위를 벗어났거나 모르는 이동수단이다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("ROUTE_INVALID_REQUEST", exception.getMessage()), requestId()));
	}

	/** 숫자 자리에 숫자가 아닌 것이 왔다 — {@code originLat=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("ROUTE_INVALID_REQUEST", "값의 형식이 올바르지 않습니다.",
						List.of(exception.getName())),
				requestId()));
	}

	/** 필수 칸이 빠졌다. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissing(MissingServletRequestParameterException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("ROUTE_INVALID_REQUEST", "필수 값이 빠졌습니다.",
						List.of(exception.getParameterName())),
				requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
