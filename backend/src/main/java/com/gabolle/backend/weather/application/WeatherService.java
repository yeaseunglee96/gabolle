package com.gabolle.backend.weather.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.weather.config.WeatherProperties;
import com.gabolle.backend.weather.domain.DailyForecast;
import com.gabolle.backend.weather.domain.KmaBaseTime;
import com.gabolle.backend.weather.domain.KmaBaseTimeCalculator;
import com.gabolle.backend.weather.domain.KmaForecastAggregator;
import com.gabolle.backend.weather.domain.KmaForecastItem;
import com.gabolle.backend.weather.domain.KmaGridConverter;
import com.gabolle.backend.weather.domain.KmaGridCoordinate;
import com.gabolle.backend.weather.domain.WeatherForecastCacheRepository;
import com.gabolle.backend.weather.domain.WeatherForecastResult;
import com.gabolle.backend.weather.domain.WeatherQuery;

import tools.jackson.databind.ObjectMapper;

/**
 * 지역·날짜로 기상청 단기예보를 조회해 답한다. 캐시 열쇠는 격자 + 발표 회차라, 같은
 * 열쇠면 원문이 안 바뀐다.
 *
 * 벤더 호출이 실패하면 WeatherVendorException 이 그대로 위로 올라간다 — 여기서 잡아
 * 지어낸 값으로 답하지 않는다.
 *
 * @Profile 을 지우면 프로필 없는 기본 컨텍스트 테스트가 죽는다. 이 서비스가 요구하는
 * WeatherVendorPort·WeatherForecastCacheRepository 구현이 db·dev 에서만 뜬다.
 */
@Service
@Profile({ "db", "dev" })
public class WeatherService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final WeatherVendorPort vendor;

	private final WeatherForecastCacheRepository cacheRepository;

	private final WeatherProperties properties;

	private final Clock clock;

	private final ObjectMapper objectMapper;

	public WeatherService(WeatherVendorPort vendor, WeatherForecastCacheRepository cacheRepository,
			WeatherProperties properties, Clock clock, ObjectMapper objectMapper) {
		this.vendor = vendor;
		this.cacheRepository = cacheRepository;
		this.properties = properties;
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	public WeatherForecastResult getForecast(WeatherQuery query) {
		return forecast(query, true);
	}

	/**
	 * 캐시에 이미 있는 것만 답한다 — 기상청을 부르지 않는다. 로그인하지 않은 사람이 오는
	 * 길이라, 여기서 벤더를 부르면 좌표를 바꿔가며 우리 키의 호출 한도를 태울 수 있다.
	 * 안 받아 둔 격자·회차면 ForecastNotPreparedException.
	 */
	public WeatherForecastResult getForecastFromCache(WeatherQuery query) {
		return forecast(query, false);
	}

	/**
	 * 한 격자의 이번 발표 회차를 받아 캐시에 채운다. 이미 있으면 아무것도 안 한다.
	 * 실제로 기상청을 불러 새로 채웠을 때만 true.
	 */
	public boolean prefetch(KmaGridCoordinate grid) {
		ZonedDateTime nowInSeoul = ZonedDateTime.now(this.clock).withZoneSameInstant(KST);
		KmaBaseTime baseTime = KmaBaseTimeCalculator.calculate(nowInSeoul.toLocalDateTime());
		String cacheKey = cacheKey(grid, baseTime);
		Instant now = Instant.now(this.clock);

		if (this.cacheRepository.findFreshForecastJson(cacheKey, now).isPresent()) {
			return false;
		}
		String rawJson = this.vendor.fetchForecastJson(grid.nx(), grid.ny(), baseTime);
		this.cacheRepository.save(cacheKey, rawJson, now, now.plus(this.properties.getCacheTtl()));
		return true;
	}

	private WeatherForecastResult forecast(WeatherQuery query, boolean mayCallVendor) {
		KmaGridCoordinate grid = KmaGridConverter.toGrid(query.lat(), query.lon());
		ZonedDateTime nowInSeoul = ZonedDateTime.now(this.clock).withZoneSameInstant(KST);
		KmaBaseTime baseTime = KmaBaseTimeCalculator.calculate(nowInSeoul.toLocalDateTime());

		String cacheKey = cacheKey(grid, baseTime);
		Instant now = Instant.now(this.clock);

		Optional<String> cached = this.cacheRepository.findFreshForecastJson(cacheKey, now);
		boolean cacheHit = cached.isPresent();

		String rawJson;
		if (cacheHit) {
			rawJson = cached.get();
		}
		else if (!mayCallVendor) {
			throw new ForecastNotPreparedException(grid);
		}
		else {
			// 실패하면 여기서 던진 WeatherVendorException 이 그대로 위로 올라간다.
			rawJson = this.vendor.fetchForecastJson(grid.nx(), grid.ny(), baseTime);
			Instant expiresAt = now.plus(this.properties.getCacheTtl());
			this.cacheRepository.save(cacheKey, rawJson, now, expiresAt);
		}

		List<KmaForecastItem> items = KmaForecastJsonParser.parse(rawJson, this.objectMapper);
		Optional<DailyForecast> forecast = KmaForecastAggregator.aggregate(items, query.date());
		if (forecast.isPresent()) {
			return new WeatherForecastResult(grid, forecast.get(), cacheHit);
		}

		// 이번 회차에 그 날짜가 없다. 밤 23시 10분 이후의 「오늘」이 그렇다 —
		// 이미 받아 둔 이전 회차에 담겨 있으므로 거기까지 찾아본다.
		return fromEarlierRound(grid, baseTime, query, now)
				.orElseThrow(() -> new IllegalArgumentException(
						"date 가 이 발표 회차의 단기예보 범위를 벗어났습니다: " + query.date()));
	}

	/**
	 * 이전 발표 회차의 캐시에서 그 날짜를 찾는다. 어느 회차에도 없으면 빈 값.
	 *
	 * 기상청 23시 발표는 오늘을 안 담고 다음 날부터라, 밤 23시 10분이 지나면 「오늘」이
	 * 이번 회차에 없다. 20시 발표에는 있고 이미 받아 둔 상태다.
	 *
	 * 캐시에 있는 것만 본다 — 새로 받지 않는다. 거슬러 가는 한계는 하루치 회차이고,
	 * 캐시가 하루라 그보다 오래된 것은 어차피 없다.
	 */
	private Optional<WeatherForecastResult> fromEarlierRound(KmaGridCoordinate grid, KmaBaseTime baseTime,
			WeatherQuery query, Instant now) {
		KmaBaseTime round = baseTime;
		for (int step = 0; step < KmaBaseTimeCalculator.announcementsPerDay(); step++) {
			round = KmaBaseTimeCalculator.previous(round);
			Optional<String> json = this.cacheRepository.findFreshForecastJson(cacheKey(grid, round), now);
			if (json.isEmpty()) {
				continue;
			}
			Optional<DailyForecast> found = KmaForecastAggregator
					.aggregate(KmaForecastJsonParser.parse(json.get(), this.objectMapper), query.date());
			if (found.isPresent()) {
				// 캐시로만 답했으므로 cached 는 참이다.
				return Optional.of(new WeatherForecastResult(grid, found.get(), true));
			}
		}
		return Optional.empty();
	}

	/**
	 * 미리 받아 둔 것이 없다. 부산 밖 좌표이거나 미리 받기가 아직 그 회차를 못 채웠을 때다.
	 * 로그인한 사람에게는 나지 않는다 — 그 길은 기상청을 부른다.
	 */
	public static class ForecastNotPreparedException extends RuntimeException {

		public ForecastNotPreparedException(KmaGridCoordinate grid) {
			super("아직 준비되지 않은 지역이에요. 로그인하면 바로 받아올 수 있어요. (격자 " + grid.nx() + "," + grid.ny() + ")");
		}

	}

	private String cacheKey(KmaGridCoordinate grid, KmaBaseTime baseTime) {
		return grid.nx() + "_" + grid.ny() + "_" + baseTime.key();
	}
}
