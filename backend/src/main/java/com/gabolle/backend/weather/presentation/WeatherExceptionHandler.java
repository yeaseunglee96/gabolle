package com.gabolle.backend.weather.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.weather.application.WeatherService;
import com.gabolle.backend.weather.application.WeatherVendorException;

/**
 * 기상청 단기예보 조회의 실패를 상태코드로 번역한다. assignableTypes 로 WeatherController
 * 에만 건다 — 범위를 넓히면 다른 컨트롤러의 같은 예외까지 이 오류 코드로 바뀐다.
 *
 * 기상청 호출 실패는 502 다. 200 으로 숨기거나 지어낸 값으로 대신 답하지 않는다.
 */
@RestControllerAdvice(assignableTypes = WeatherController.class)
@Profile({ "db", "dev" })
public class WeatherExceptionHandler {

	/** 좌표 범위를 벗어났거나, date 가 이 발표 회차의 예보 범위 밖이다. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("WEATHER_INVALID_REQUEST", exception.getMessage()), requestId()));
	}

	/** 숫자·날짜 자리에 그 형식이 아닌 것이 왔다 — {@code date=abc}. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("WEATHER_INVALID_REQUEST", "값의 형식이 올바르지 않습니다.",
						List.of(exception.getName())),
				requestId()));
	}

	/** 필수 칸(lat·lon·date)이 빠졌다. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissing(MissingServletRequestParameterException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("WEATHER_INVALID_REQUEST", "필수 값이 빠졌습니다.",
						List.of(exception.getParameterName())),
				requestId()));
	}

	/** 기상청을 부르지 못했다. */
	@ExceptionHandler(WeatherVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(WeatherVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	/**
	 * 로그인하지 않은 사람이 아직 안 받아 둔 지역·회차를 물었다. 404 이지 401·503 이 아니다 —
	 * 401 이면 앱이 출입증 만료로 보고 재발급·재시도를 되풀이하고, 503 이면 서버가 아픈
	 * 것처럼 보인다. 화면은 이 코드를 보고 날씨 줄을 감추면 된다.
	 */
	@ExceptionHandler(WeatherService.ForecastNotPreparedException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotPrepared(WeatherService.ForecastNotPreparedException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("WEATHER_NOT_PREPARED", exception.getMessage()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
