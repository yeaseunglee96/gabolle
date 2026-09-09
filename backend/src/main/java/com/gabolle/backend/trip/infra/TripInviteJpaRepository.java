package com.gabolle.backend.trip.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripInviteJpaRepository extends JpaRepository<TripInviteJpaEntity, UUID> {

	/** 초대 수락이 URL 의 표로 찾는다. {@code uq_trip_invite_token} 이 하나임을 보장한다. */
	Optional<TripInviteJpaEntity> findByToken(String token);
}
