package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PreferenceAnswerJpaRepository extends JpaRepository<PreferenceAnswerJpaEntity, UUID> {

	List<PreferenceAnswerJpaEntity> findByPreferenceSnapshotId(UUID preferenceSnapshotId);
}
