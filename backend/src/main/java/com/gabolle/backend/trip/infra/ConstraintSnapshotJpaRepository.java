package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConstraintSnapshotJpaRepository extends JpaRepository<ConstraintSnapshotJpaEntity, UUID> {

	List<ConstraintSnapshotJpaEntity> findByTripId(UUID tripId);

	/** 추천 Job 을 만들 때 constraintSnapshotId 를 채우는 데 쓴다. */
	Optional<ConstraintSnapshotJpaEntity> findTopByTripIdOrderByVersionDesc(UUID tripId);
}
