package com.gabolle.backend.trip.infra;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** 여행 예산 — 여행 ID 가 곧 키다(S15P21E201-1935). */
public interface TripBudgetJpaRepository extends JpaRepository<TripBudgetJpaEntity, UUID> {
}
