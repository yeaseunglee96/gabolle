package com.gabolle.backend.routepost;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;

/** {@link RoutePostController} 만의 오류 응답. 코드는 예외 메시지 그대로다. */
@RestControllerAdvice(assignableTypes = RoutePostController.class)
public class RoutePostExceptionHandler {

	@ExceptionHandler({ RoutePostExceptions.NotFoundException.class, RoutePostExceptions.SourceNotFoundException.class })
	public ResponseEntity<ApiResponse<Void>> notFound(RuntimeException e) {
		return fail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(RoutePostExceptions.ForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> forbidden(RuntimeException e) {
		return fail(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler(RoutePostExceptions.InvalidRouteException.class)
	public ResponseEntity<ApiResponse<Void>> invalid(RuntimeException e) {
		return fail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	private static ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, String code) {
		return ResponseEntity.status(status)
				.body(ApiResponse.failure(new ApiError(code, code, List.of()), "req_" + UUID.randomUUID()));
	}
}
