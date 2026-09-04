package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripMemberJpaRepository extends JpaRepository<TripMemberJpaEntity, UUID> {

	List<TripMemberJpaEntity> findByTripId(UUID tripId);
}
