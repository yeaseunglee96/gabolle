package com.gabolle.backend.weather.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * 지역(격자)+발표시각 단위로 벤더 응답 원문을 들고 있는 자리. 같은 열쇠면 기상청 응답이
 * 고정되므로, 만료는 배치 삭제 없이 조회 시점에 expiresAt 을 보는 것으로 충분하다.
 */
public interface WeatherForecastCacheRepository {

	/** 만료되지 않은 원문 JSON. 없거나 지났으면 빈 값이다. */
	Optional<String> findFreshForecastJson(String cacheKey, Instant now);

	/** 이미 같은 열쇠가 있으면(레이스로 두 번 불렸다) 먼저 담긴 값을 그대로 둔다. */
	void save(String cacheKey, String forecastJson, Instant now, Instant expiresAt);
}
