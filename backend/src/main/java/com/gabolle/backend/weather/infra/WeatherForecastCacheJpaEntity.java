package com.gabolle.backend.weather.infra;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code weather_forecast_cache} 표 매핑 — S15P21E201-366.
 *
 * <p>{@code cacheKey} 는 격자(nx,ny)+발표 회차(baseDate+baseTime)로 만든 문자열이다 — 번역
 * 캐시의 {@code sourceHash} 와 달리 원문을 감출 이유가 없어 해시가 아니라 평문 열쇠를 그대로
 * 쓴다. 담아 두는 것은 기상청 응답 원문(JSON)이다.
 */
@Entity
@Table(name = "weather_forecast_cache")
public class WeatherForecastCacheJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID cacheId;

	@Column(name = "cache_key", nullable = false, unique = true, length = 64, updatable = false)
	private String cacheKey;

	@Column(name = "forecast_json", nullable = false, columnDefinition = "text", updatable = false)
	private String forecastJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	protected WeatherForecastCacheJpaEntity() {
		// JPA 전용
	}

	private WeatherForecastCacheJpaEntity(String cacheKey, String forecastJson, Instant createdAt, Instant expiresAt) {
		this.cacheKey = cacheKey;
		this.forecastJson = forecastJson;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public static WeatherForecastCacheJpaEntity of(String cacheKey, String forecastJson, Instant createdAt,
			Instant expiresAt) {
		return new WeatherForecastCacheJpaEntity(cacheKey, forecastJson, createdAt, expiresAt);
	}

	public UUID getCacheId() { return this.cacheId; }
	public String getCacheKey() { return this.cacheKey; }
	public String getForecastJson() { return this.forecastJson; }
	public Instant getCreatedAt() { return this.createdAt; }
	public Instant getExpiresAt() { return this.expiresAt; }
}
