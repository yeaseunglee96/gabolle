package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripMemberJpaRepository extends JpaRepository<TripMemberJpaEntity, UUID> {

	List<TripMemberJpaEntity> findByTripId(UUID tripId);

	List<TripMemberJpaEntity> findByUserId(UUID userId);

	/** {@code uq_trip_member (trip_id, user_id)} 가 한 행임을 보장한다. */
	Optional<TripMemberJpaEntity> findByTripIdAndUserId(UUID tripId, UUID userId);
}
