package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryItemActualJpaRepository extends JpaRepository<ItineraryItemActualJpaEntity, UUID> {

	List<ItineraryItemActualJpaEntity> findByItineraryId(UUID itineraryId);
}
