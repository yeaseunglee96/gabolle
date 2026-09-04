package com.gabolle.backend.trip.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PreferenceSnapshotJpaRepository extends JpaRepository<PreferenceSnapshotJpaEntity, UUID> {

	Optional<PreferenceSnapshotJpaEntity> findByTripIdAndVersion(UUID tripId, int version);

	Optional<PreferenceSnapshotJpaEntity> findTopByTripIdOrderByVersionDesc(UUID tripId);
}
