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
 * <p>🔴 <b>이 엔티티로 {@code latest_version} 을 바꾸지 않는다</b>(S15P21E201-662).
 * 예전에는 {@code updateLatestVersion(int)} 로 여기서 값을 고쳤는데, 그것은 "불러서 ·
 * 고치고 · 저장" 이라 두 트랜잭션이 같은 값을 읽을 수 있었다. 지금은
 * {@link JpaItineraryRepository#appendVersion} 이 {@code UPDATE ... WHERE latest_version
 * = :base} 한 문장으로 옮긴다 — 내가 본 값에서만 움직이는지를 DB 가 판정한다.
 * 그래서 값을 바꾸는 메서드를 아예 두지 않는다. 있으면 다음 사람이 그 길로 간다.
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
}
