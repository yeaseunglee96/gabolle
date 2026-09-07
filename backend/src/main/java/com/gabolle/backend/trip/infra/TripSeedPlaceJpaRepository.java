package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripSeedPlaceJpaRepository extends JpaRepository<TripSeedPlaceJpaEntity, TripSeedPlaceJpaEntity.Key> {

	List<TripSeedPlaceJpaEntity> findByKeyTripIdOrderBySequenceAsc(UUID tripId);
}
