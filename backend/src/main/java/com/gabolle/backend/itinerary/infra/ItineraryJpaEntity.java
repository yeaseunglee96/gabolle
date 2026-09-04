package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itineraries} 표 매핑 — S15P21E201-313.
 *
 * <p>🔴 {@code latest_version} 을 여기서 직접 갱신한다({@link #updateLatestVersion(int)}).
 * 도메인 {@code Itinerary.moveTo(int)} 와 같은 불변식(한 칸씩만 전진)을 요구하지 않는다 —
 * 그 검증은 {@link JpaItineraryRepository#append} 가 도메인 객체로 이미 통과시킨 값만
 * 여기로 가져오기 때문이다. 이 엔티티는 검증하지 않고 그대로 반영한다.
 */
@Entity
@Table(name = "itineraries")
public class ItineraryJpaEntity {

	@Id
	@Column(name = "itinerary_id")
	private UUID itineraryId;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "latest_version", nullable = false)
	private int latestVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ItineraryJpaEntity() {
		// JPA 전용
	}

	ItineraryJpaEntity(UUID itineraryId, UUID tripId, int latestVersion, OffsetDateTime createdAt) {
		this.itineraryId = itineraryId;
		this.tripId = tripId;
		this.latestVersion = latestVersion;
		this.createdAt = createdAt;
	}

	UUID itineraryId() { return itineraryId; }
	UUID tripId() { return tripId; }
	int latestVersion() { return latestVersion; }
	OffsetDateTime createdAt() { return createdAt; }

	void updateLatestVersion(int newVersion) {
		this.latestVersion = newVersion;
	}
}
