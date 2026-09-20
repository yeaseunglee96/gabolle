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
 * weather_forecast_cache 표 매핑. cacheKey 는 격자(nx,ny)+발표 회차로 만든 평문 문자열이고
 * 담는 값은 기상청 응답 원문(JSON)이다.
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
