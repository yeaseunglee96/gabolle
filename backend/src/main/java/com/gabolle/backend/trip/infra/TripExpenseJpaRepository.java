package com.gabolle.backend.trip.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** 여행에서 쓴 돈(S15P21E201-1935). */
public interface TripExpenseJpaRepository extends JpaRepository<TripExpenseJpaEntity, UUID> {

	/** 이 여행의 쓴 돈 — 최근 것이 위. 같은 시각이면 나중에 적은 것이 위. */
	List<TripExpenseJpaEntity> findByTripIdOrderBySpentAtDescCreatedAtDesc(UUID tripId);
}
