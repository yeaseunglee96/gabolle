package com.gabolle.backend.place.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.UnauthenticatedRequestException;
import com.gabolle.backend.place.service.PlaceNotFoundException;
import com.gabolle.backend.place.service.PlaceRequestException;

/**
 * 장소 조회의 오류를 HTTP 로 번역한다.
 *
 * <p>🔴 장소 API 패키지에만 건다. 전역 {@code @RestControllerAdvice} 로 만들면 인증 모듈의
 * {@code AuthExceptionHandler}(전역이고 {@code Exception} 까지 잡는다)와 우선순위가 얽혀서,
 * 어느 쪽이 잡을지가 클래스 이름 순서에 달리게 된다.
 *
 * <p>{@code assignableTypes} 로 컨트롤러를 나열하지 않고 패키지로 거는 이유는 유지보수다.
 * 컨트롤러가 늘 때마다 이 파일을 고쳐야 하면, 나중에 추가한 컨트롤러 하나가 조용히 빠진 채
 * 인증 모듈의 {@code INTERNAL_ERROR} 로 나가게 된다.
 *
 * <h2>🔴 {@code message} 에 무엇을 넣는가 — 여기서 trip 과 다르게 했다</h2>
 *
 * {@code TripExceptionHandler} 는 {@code message} 자리에 {@code "error.trip.validation"} 같은
 * 메시지 키를 넣고, {@code auth} 쪽은 한국어 문장을 넣는다. 둘이 갈려 있다.
 *
 * <p>프런트({@code src/api/client.ts})는 {@code error.message} 를 <b>그대로 화면에 띄운다.</b>
 * 그래서 키를 넣으면 사용자에게 {@code error.trip.validation} 이 보인다. 배포에서 실제로 도는
 * auth 쪽을 따라 <b>사람이 읽는 한국어 문장</b>을 넣는다. 다국어가 필요해지면 {@code ApiError} 에
 * {@code messageKey} 를 더하는 것이 맞고, 그건 이 티켓의 범위가 아니다.
 */
@RestControllerAdvice(basePackages = "com.gabolle.backend.place.api")
public class PlaceExceptionHandler {

	/** 요청 형식이 계약과 다르다. 어느 항목이 문제인지 함께 준다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
		List<String> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(field -> field.getField() + ": " + field.getDefaultMessage())
				.toList();

		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값이 올바르지 않습니다.", fields), requestId()));
	}

	/** 질의어가 비었거나, 개수 상한을 넘겼거나, 커서를 알아볼 수 없다. */
	@ExceptionHandler(PlaceRequestException.class)
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(PlaceRequestException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage(), exception.getFields()), requestId()));
	}

	@ExceptionHandler(PlaceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(PlaceNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(
				new ApiError("PLACE_NOT_FOUND", "장소를 찾을 수 없습니다."), requestId()));
	}

	/**
	 * 인증 정보가 없거나 알아볼 수 없다.
	 *
	 * <p>운영에서는 {@code SecurityConfig} 가 앞에서 막으므로 여기까지 오지 않는다. 필터 없이
	 * 컨트롤러만 띄우는 테스트와, 앞으로 permitAll 로 열릴 경로를 위해 둔다.
	 */
	@ExceptionHandler(UnauthenticatedRequestException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(UnauthenticatedRequestException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
