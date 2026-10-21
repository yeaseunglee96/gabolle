package com.gabolle.backend.trip.infra;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 여행 별점. 「내 별점」은 {@code findById(new Key(trip, me))} 다. */
public interface TripRatingJpaRepository extends JpaRepository<TripRatingJpaEntity, TripRatingJpaEntity.Key> {

	/** 이 여행의 별점 개수. */
	@Query("SELECT COUNT(r) FROM TripRatingJpaEntity r WHERE r.id.tripId = :tripId")
	long countByTrip(@Param("tripId") UUID tripId);

	/** 이 여행의 별점 평균. 아무도 안 매겼으면 {@code null}. */
	@Query("SELECT AVG(r.score) FROM TripRatingJpaEntity r WHERE r.id.tripId = :tripId")
	Double averageByTrip(@Param("tripId") UUID tripId);
}
