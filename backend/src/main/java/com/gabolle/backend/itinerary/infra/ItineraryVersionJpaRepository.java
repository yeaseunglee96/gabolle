package com.gabolle.backend.itinerary.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryVersionJpaRepository extends JpaRepository<ItineraryVersionJpaEntity, UUID> {

	Optional<ItineraryVersionJpaEntity> findByItineraryIdAndVersion(UUID itineraryId, int version);
}
