package com.gabolle.backend.trip.infra;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.gabolle.backend.trip.domain.TravelArea;
import com.gabolle.backend.trip.domain.TripTravelAreaRepository;

/** {@link TripTravelAreaRepository} 의 JPA 구현 — S15P21E201-980. */
@Repository
@Profile({ "db", "dev" })
public class JpaTripTravelAreaRepository implements TripTravelAreaRepository {

	private final TripTravelAreaJpaRepository jpaRepository;

	private final Clock clock;

	public JpaTripTravelAreaRepository(TripTravelAreaJpaRepository jpaRepository, Clock clock) {
		this.jpaRepository = jpaRepository;
		this.clock = clock;
	}

	@Override
	public void saveAll(String tripId, List<TravelArea> areas) {
		if (areas.isEmpty()) {
			return;
		}
		OffsetDateTime now = OffsetDateTime.ofInstant(this.clock.instant(), java.time.ZoneOffset.UTC);
		UUID trip = UUID.fromString(tripId);
		List<TripTravelAreaJpaEntity> rows = new ArrayList<>(areas.size());
		for (int index = 0; index < areas.size(); index++) {
			rows.add(new TripTravelAreaJpaEntity(trip, areas.get(index).name(), index + 1, now));
		}
		this.jpaRepository.saveAll(rows);
	}

	@Override
	public List<TravelArea> findByTripId(String tripId) {
		return this.jpaRepository.findByKeyTripIdOrderBySequenceAsc(UUID.fromString(tripId)).stream()
				.map((row) -> TravelArea.of(row.key().areaCode()))
				// 🔴 모르는 코드는 버린다. 표의 CHECK 가 막지만, 코드에서 지역을 지우는 날
				//    이미 저장된 행이 남아 있을 수 있다 — 그때 추천이 죽으면 안 된다.
				.filter(java.util.Optional::isPresent)
				.map(java.util.Optional::get)
				.toList();
	}
}
