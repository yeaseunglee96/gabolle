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

/** {@code trip_invite} 표 매핑. 모든 칸이 불변이다 — 초대는 고치지 않고 새로 만든다. */
@Entity
@Table(name = "trip_invite")
public class TripInviteJpaEntity {

	@Id
	@Column(name = "trip_invite_id")
	private UUID tripInviteId;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "token", nullable = false, length = 64, updatable = false)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 10, updatable = false)
	private TripMember.Role role;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private OffsetDateTime expiresAt;

	protected TripInviteJpaEntity() {
		// JPA 전용
	}

	TripInviteJpaEntity(UUID tripInviteId, UUID tripId, String token, TripMember.Role role, UUID createdBy,
			OffsetDateTime createdAt, OffsetDateTime expiresAt) {
		this.tripInviteId = tripInviteId;
		this.tripId = tripId;
		this.token = token;
		this.role = role;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	UUID tripInviteId() { return tripInviteId; }
	UUID tripId() { return tripId; }
	String token() { return token; }
	TripMember.Role role() { return role; }
	UUID createdBy() { return createdBy; }
	OffsetDateTime createdAt() { return createdAt; }
	OffsetDateTime expiresAt() { return expiresAt; }
}
