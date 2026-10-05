package com.gabolle.backend.weather.presentation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 지역·날짜로 기상청 단기예보를 답한다. 로그인을 요구하지 않는다 — 좌표와 날짜는 부르는
 * 쪽이 준 값이라 남의 것/내 것 구분이 없고, 미리 받기가 캐시를 채우므로 요청 경로에
 * 기상청이 없다. 대신 익명은 캐시까지만 닿는다.
 *
 * @Profile 을 지우면 프로필 없는 기본 컨텍스트 테스트가 WeatherService 빈을 못 찾아 죽는다.
 */
@RestController
@RequestMapping("/api/v1/weather")
@Profile({ "db", "dev" })
public class WeatherController {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final WeatherService weatherService;
	/** 응답의 지금 기온(currentTemperature)을 고를 때 쓴다 — S15P21E201-1979. */
	private final Clock clock;

	public WeatherController(WeatherService weatherService, Clock clock) {
		this.weatherService = weatherService;
		this.clock = clock;
	}

	/**
	 * 좌표 범위 밖이거나 date 가 이 발표 회차의 예보 범위 밖이면 400,
	 * 기상청 호출이 실패하면 502 다.
	 */
	@GetMapping
	public ApiResponse<WeatherForecastResponseDto> forecast(
			@RequestParam double lat,
			@RequestParam double lon,
			@RequestParam LocalDate date,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		// 로그인한 사람은 캐시에 없으면 기상청을 부르고, 익명은 미리 받아 둔 것만 답한다.
		// 익명 출입증은 누구나 발급받을 수 있어서, 익명이 벤더를 부르게 두면 좌표를
		// 바꿔가며 우리 키의 호출 한도를 태울 수 있다.
		boolean signedIn = AuthenticatedUsers.optionalId(authentication).isPresent();
		WeatherQuery query = new WeatherQuery(lat, lon, date);

		WeatherForecastResult result = signedIn ? this.weatherService.getForecast(query)
				: this.weatherService.getForecastFromCache(query);

		return ApiResponse.success(WeatherForecastResponseDto.from(result, LocalDateTime.now(this.clock.withZone(KST))), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
