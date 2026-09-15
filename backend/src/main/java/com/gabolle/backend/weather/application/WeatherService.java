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
 * 지역·날짜로 기상청 단기예보를 조회해 답한다 — S15P21E201-366.
 *
 * <h2>순서</h2>
 * <ol>
 *   <li>위경도를 기상청 격자로 바꾸고({@link KmaGridConverter}), 지금 조회해도 반영이 끝난
 *       가장 최근 발표 회차를 고른다({@link KmaBaseTimeCalculator})</li>
 *   <li>격자+회차 열쇠로 캐시에 있으면(만료 전이면) 그 원문을 그대로 쓴다 — 벤더를 다시
 *       부르지 않는다</li>
 *   <li>없으면 벤더를 부르고, 성공하면 원문을 담아 둔다</li>
 *   <li>원문(캐시 히트든 새로 받은 것이든)을 파싱해 요청한 날짜로 하루 요약을 만든다</li>
 * </ol>
 *
 * <p>🔴 <b>벤더 호출이 실패하면 {@link WeatherVendorException} 이 그대로 위로 올라간다.</b>
 * 여기서 잡아 지어낸 값으로 대신 답하지 않는다 — 이 티켓이 금지하는 바로 그 실수다.
 *
 * <p>{@code @Profile({"db","dev"})} 는 {@code TranslationService} 와 같은 이유다 — 이
 * 서비스가 요구하는 {@link WeatherVendorPort}·{@link WeatherForecastCacheRepository} 구현이
 * 이 두 프로필에서만 뜨므로, 서비스도 같이 묶지 않으면 프로필 없는 기본 컨텍스트 테스트
 * ({@code GabolleBackendApplicationTests})가 빈을 못 찾아 죽는다.
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
	 * 캐시에 이미 있는 것만 답한다 — <b>기상청을 부르지 않는다</b> (S15P21E201-993).
	 *
	 * <p>🔴 로그인하지 않은 사람이 오는 길이다. 문턱을 없앤 대신 이 경계를 둔다 — 익명
	 * 출입증은 누구나 발급받을 수 있어서, 이 길에서 벤더를 부르면 좌표를 바꿔가며 우리 키의
	 * 호출 한도를 태울 수 있다. 미리 받아 두는 작업({@code WeatherPrefetchScheduler})이
	 * 부산 격자를 채워 두므로, 부산 안이라면 이 길로도 값이 나온다.
	 *
	 * @throws ForecastNotPreparedException 아직 안 받아 둔 격자·회차다
	 */
	public WeatherForecastResult getForecastFromCache(WeatherQuery query) {
		return forecast(query, false);
	}

	/**
	 * 한 격자의 이번 발표 회차를 받아 캐시에 채운다. 이미 있으면 아무것도 안 한다.
	 *
	 * @return 실제로 기상청을 불러 새로 채웠으면 {@code true}
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
			// 🔴 실패하면 여기서 던진 WeatherVendorException 이 그대로 위로 올라간다.
			rawJson = this.vendor.fetchForecastJson(grid.nx(), grid.ny(), baseTime);
			Instant expiresAt = now.plus(this.properties.getCacheTtl());
			this.cacheRepository.save(cacheKey, rawJson, now, expiresAt);
		}

		List<KmaForecastItem> items = KmaForecastJsonParser.parse(rawJson, this.objectMapper);
		DailyForecast forecast = KmaForecastAggregator.aggregate(items, query.date())
				.orElseThrow(() -> new IllegalArgumentException(
						"date 가 이 발표 회차의 단기예보 범위를 벗어났습니다: " + query.date()));

		return new WeatherForecastResult(grid, forecast, cacheHit);
	}

	/**
	 * 미리 받아 둔 것이 없다 — S15P21E201-993.
	 *
	 * <p>부산 밖 좌표이거나, 미리 받아 두는 작업이 아직 그 회차를 못 채웠을 때다. 로그인한
	 * 사람에게는 이 예외가 나지 않는다(그 길은 기상청을 부른다).
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
