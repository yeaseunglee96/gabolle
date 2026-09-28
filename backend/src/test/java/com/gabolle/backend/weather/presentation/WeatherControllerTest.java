package com.gabolle.backend.weather.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

import tools.jackson.databind.JsonNode;
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

	// 시간별 예보(hourly) — S15P21E201-1534

	/**
	 * 1시간 간격 두 칸(09·10시) + 12시 한 칸 + 다른 날짜 한 칸. 10시에는 기온이 없고, 12시에는
	 * 기온만 있다 — 빠진 값이 null 로 나오는지 본다. TMN·TMX 가 있어 하루 요약은 그 값을 쓴다.
	 */
	private static final String HOURLY_JSON = """
			{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"items":{"item":[
			{"category":"TMP","fcstDate":"20260910","fcstTime":"0900","fcstValue":"21"},
			{"category":"SKY","fcstDate":"20260910","fcstTime":"0900","fcstValue":"1"},
			{"category":"POP","fcstDate":"20260910","fcstTime":"0900","fcstValue":"0"},
			{"category":"PTY","fcstDate":"20260910","fcstTime":"0900","fcstValue":"0"},
			{"category":"SKY","fcstDate":"20260910","fcstTime":"1000","fcstValue":"4"},
			{"category":"POP","fcstDate":"20260910","fcstTime":"1000","fcstValue":"70"},
			{"category":"PTY","fcstDate":"20260910","fcstTime":"1000","fcstValue":"4"},
			{"category":"TMN","fcstDate":"20260910","fcstTime":"0600","fcstValue":"18.0"},
			{"category":"TMX","fcstDate":"20260910","fcstTime":"1500","fcstValue":"27.0"},
			{"category":"TMP","fcstDate":"20260910","fcstTime":"1200","fcstValue":"25"},
			{"category":"TMP","fcstDate":"20260911","fcstTime":"0000","fcstValue":"19"}
			]}}}}""";

	@Test
	@DisplayName("🔴 응답에 hourly 가 더해지고 기존 forecast 는 칸 이름·값이 그대로다")
	void responseAddsHourlyWithoutChangingForecast() throws Exception {
		this.vendor.body = HOURLY_JSON;

		String body = this.mockMvc.perform(get("/api/v1/weather")
						.param("lat", "35.1796")
						.param("lon", "129.0756")
						.param("date", "2026-09-10")
						.principal(asUser()))
				.andExpect(status().isOk())
				// 기존 하루 요약 — 이 줄들이 옛 화면이 읽는 것이다.
				.andExpect(jsonPath("$.data.forecast.date").value("2026-09-10"))
				.andExpect(jsonPath("$.data.forecast.minTemperature").value(18.0))
				.andExpect(jsonPath("$.data.forecast.maxTemperature").value(27.0))
				.andExpect(jsonPath("$.data.forecast.precipitationProbability").value(70))
				.andExpect(jsonPath("$.data.forecast.skyCondition").value("CLOUDY"))
				// 시간별 — 그 날짜의 것만, 시각 오름차순.
				.andExpect(jsonPath("$.data.hourly.length()").value(3))
				.andExpect(jsonPath("$.data.hourly[0].time").value("09:00"))
				.andExpect(jsonPath("$.data.hourly[0].temperature").value(21.0))
				.andExpect(jsonPath("$.data.hourly[0].skyCondition").value("CLEAR"))
				.andExpect(jsonPath("$.data.hourly[0].precipitationProbability").value(0))
				.andExpect(jsonPath("$.data.hourly[0].precipitationType").value("NONE"))
				.andExpect(jsonPath("$.data.hourly[1].time").value("10:00"))
				.andExpect(jsonPath("$.data.hourly[1].skyCondition").value("CLOUDY"))
				.andExpect(jsonPath("$.data.hourly[1].precipitationProbability").value(70))
				.andExpect(jsonPath("$.data.hourly[1].precipitationType").value("SHOWER"))
				.andExpect(jsonPath("$.data.hourly[2].time").value("12:00"))
				.andExpect(jsonPath("$.data.hourly[2].temperature").value(25.0))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		JsonNode data = new ObjectMapper().readTree(body).path("data");
		// 칸을 더하기만 했다 — 앞의 칸 이름이 그대로이고 hourly 하나만 늘었다.
		assertThat(fieldNames(data)).containsExactlyInAnyOrder("nx", "ny", "cached", "forecast", "hourly");
		assertThat(fieldNames(data.path("forecast"))).containsExactlyInAnyOrder("date", "minTemperature", "maxTemperature",
				"precipitationProbability", "skyCondition");

		// 원문에 없는 값은 키가 있고 값이 null 이다 — 0 으로 채우지도, 키를 빼지도 않는다.
		JsonNode tenOClock = data.path("hourly").get(1);
		assertThat(tenOClock.has("temperature")).isTrue();
		assertThat(tenOClock.get("temperature").isNull()).as("10시 기온이 원문에 없는데 값이 나왔다").isTrue();
		JsonNode noon = data.path("hourly").get(2);
		assertThat(noon.get("skyCondition").isNull()).isTrue();
		assertThat(noon.get("precipitationProbability").isNull()).isTrue();
		assertThat(noon.get("precipitationType").isNull()).isTrue();
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names;
	}

	private static final class StubVendor implements WeatherVendorPort {

		boolean shouldFail = false;

		String body = SAMPLE_JSON;

		@Override
		public String fetchForecastJson(int nx, int ny, KmaBaseTime baseTime) {
			if (this.shouldFail) {
				throw new WeatherVendorException("WEATHER_VENDOR_UNAVAILABLE", "기상청 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return this.body;
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
