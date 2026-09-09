package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryJpaRepository extends JpaRepository<ItineraryJpaEntity, UUID> {

	List<ItineraryJpaEntity> findByTripIdOrderByCreatedAtAsc(UUID tripId);
}
