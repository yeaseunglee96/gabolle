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
 * {@code constraint_snapshot} 표 매핑 (V120000) — S15P21E201-461.
 *
 * <p>🔴 도메인에는 이 표에 대응하는 클래스가 없다. {@link com.gabolle.backend.trip.domain.TripConstraint}
 * 는 낱개 제약(=constraint_answer 한 행)만 알고, 그것들을 묶는 판(snapshot) 개념은
 * {@link JpaTripRepository} 가 저장 시점에만 만든다 — TRIP-01 은 여행 생성마다
 * 제약을 한 판(version=1)으로 굳히므로, 이 표는 저장·조회 경계 안에서만 쓰인다.
 */
@Entity
@Table(name = "constraint_snapshot")
public class ConstraintSnapshotJpaEntity {

	@Id
	@Column(name = "constraint_snapshot_id")
	private UUID constraintSnapshotId;

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

	protected ConstraintSnapshotJpaEntity() {
		// JPA 전용
	}

	ConstraintSnapshotJpaEntity(UUID constraintSnapshotId, UUID userId, UUID tripId, int version,
			PersonalizationScope scope, OffsetDateTime createdAt) {
		this.constraintSnapshotId = constraintSnapshotId;
		this.userId = userId;
		this.tripId = tripId;
		this.version = version;
		this.scope = scope;
		this.createdAt = createdAt;
	}

	UUID constraintSnapshotId() { return constraintSnapshotId; }
	UUID tripId() { return tripId; }
}
