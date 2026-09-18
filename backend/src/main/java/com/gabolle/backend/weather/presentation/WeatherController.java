package com.gabolle.backend.weather.presentation;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.weather.application.WeatherService;
import com.gabolle.backend.weather.domain.WeatherForecastResult;
import com.gabolle.backend.weather.domain.WeatherQuery;
import com.gabolle.backend.weather.presentation.dto.WeatherForecastResponseDto;

/**
 * 지역·날짜로 기상청 단기예보를 답한다 — S15P21E201-366.
 *
 * <h2>🔴 인가는 "로그인한 사람이면 된다" 다</h2>
 * 이 자리에 남의 것/내 것 구분이 없다 — 좌표와 날짜는 부르는 쪽이 준 값이고 우리 자원이
 * 아니다. 그런데도 로그인을 요구하는 이유는 {@code RouteController}·{@code TranslateController}
 * 와 같다 — 우리 기상청 키로 남이 대신 호출을 돌리는 것(비용·호출 한도 소진)을 막기 위해서다.
 *
 * <p>{@code @Profile({"db","dev"})} 는 {@code tools} 패키지와 같다 — 이 컨트롤러가 요구하는
 * {@link WeatherService}(그리고 그 아래 벤더 포트·캐시 포트)가 이 두 프로필에서만 뜨므로,
 * 컨트롤러도 같이 묶지 않으면 프로필 없는 기본 컨텍스트 테스트
 * ({@code GabolleBackendApplicationTests})가 빈을 못 찾아 죽는다.
 */
@RestController
@RequestMapping("/api/v1/weather")
@Profile({ "db", "dev" })
public class WeatherController {

	private final WeatherService weatherService;

	public WeatherController(WeatherService weatherService) {
		this.weatherService = weatherService;
	}

	/**
	 * @throws IllegalArgumentException 좌표 범위를 벗어났거나, date 가 이 발표 회차의 예보
	 *         범위 밖이다 — 400
	 * @throws com.gabolle.backend.weather.application.WeatherVendorException 기상청 호출 실패 — 502
	 */
	@GetMapping
	public ApiResponse<WeatherForecastResponseDto> forecast(
			@RequestParam double lat,
			@RequestParam double lon,
			@RequestParam LocalDate date,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AuthenticatedUsers.requireId(authentication);

		WeatherForecastResult result = this.weatherService.getForecast(new WeatherQuery(lat, lon, date));

		return ApiResponse.success(WeatherForecastResponseDto.from(result), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
