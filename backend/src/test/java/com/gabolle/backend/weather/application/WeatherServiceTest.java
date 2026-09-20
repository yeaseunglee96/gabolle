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
		// 이 값이 이 테스트의 핵심이다 — 두 번째 호출에서 벤더가 다시 불리면 실패한다.
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

	// 밤 11시가 넘으면 오늘이 사라지던 것

	/** 2026-09-18 23:30 KST. 23:10 이 지났으므로 이번 회차는 그날 23시다. */
	private static final Instant NIGHT = Instant.parse("2026-09-18T14:30:00Z");

	private static final LocalDate NIGHT_TODAY = LocalDate.of(2026, 9, 18);

	/** 23시 발표 — 오늘을 안 담는다. 내일부터다. 이것이 이 결함의 뿌리다. */
	private static final String ROUND_2300_JSON = json("20260919", "21");

	/** 20시 발표 — 오늘을 담고 있다. 이미 받아 둔 것이다. */
	private static final String ROUND_2000_JSON = json("20260918", "18");

	@Test
	@DisplayName("🔴 밤 11시가 넘어도 오늘 날씨를 준다 — 20시 회차에 담겨 있고 이미 받아 뒀다")
	void nightFallsBackToTheEarlierRoundThatStillHasToday() {
		WeatherService night = serviceAt(NIGHT);
		this.cacheRepository.store.put("98_76_202609182300", entry(ROUND_2300_JSON));
		this.cacheRepository.store.put("98_76_202609182000", entry(ROUND_2000_JSON));

		WeatherForecastResult result = night.getForecast(new WeatherQuery(35.1796, 129.0756, NIGHT_TODAY));

		assertThat(result.forecast().maxTemperature()).as("20시 회차의 오늘 값이 안 나온다").isEqualTo(18.0);
		// 새로 받는 것이 아니라 받아 둔 것을 쓰는 것이다. 이 줄이 그 차이를 잰다.
		assertThat(this.vendor.callCount).as("거슬러 가면서 기상청을 불렀다").isZero();
		assertThat(result.cached()).isTrue();
	}

	@Test
	@DisplayName("🔴 이전 회차에도 오늘이 없으면 여전히 못 준다고 한다 — 지어내지 않는다")
	void nightStillRefusesWhenNoEarlierRoundHasTheDate() {
		WeatherService night = serviceAt(NIGHT);
		this.cacheRepository.store.put("98_76_202609182300", entry(ROUND_2300_JSON));
		// 20시 회차를 일부러 안 넣는다 — 미리 받아 두는 작업이 그 격자를 못 채운 경우다.

		assertThatThrownBy(() -> night.getForecast(new WeatherQuery(35.1796, 129.0756, NIGHT_TODAY)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.vendor.callCount).as("못 줄 바에 기상청을 불렀다 — 이 길은 캐시만 본다").isZero();
	}

	@Test
	@DisplayName("이번 회차에 있으면 거슬러 가지 않는다 — 낮의 동작이 안 바뀐다")
	void doesNotWalkBackWhenTheCurrentRoundHasTheDate() {
		WeatherService night = serviceAt(NIGHT);
		// 이번 회차가 내일(9/19)을 담고 있고, 이전 회차에는 같은 날짜에 다른 값을 넣어 둔다.
		this.cacheRepository.store.put("98_76_202609182300", entry(ROUND_2300_JSON));
		this.cacheRepository.store.put("98_76_202609182000", entry(json("20260919", "99")));

		WeatherForecastResult result = night.getForecast(
				new WeatherQuery(35.1796, 129.0756, LocalDate.of(2026, 9, 19)));

		assertThat(result.forecast().maxTemperature()).as("이전 회차 값이 이번 회차를 덮었다").isEqualTo(21.0);
	}

	private WeatherService serviceAt(Instant instant) {
		return new WeatherService(this.vendor, this.cacheRepository, new WeatherProperties(),
			Clock.fixed(instant, ZoneOffset.UTC), new ObjectMapper());
	}

	private InMemoryCacheRepository.Entry entry(String forecastJson) {
		return new InMemoryCacheRepository.Entry(forecastJson, NIGHT.plusSeconds(3600));
	}

	/** 하루치 예보 한 벌 — 그 날짜의 기온·강수확률·하늘상태. */
	private static String json(String fcstDate, String temperature) {
		return """
			{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"items":{"item":[
			{"category":"TMP","fcstDate":"%s","fcstTime":"1200","fcstValue":"%s"},
			{"category":"POP","fcstDate":"%s","fcstTime":"1200","fcstValue":"30"},
			{"category":"SKY","fcstDate":"%s","fcstTime":"1200","fcstValue":"1"}
			]}}}}""".formatted(fcstDate, temperature, fcstDate, fcstDate);
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

		record Entry(String forecastJson, Instant expiresAt) {
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
