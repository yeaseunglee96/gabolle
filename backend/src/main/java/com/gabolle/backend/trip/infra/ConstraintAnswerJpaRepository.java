package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConstraintAnswerJpaRepository extends JpaRepository<ConstraintAnswerJpaEntity, UUID> {

	List<ConstraintAnswerJpaEntity> findByConstraintSnapshotId(UUID constraintSnapshotId);
}
