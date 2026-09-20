package com.gabolle.backend.weather.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.weather.application.WeatherService;
import com.gabolle.backend.weather.application.WeatherVendorException;
import com.gabolle.backend.weather.application.WeatherVendorPort;
import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.KmaBaseTime;
import com.gabolle.backend.weather.domain.WeatherForecastCacheRepository;

import tools.jackson.databind.ObjectMapper;

/** {@code GET /api/v1/weather} 의 HTTP 경계. */
class WeatherControllerTest {

	private static final Instant NOW = Instant.parse("2026-09-10T01:00:00Z");

	private static final String SAMPLE_JSON = """
			{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"items":{"item":[
			{"category":"TMP","fcstDate":"20260910","fcstTime":"1200","fcstValue":"25"},
			{"category":"POP","fcstDate":"20260910","fcstTime":"1200","fcstValue":"20"},
			{"category":"SKY","fcstDate":"20260910","fcstTime":"1200","fcstValue":"1"}
			]}}}}""";

	private MockMvc mockMvc;
	private StubVendor vendor;

	@BeforeEach
	void setUp() {
		this.vendor = new StubVendor();
		WeatherForecastCacheRepository cacheRepository = new InMemoryCacheRepository();
		WeatherProperties properties = new WeatherProperties();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		WeatherService service = new WeatherService(this.vendor, cacheRepository, properties, clock,
				new ObjectMapper());

		this.mockMvc = MockMvcBuilders.standaloneSetup(new WeatherController(service))
				.setControllerAdvice(new WeatherExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("부산 좌표로 부르면 벤더 응답에서 나온 기온·강수확률·하늘상태가 돌아온다")
	void forecastSucceedsForBusanCoordinates() throws Exception {
		this.mockMvc.perform(get("/api/v1/weather")
						.param("lat", "35.1796")
						.param("lon", "129.0756")
						.param("date", "2026-09-10")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nx").value(98))
				.andExpect(jsonPath("$.data.ny").value(76))
				.andExpect(jsonPath("$.data.forecast.minTemperature").value(25.0))
				.andExpect(jsonPath("$.data.forecast.precipitationProbability").value(20))
				.andExpect(jsonPath("$.data.forecast.skyCondition").value("CLEAR"));
	}

	@Test
	@DisplayName("🔴 벤더 호출이 실패하면 502 이고 응답에 실패가 분명히 담긴다 — 200 으로 숨기지 않는다")
	void vendorFailureIsNotHiddenAs200() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(get("/api/v1/weather")
						.param("lat", "35.1796")
						.param("lon", "129.0756")
						.param("date", "2026-09-10")
						.principal(asUser()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("WEATHER_VENDOR_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("좌표 범위를 벗어나면 400 이다")
	void outOfRangeCoordinateIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/weather")
						.param("lat", "999")
						.param("lon", "129.0756")
						.param("date", "2026-09-10")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("WEATHER_INVALID_REQUEST"));
	}

	@Test
	@DisplayName("필수 값이 빠지면 400 이다")
	void missingParameterIsRejected() throws Exception {
		this.mockMvc.perform(get("/api/v1/weather")
						.param("lat", "35.1796")
						.param("lon", "129.0756")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("WEATHER_INVALID_REQUEST"));
	}

	private static final class StubVendor implements WeatherVendorPort {

		boolean shouldFail = false;

		@Override
		public String fetchForecastJson(int nx, int ny, KmaBaseTime baseTime) {
			if (this.shouldFail) {
				throw new WeatherVendorException("WEATHER_VENDOR_UNAVAILABLE", "기상청 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return SAMPLE_JSON;
		}

		@Override
		public String providerName() {
			return "STUB_VENDOR";
		}
	}

	private static final class InMemoryCacheRepository implements WeatherForecastCacheRepository {

		private final Map<String, String> store = new HashMap<>();

		@Override
		public Optional<String> findFreshForecastJson(String cacheKey, Instant now) {
			return Optional.ofNullable(this.store.get(cacheKey));
		}

		@Override
		public void save(String cacheKey, String forecastJson, Instant now, Instant expiresAt) {
			this.store.put(cacheKey, forecastJson);
		}
	}
}
