package com.gabolle.backend.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.DailyForecast;
import com.gabolle.backend.weather.domain.KmaBaseTime;
import com.gabolle.backend.weather.domain.WeatherForecastCacheRepository;
import com.gabolle.backend.weather.domain.WeatherForecastResult;
import com.gabolle.backend.weather.domain.WeatherQuery;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link WeatherService} 검증 — S15P21E201-366.
 *
 * <p>완료 기준 중 "지어낸 값이 코드 어디에도 없다" 를 가장 직접적으로 잰다 — 벤더가 실패하면
 * 예외가 그대로 위로 올라가고, 캐시에는 아무것도 안 남아야 한다.
 */
class WeatherServiceTest {

	// 2026-09-10 10:00 KST -- KmaBaseTimeCalculator 가 08:00 회차를 고르는 시각이다.
	private static final Instant NOW = Instant.parse("2026-09-10T01:00:00Z");

	private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 9, 10);

	private static final String SAMPLE_JSON = """
			{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"items":{"item":[
			{"category":"TMP","fcstDate":"20260910","fcstTime":"1200","fcstValue":"25"},
			{"category":"POP","fcstDate":"20260910","fcstTime":"1200","fcstValue":"20"},
			{"category":"SKY","fcstDate":"20260910","fcstTime":"1200","fcstValue":"1"}
			]}}}}""";

	private InMemoryCacheRepository cacheRepository;
	private CountingVendor vendor;
	private WeatherService service;

	@BeforeEach
	void setUp() {
		this.cacheRepository = new InMemoryCacheRepository();
		this.vendor = new CountingVendor();
		WeatherProperties properties = new WeatherProperties();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.service = new WeatherService(this.vendor, this.cacheRepository, properties, clock, new ObjectMapper());
	}

	@Test
	@DisplayName("캐시가 비어 있으면 벤더를 부르고 원문을 담아 둔다")
	void callsVendorOnCacheMissAndStoresResult() {
		WeatherForecastResult result = this.service.getForecast(new WeatherQuery(35.1796, 129.0756, REQUEST_DATE));

		assertThat(result.cached()).isFalse();
		assertThat(result.grid().nx()).isEqualTo(98);
		assertThat(result.grid().ny()).isEqualTo(76);
		DailyForecast forecast = result.forecast();
		assertThat(forecast.minTemperature()).isEqualTo(25.0);
		assertThat(forecast.maxTemperature()).isEqualTo(25.0);
		assertThat(forecast.precipitationProbability()).isEqualTo(20);
		assertThat(this.vendor.callCount).isEqualTo(1);
		assertThat(this.cacheRepository.store).hasSize(1);
	}

	@Test
	@DisplayName("🔴 같은 격자·같은 회차를 다시 물으면 벤더를 다시 안 부른다 — 캐시 히트")
	void secondCallForSameGridAndBaseTimeIsACacheHit() {
		WeatherQuery query = new WeatherQuery(35.1796, 129.0756, REQUEST_DATE);

		WeatherForecastResult first = this.service.getForecast(query);
		WeatherForecastResult second = this.service.getForecast(query);

		assertThat(first.cached()).isFalse();
		assertThat(second.cached()).isTrue();
		assertThat(second.forecast().minTemperature()).isEqualTo(25.0);
		// 🔴 이 값이 이 테스트의 핵심이다 — 두 번째 호출에서 벤더가 다시 불리면 실패한다.
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 벤더 호출이 실패하면 응답에 실패가 담겨 온다 — 지어낸 값으로 숨기지 않는다")
	void vendorFailurePropagatesInsteadOfBeingHidden() {
		this.vendor.shouldFail = true;

		assertThatThrownBy(() -> this.service.getForecast(new WeatherQuery(35.1796, 129.0756, REQUEST_DATE)))
				.isInstanceOf(WeatherVendorException.class);

		// 실패했으니 캐시에도 아무것도 안 남아야 한다.
		assertThat(this.cacheRepository.store).isEmpty();
	}

	@Test
	@DisplayName("요청한 날짜가 이 회차의 예보 범위 밖이면 400 성격의 예외다")
	void dateOutsideForecastRangeIsRejected() {
		assertThatThrownBy(() -> this.service
				.getForecast(new WeatherQuery(35.1796, 129.0756, REQUEST_DATE.plusDays(10))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static final class InMemoryCacheRepository implements WeatherForecastCacheRepository {

		final Map<String, Entry> store = new HashMap<>();

		@Override
		public Optional<String> findFreshForecastJson(String cacheKey, Instant now) {
			Entry entry = this.store.get(cacheKey);
			if (entry == null || !entry.expiresAt.isAfter(now)) {
				return Optional.empty();
			}
			return Optional.of(entry.forecastJson);
		}

		@Override
		public void save(String cacheKey, String forecastJson, Instant now, Instant expiresAt) {
			this.store.put(cacheKey, new Entry(forecastJson, expiresAt));
		}

		private record Entry(String forecastJson, Instant expiresAt) {
		}
	}

	private static final class CountingVendor implements WeatherVendorPort {

		int callCount = 0;
		boolean shouldFail = false;

		@Override
		public String fetchForecastJson(int nx, int ny, KmaBaseTime baseTime) {
			this.callCount++;
			if (this.shouldFail) {
				throw new WeatherVendorException("WEATHER_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return SAMPLE_JSON;
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}
}
