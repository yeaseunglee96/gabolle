package com.gabolle.backend.itinerary.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 휠체어·유모차·계단 피함을 고른 여행은 구간을 계단 없는 길로 묻는다.
 *
 * <p>이 시험이 생긴 이유. 서버의 보행 길찾기가 계단과 경사를 지도에 칠하기만 하고 길 고르기에는 안 써서, 휠체어로
 * 들어갈 수 있는 곳을 추천해 놓고 가는 길은 계단 지름길로 냈다. 여기서는 일정이 그 뜻(stepFree)을 이동 시간 문에
 * 실제로 싣는지만 본다 — 길을 고르는 것은 {@code WalkGraph} 이고 그쪽 시험이 따로 있다.
 */
class ItineraryLegPlannerStepFreeTest {

	private static final String TRIP_ID = "trip_1";

	private static final String SNAPSHOT_ID = "cs_1";

	private final PlaceRepository places = mock(PlaceRepository.class);

	private final TripRepository trips = mock(TripRepository.class);

	private final Place stop = Place.imported(UUID.randomUUID(), "장소", "FOOD", null, 35.1631, 129.1638, "FIXTURE",
			"fixture-1", null, null, "v1");

	/** 이동 시간 문에 무엇이 실려 왔는지 적어 두는 가짜. */
	private final List<Boolean> askedStepFree = new ArrayList<>();

	private final TravelTimePort port = new TravelTimePort() {
		@Override
		public TravelTime between(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode) {
			return between(fromLat, fromLng, toLat, toLng, travelMode, false);
		}

		@Override
		public TravelTime between(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode,
				boolean stepFree) {
			ItineraryLegPlannerStepFreeTest.this.askedStepFree.add(stepFree);
			return new TravelTime(500, 7, ItineraryItem.DataStatus.VERIFIED);
		}
	};

	private List<Boolean> askFor(TripConstraint... constraints) {
		when(this.places.findByPlaceIdIn(any())).thenReturn(List.of(this.stop));
		when(this.trips.findLatestConstraintSnapshotId(TRIP_ID)).thenReturn(Optional.of(SNAPSHOT_ID));
		when(this.trips.findConstraintsBySnapshotId(SNAPSHOT_ID)).thenReturn(List.of(constraints));
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(this.port);
		ItineraryLegPlanner planner = new ItineraryLegPlanner(this.places, provider);
		planner.setTripRepository(this.trips);

		planner.buildLegs(trip(), List.of(List.of(this.stop.getPlaceId(), this.stop.getPlaceId())));
		return this.askedStepFree;
	}

	@Test
	@DisplayName("🔴 휠체어를 고른 여행은 모든 구간을 계단 없는 길로 묻는다")
	void 휠체어면_계단없는_길로_묻는다() {
		assertThat(askFor(mobility("WHEELCHAIR", TripConstraint.Severity.HARD, TripConstraint.AnswerStatus.SELECTED)))
				.hasSize(2).containsOnly(true);
	}

	@Test
	@DisplayName("유모차·계단 피함도 같다 — 「되도록」이어도 돌아갈 길이 있으면 돌아간다")
	void 유모차와_계단피함도_같다() {
		assertThat(askFor(mobility("STROLLER", TripConstraint.Severity.SOFT, TripConstraint.AnswerStatus.SELECTED)))
				.containsOnly(true);
		this.askedStepFree.clear();
		assertThat(askFor(mobility("STAIRS_AVOIDANCE", TripConstraint.Severity.SOFT,
				TripConstraint.AnswerStatus.SELECTED))).containsOnly(true);
	}

	@Test
	@DisplayName("🔴 이동 조건이 없거나 「없다고 답했다」면 전처럼 보통 길이다")
	void 고르지_않았으면_보통_길이다() {
		assertThat(askFor()).hasSize(2).containsOnly(false);
		this.askedStepFree.clear();
		assertThat(askFor(mobility("WHEELCHAIR", TripConstraint.Severity.HARD, TripConstraint.AnswerStatus.NONE)))
				.containsOnly(false);
	}

	@Test
	@DisplayName("계단과 상관없는 이동 조건(무거운 짐)만 있으면 보통 길이다")
	void 계단과_상관없는_조건이면_보통_길이다() {
		assertThat(askFor(mobility("HEAVY_LUGGAGE", TripConstraint.Severity.SOFT,
				TripConstraint.AnswerStatus.SELECTED))).containsOnly(false);
	}

	@Test
	@DisplayName("여행 저장소가 없는 컨텍스트에서도 뜬다 — 그때는 보통 길이다")
	void 저장소가_없으면_보통_길이다() {
		when(this.places.findByPlaceIdIn(any())).thenReturn(List.of(this.stop));
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(this.port);

		new ItineraryLegPlanner(this.places, provider).buildLegs(trip(), List.of(List.of(this.stop.getPlaceId())));

		assertThat(this.askedStepFree).containsExactly(false);
	}

	private static TripConstraint mobility(String key, TripConstraint.Severity severity,
			TripConstraint.AnswerStatus answerStatus) {
		boolean selected = answerStatus == TripConstraint.AnswerStatus.SELECTED;
		String operator = (severity == TripConstraint.Severity.HARD) ? "EXCLUDES" : null;
		return new TripConstraint(UUID.randomUUID().toString(), TRIP_ID, "MOBILITY", key, severity, operator,
				selected ? "true" : null, null, TripConstraint.EvidenceStatus.VERIFIED, answerStatus,
				PersonalizationScope.TRIP, null);
	}

	private static Trip trip() {
		return new Trip(TRIP_ID, "usr_1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
				35.1152, 129.0422, 300_000, 2, "09:00-18:00", "Asia/Seoul",
				new String[] { "WALK" }, null, null, null, false, false, false, null, Instant.now());
	}
}
