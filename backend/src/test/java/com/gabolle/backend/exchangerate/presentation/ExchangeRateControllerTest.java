package com.gabolle.backend.exchangerate.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.exchangerate.application.ExchangeRateService;
import com.gabolle.backend.exchangerate.application.ExchangeRateVendorException;
import com.gabolle.backend.exchangerate.application.ExchangeRateVendorPort;

import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /api/v1/exchange-rates}의 HTTP 경계 — S15P21E201-1079.
 *
 * <p>{@code WeatherControllerTest}와 같은 방식으로 컨트롤러+예외 처리기만 세워 HTTP 계약을
 * 잰다.
 */
class ExchangeRateControllerTest {

	private static final String SAMPLE_JSON = """
			[{"result":1,"cur_unit":"USD","cur_nm":"미국 달러","deal_bas_r":"1,378.60","ttb":"1,364.81","tts":"1,392.39"}]""";

	private MockMvc mockMvc;
	private StubVendor vendor;

	@BeforeEach
	void setUp() {
		this.vendor = new StubVendor();
		Clock clock = Clock.fixed(Instant.parse("2026-09-16T02:00:00Z"), ZoneOffset.UTC);
		ExchangeRateService service = new ExchangeRateService(this.vendor, clock, new ObjectMapper());

		this.mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeRateController(service))
				.setControllerAdvice(new ExchangeRateExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("부르면 오늘의 환율이 돌아온다")
	void ratesSucceed() throws Exception {
		this.mockMvc.perform(get("/api/v1/exchange-rates").principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.rates[0].currencyCode").value("USD"))
				.andExpect(jsonPath("$.data.rates[0].currencyName").value("미국 달러"))
				.andExpect(jsonPath("$.data.rates[0].baseRate").value(1378.60));
	}

	@Test
	@DisplayName("🔴 벤더 호출이 실패하면 502 이고 응답에 실패가 분명히 담긴다 — 200 으로 숨기지 않는다")
	void vendorFailureIsNotHiddenAs200() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(get("/api/v1/exchange-rates").principal(asUser()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("EXCHANGE_RATE_VENDOR_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	private static final class StubVendor implements ExchangeRateVendorPort {

		boolean shouldFail = false;

		@Override
		public String fetchRatesJson(LocalDate searchDate) {
			if (this.shouldFail) {
				throw new ExchangeRateVendorException("EXCHANGE_RATE_VENDOR_UNAVAILABLE", "환율 조회 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return SAMPLE_JSON;
		}

		@Override
		public String providerName() {
			return "STUB_VENDOR";
		}
	}
}
