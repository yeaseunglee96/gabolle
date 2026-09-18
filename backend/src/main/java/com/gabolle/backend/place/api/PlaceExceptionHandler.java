package com.gabolle.backend.place.api;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
 * <p>🔴 <b>패키지를 좁히는 것만으로는 부족하다.</b> 인증 모듈의 {@code AuthExceptionHandler} 는
 * 범위 제한이 없고 {@code Exception} 까지 잡는다. Spring 은 적용 가능한 advice 를 순회하다가
 * 그 예외를 처리할 메서드가 있는 <b>첫 번째</b> advice 에서 멈추는데, 둘 다 {@code @Order} 가
 * 없으면 순서가 컴포넌트 스캔 등록 순서로 정해진다 — 그러면 장소 404 가 인증 모듈의
 * {@code 500 INTERNAL_ERROR} 로 나간다. 그래서 {@link Ordered#HIGHEST_PRECEDENCE} 를 명시한다.
 *
 * <p>슬라이스 테스트로는 이 문제를 못 잡는다. {@code PlaceSliceApplication} 이 {@code common} 과
 * {@code place} 만 스캔해서 {@code AuthExceptionHandler} 가 컨텍스트에 없기 때문이다.
 *
 * <p>{@code assignableTypes} 로 컨트롤러를 나열하지 않고 패키지로 거는 이유는 유지보수다.
 * 컨트롤러가 늘 때마다 이 파일을 고쳐야 하면, 나중에 추가한 컨트롤러 하나가 조용히 빠진다.
 *
 * <h2>🔴 {@code message} 에는 <b>사람이 읽는 한국어 문장</b>을 넣는다</h2>
 *
 * 프런트({@code src/api/errorText.ts})가 {@code error.message} 를 화면에 띄우기 때문이다.
 * 다국어가 필요해지면 {@code ApiError} 에 {@code messageKey} 를 더하는 것이 맞고, 그때까지는
 * 모든 예외 처리기가 이 한 가지 방식을 쓴다.
 *
 * <p>🔴 <b>2026-09-18 정정.</b> 여기 원래 <i>「{@code TripExceptionHandler} 는 메시지 키를
 * 넣고 {@code auth} 는 문장을 넣는다 — 둘이 갈려 있다」</i> 고 적혀 있었다. <b>그 갈림은
 * S15P21E201-1258 에서 없앴다</b> — 여행 계열 16곳이 키를 넣고 있었고 전부 문장으로 바꿨다.
 * 낡은 설명을 지우지 않고 정정한 날짜와 함께 남긴다: 이 파일이 <b>그 갈림을 설명하던 자리</b>라,
 * 다음 사람이 「아직 갈려 있나」를 다시 재 보지 않게 하기 위해서다.
 */
@RestControllerAdvice(basePackages = "com.gabolle.backend.place.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
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

	/**
	 * 경로 변수나 질의 인자의 타입이 안 맞는다. 예: {@code /api/v1/places/not-a-uuid}, {@code ?lat=abc}.
	 *
	 * <p>🔴 이걸 안 잡으면 프레임워크 기본 응답이 나가서 {@code data} 도 {@code error} 도 없는
	 * 본문이 되고, 프런트 클라이언트는 그것을 {@code INVALID_RESPONSE} 로 읽는다.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 값의 형식이 올바르지 않습니다.",
						List.of(exception.getName())), requestId()));
	}

	/** 필수 질의 인자가 없다. 예: {@code /api/v1/origins} 에 {@code query} 누락. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
			MissingServletRequestParameterException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "필수 값이 빠졌습니다.",
						List.of(exception.getParameterName())), requestId()));
	}

	/** 본문 JSON 을 읽을 수 없다. 🔴 파싱 오류 원문을 응답에 싣지 않는다 — 내부 구조가 새어 나간다. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다."), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
