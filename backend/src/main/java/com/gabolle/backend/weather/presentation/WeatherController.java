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
 * <h2>🔴 인가 — 로그인은 이제 필요 없다 (정정 2026-09-15, S15P21E201-993)</h2>
 * 이 자리에 남의 것/내 것 구분이 없다 — 좌표와 날짜는 부르는 쪽이 준 값이고 우리 자원이
 * 아니다. <b>그런데도 로그인을 요구했던 이유</b>는 {@code RouteController}·
 * {@code TranslateController} 와 같았다 — 우리 기상청 키로 남이 대신 호출을 돌리는 것(비용·
 * 호출 한도 소진)을 막기 위해서다.
 *
 * <p>그 이유를 <b>구조로 없앴다.</b> {@code WeatherPrefetchScheduler} 가 부산 격자(실측 51칸)를
 * 미리 받아 캐시에 채우므로, 요청 경로에 기상청이 없다. 그래서 호출량이 사용자 수와 무관하게
 * 고정되고, 익명에게 열어도 한도를 태울 방법이 없다.
 *
 * <p>대신 <b>익명은 캐시까지만</b> 닿는다(아래 {@code forecast} 주석). 문턱을 없앤 자리에
 * 경계를 하나 남겨 둔 것이다 — 익명 출입증은 누구나 발급받을 수 있기 때문이다.
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

		// 🔴 S15P21E201-993 — 로그인하지 않은 사람도 읽는다. 다만 길이 갈린다.
		//
		//    로그인한 사람 : 캐시에 없으면 기상청을 부른다 (지금까지와 같다)
		//    익명         : 미리 받아 둔 것만 답한다. 기상청을 부르지 않는다
		//
		//    익명 쪽을 이렇게 가르는 이유가 이 클래스 맨 위 주석에 적힌 그 이유다 — 익명
		//    출입증은 누구나 발급받을 수 있어서, 이 길에서 벤더를 부르면 좌표를 바꿔가며
		//    우리 키의 호출 한도를 태울 수 있다. WeatherPrefetchScheduler 가 부산 격자를
		//    미리 채워 두므로 부산 안이라면 익명도 값을 받는다.
		boolean signedIn = AuthenticatedUsers.optionalId(authentication).isPresent();
		WeatherQuery query = new WeatherQuery(lat, lon, date);

		WeatherForecastResult result = signedIn ? this.weatherService.getForecast(query)
				: this.weatherService.getForecastFromCache(query);

		return ApiResponse.success(WeatherForecastResponseDto.from(result), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
