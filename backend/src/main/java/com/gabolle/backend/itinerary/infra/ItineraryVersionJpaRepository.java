package com.gabolle.backend.itinerary.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ItineraryVersionJpaRepository extends JpaRepository<ItineraryVersionJpaEntity, UUID> {

	Optional<ItineraryVersionJpaEntity> findByItineraryIdAndVersion(UUID itineraryId, int version);

	/** 🔴 S15P21E201-284 — 최신 판이 먼저. {@link JpaItineraryRepository#findVersions} 가 쓴다. */
	List<ItineraryVersionJpaEntity> findByItineraryIdOrderByVersionDesc(UUID itineraryId);

	/** 협업 화면의 최근 변경 — 여러 일정의 판을 만든 시각 역순으로. 호출자가 {@code PageRequest.of(0, limit)} 로 자른다. */
	List<ItineraryVersionJpaEntity> findByItineraryIdInOrderByCreatedAtDesc(Collection<UUID> itineraryIds, Pageable pageable);
}
