package com.gabolle.backend.trip.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.gabolle.backend.trip.domain.PersonalizationScope;

/**
 * {@code preference_snapshot} 표 매핑. {@code user_id} 는 도메인 {@code PreferenceSnapshot} 에 없어
 * {@link JpaTripRepository} 가 {@code save} 로 함께 받는 {@code TripMember owner} 에서 채운다 —
 * scope=TRIP 스냅샷은 항상 그 여행을 만든 사람의 것이다.
 */
@Entity
@Table(name = "preference_snapshot")
public class PreferenceSnapshotJpaEntity {

	@Id
	@Column(name = "preference_snapshot_id")
	private UUID preferenceSnapshotId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "trip_id", updatable = false)
	private UUID tripId;

	@Column(name = "version", nullable = false, updatable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope", nullable = false, length = 10, updatable = false)
	private PersonalizationScope scope;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected PreferenceSnapshotJpaEntity() {
		// JPA 전용
	}

	PreferenceSnapshotJpaEntity(UUID preferenceSnapshotId, UUID userId, UUID tripId, int version,
			PersonalizationScope scope, OffsetDateTime createdAt) {
		this.preferenceSnapshotId = preferenceSnapshotId;
		this.userId = userId;
		this.tripId = tripId;
		this.version = version;
		this.scope = scope;
		this.createdAt = createdAt;
	}

	UUID preferenceSnapshotId() { return preferenceSnapshotId; }
	UUID tripId() { return tripId; }
	int version() { return version; }
	PersonalizationScope scope() { return scope; }
	OffsetDateTime createdAt() { return createdAt; }
}
