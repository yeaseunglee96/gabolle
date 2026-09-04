package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConstraintSnapshotJpaRepository extends JpaRepository<ConstraintSnapshotJpaEntity, UUID> {

	List<ConstraintSnapshotJpaEntity> findByTripId(UUID tripId);
}
