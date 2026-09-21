package com.gabolle.backend.trip.infra;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

@Repository
@Profile({ "db", "dev" })
public class JpaTripSeedPlaceRepository implements TripSeedPlaceRepository {

	private final TripSeedPlaceJpaRepository jpaRepository;

	public JpaTripSeedPlaceRepository(TripSeedPlaceJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public void saveAll(List<TripSeedPlace> seeds) {
		this.jpaRepository.saveAll(seeds.stream()
				.map(s -> new TripSeedPlaceJpaEntity(UUID.fromString(s.tripId()), UUID.fromString(s.placeId()),
						s.sequence(),
						s.sourceTripId() == null ? null : UUID.fromString(s.sourceTripId()),
						s.sourceShareLinkId() == null ? null : UUID.fromString(s.sourceShareLinkId()),
						s.createdAt().atOffset(ZoneOffset.UTC)))
				.toList());
	}

	@Override
	public List<TripSeedPlace> findByTripId(String tripId) {
		return this.jpaRepository.findByKeyTripIdOrderBySequenceAsc(UUID.fromString(tripId)).stream()
				.map(e -> new TripSeedPlace(e.key().tripId().toString(), e.key().placeId().toString(), e.sequence(),
						e.sourceTripId() == null ? null : e.sourceTripId().toString(),
						e.sourceShareLinkId() == null ? null : e.sourceShareLinkId().toString(),
						e.createdAt().toInstant()))
				.toList();
	}
}
