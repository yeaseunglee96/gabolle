package com.gabolle.backend.weather.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data 창구 — {@link JpaWeatherForecastCacheRepository} 가 감싸서 도메인 포트로 내보낸다. */
public interface WeatherForecastCacheJpaRepository extends JpaRepository<WeatherForecastCacheJpaEntity, UUID> {

	Optional<WeatherForecastCacheJpaEntity> findByCacheKey(String cacheKey);
}
