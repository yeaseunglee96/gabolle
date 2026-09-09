package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryExclusionJpaRepository extends JpaRepository<ItineraryExclusionJpaEntity, UUID> {

	List<ItineraryExclusionJpaEntity> findByItineraryVersionIdOrderByCreatedAtAsc(UUID itineraryVersionId);
}
