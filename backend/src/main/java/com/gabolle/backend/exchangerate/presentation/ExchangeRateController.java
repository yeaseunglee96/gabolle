package com.gabolle.backend.exchangerate.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.exchangerate.application.ExchangeRateService;
import com.gabolle.backend.exchangerate.domain.ExchangeRatesResult;
import com.gabolle.backend.exchangerate.presentation.dto.ExchangeRatesResponseDto;

/**
 * 오늘의 환율을 답한다 — S15P21E201-1079.
 *
 * <h2>🔴 인가는 "로그인한 사람이면 된다" 다</h2>
 * {@code WeatherController}와 같은 이유 — 이 자리에 남의 것/내 것 구분이 없다. 로그인을
 * 요구하는 것은 우리 인증키로 남이 대신 호출을 돌리는 것(하루 1000회 한도 소진)을 막기
 * 위해서다.
 *
 * <p>{@code @Profile({"db","dev"})}는 {@code weather} 패키지와 같은 이유다.
 */
@RestController
@RequestMapping("/api/v1/exchange-rates")
@Profile({ "db", "dev" })
public class ExchangeRateController {

	private final ExchangeRateService exchangeRateService;

	public ExchangeRateController(ExchangeRateService exchangeRateService) {
		this.exchangeRateService = exchangeRateService;
	}

	/** @throws com.gabolle.backend.exchangerate.application.ExchangeRateVendorException 벤더 호출 실패 — 502 */
	@GetMapping
	public ApiResponse<ExchangeRatesResponseDto> rates(
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AuthenticatedUsers.requireId(authentication);

		ExchangeRatesResult result = this.exchangeRateService.getLatestRates();

		return ApiResponse.success(ExchangeRatesResponseDto.from(result), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
