package com.gabolle.backend.trip.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.gabolle.backend.trip.domain.TripMember;

/** {@code trip_member} 표 매핑 (V20260904030000) — S15P21E201-461. */
@Entity
@Table(name = "trip_member")
public class TripMemberJpaEntity {

	@Id
	@Column(name = "trip_member_id")
	private UUID tripMemberId;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 10, updatable = false)
	private TripMember.Role role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private OffsetDateTime joinedAt;

	protected TripMemberJpaEntity() {
		// JPA 전용
	}

	TripMemberJpaEntity(UUID tripMemberId, UUID tripId, UUID userId, TripMember.Role role, OffsetDateTime joinedAt) {
		this.tripMemberId = tripMemberId;
		this.tripId = tripId;
		this.userId = userId;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	UUID tripMemberId() { return tripMemberId; }
	UUID tripId() { return tripId; }
	UUID userId() { return userId; }
	TripMember.Role role() { return role; }
	OffsetDateTime joinedAt() { return joinedAt; }
}
