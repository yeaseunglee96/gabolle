package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripMemberJpaRepository extends JpaRepository<TripMemberJpaEntity, UUID> {

	List<TripMemberJpaEntity> findByTripId(UUID tripId);

	/** S15P21E201-299 · -320 — 한 사람의 참여 행. {@code uq_trip_member (trip_id, user_id)} 가 하나임을 보장한다. */
	Optional<TripMemberJpaEntity> findByTripIdAndUserId(UUID tripId, UUID userId);
}
