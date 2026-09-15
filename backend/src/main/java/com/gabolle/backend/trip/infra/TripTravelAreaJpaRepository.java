package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TripTravelAreaJpaRepository extends JpaRepository<TripTravelAreaJpaEntity, TripTravelAreaJpaEntity.Key> {

	List<TripTravelAreaJpaEntity> findByKeyTripIdOrderBySequenceAsc(UUID tripId);
}
