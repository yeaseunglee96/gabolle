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
 * 기상청 단기예보 조회의 실패를 명확한 상태코드로 번역한다 — S15P21E201-366.
 *
 * <p>🔴 {@code assignableTypes} 로 {@link WeatherController} 에만 건다 — 다른 컨트롤러의
 * 같은 예외 타입까지 여기서 잡으면 그쪽 오류 코드가 통째로 바뀐다({@code
 * TranslateExceptionHandler}·{@code RouteExceptionHandler} 와 같은 이유).
 *
 * <p>🔴 <b>기상청 호출 실패를 200 으로 숨기지 않는다.</b> {@link WeatherVendorException} 은
 * 502(Bad Gateway)로 내려간다 — 지어낸 값으로 대신 답하지 않는다는 이 티켓의 완료 기준이다.
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

	/** 기상청을 부르지 못했다 — 절대 200 으로 위장하지 않는다. */
	@ExceptionHandler(WeatherVendorException.class)
	public ResponseEntity<ApiResponse<Void>> handleVendorFailure(WeatherVendorException exception) {
		return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failure(
				new ApiError(exception.getCode(), exception.getMessage()), requestId()));
	}

	/**
	 * 로그인하지 않은 사람이 아직 안 받아 둔 지역·회차를 물었다 — S15P21E201-993.
	 *
	 * <p>🔴 <b>404 다. 401·503 이 아니다.</b>
	 *
	 * <ul>
	 * <li>401 이면 앱이 <b>출입증 만료</b>로 보고 다시 발급받아 재시도한다 — 몇 번을 받아도
	 * 같은 답이라 고리가 된다({@code StoryFeedService} 가 같은 이유로 같은 판단을 했다)</li>
	 * <li>503 이면 <b>서버가 아픈 것</b>처럼 보인다. 서버는 멀쩡하고, 그 지역을 안 받아 둔
	 * 것뿐이다</li>
	 * </ul>
	 *
	 * <p>"그 자리에 줄 것이 없다" 가 정확한 뜻이라 404 로 답한다. 화면은 이 코드를 보고 날씨
	 * 줄을 감추면 된다.
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
