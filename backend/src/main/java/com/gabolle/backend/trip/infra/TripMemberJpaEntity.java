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

/**
 * {@code trip_member} 표 매핑. {@code role} 만 바뀔 수 있어({@code updatable} 을 안 막았다)
 * 소유자가 편집자↔열람자를 바꾼다. 나머지 칸은 불변이다 — 누가 언제 들어왔는지는 고치지 않는다.
 */
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
	@Column(name = "role", nullable = false, length = 10)
	private TripMember.Role role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private OffsetDateTime joinedAt;

	@Column(name = "trip_invite_id", updatable = false)
	private UUID tripInviteId;

	@Column(name = "invited_by", updatable = false)
	private UUID invitedBy;

	@Column(name = "invited_at", updatable = false)
	private OffsetDateTime invitedAt;

	protected TripMemberJpaEntity() {
		// JPA 전용
	}

	TripMemberJpaEntity(UUID tripMemberId, UUID tripId, UUID userId, TripMember.Role role, OffsetDateTime joinedAt) {
		this(tripMemberId, tripId, userId, role, joinedAt, null, null, null);
	}

	TripMemberJpaEntity(UUID tripMemberId, UUID tripId, UUID userId, TripMember.Role role, OffsetDateTime joinedAt,
			UUID tripInviteId, UUID invitedBy, OffsetDateTime invitedAt) {
		this.tripMemberId = tripMemberId;
		this.tripId = tripId;
		this.userId = userId;
		this.role = role;
		this.joinedAt = joinedAt;
		this.tripInviteId = tripInviteId;
		this.invitedBy = invitedBy;
		this.invitedAt = invitedAt;
	}

	UUID tripMemberId() { return tripMemberId; }
	UUID tripId() { return tripId; }
	UUID userId() { return userId; }
	TripMember.Role role() { return role; }
	OffsetDateTime joinedAt() { return joinedAt; }
	UUID tripInviteId() { return tripInviteId; }
	UUID invitedBy() { return invitedBy; }
	OffsetDateTime invitedAt() { return invitedAt; }

	/** 누가 바꿀 수 있는지는 서비스가 판정하고 여기는 값만 바꾼다. */
	void changeRole(TripMember.Role newRole) {
		this.role = newRole;
	}
}
