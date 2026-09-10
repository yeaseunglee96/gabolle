package com.gabolle.backend.weather.infra;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.gabolle.backend.weather.domain.WeatherForecastCacheRepository;

/** {@link WeatherForecastCacheRepository} 의 JPA 구현 — S15P21E201-366. */
@Repository
@Profile({ "db", "dev" })
public class JpaWeatherForecastCacheRepository implements WeatherForecastCacheRepository {

	private final WeatherForecastCacheJpaRepository jpaRepository;

	public JpaWeatherForecastCacheRepository(WeatherForecastCacheJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<String> findFreshForecastJson(String cacheKey, Instant now) {
		return this.jpaRepository.findByCacheKey(cacheKey)
				.filter(entity -> entity.getExpiresAt().isAfter(now))
				.map(WeatherForecastCacheJpaEntity::getForecastJson);
	}

	@Override
	public void save(String cacheKey, String forecastJson, Instant now, Instant expiresAt) {
		// 이미 같은 열쇠가 있으면(레이스로 두 번 불렸다) 먼저 담긴 값을 그대로 둔다 — upsert 를
		// 대신하는 가장 단순한 방법이다. unique 제약이 있어 그냥 save 하면 중복 키 예외가 난다.
		if (this.jpaRepository.findByCacheKey(cacheKey).isPresent()) {
			return;
		}
		this.jpaRepository.save(WeatherForecastCacheJpaEntity.of(cacheKey, forecastJson, now, expiresAt));
	}
}
