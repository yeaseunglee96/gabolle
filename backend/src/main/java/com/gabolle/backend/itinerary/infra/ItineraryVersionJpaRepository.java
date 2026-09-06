package com.gabolle.backend.itinerary.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryVersionJpaRepository extends JpaRepository<ItineraryVersionJpaEntity, UUID> {

	Optional<ItineraryVersionJpaEntity> findByItineraryIdAndVersion(UUID itineraryId, int version);

	/** 🔴 S15P21E201-284 — 최신 판이 먼저. {@link JpaItineraryRepository#findVersions} 가 쓴다. */
	List<ItineraryVersionJpaEntity> findByItineraryIdOrderByVersionDesc(UUID itineraryId);
}
