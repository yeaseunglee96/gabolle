package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryItemJpaRepository extends JpaRepository<ItineraryItemJpaEntity, UUID> {

	List<ItineraryItemJpaEntity> findByItineraryVersionIdOrderByDayIndexAscSequenceAsc(UUID itineraryVersionId);
}
