package com.gabolle.backend.recommendation.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.trip.domain.TravelArea;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;
import com.gabolle.backend.trip.domain.TripTravelAreaRepository;
import com.gabolle.backend.trip.domain.WalkOnlyFirstDay;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 걷기만 고른 여행 — 여행 범위 말고 출발지 둘레도 훑는다 (S15P21E201-1634).
 *
 * <p>운영 여행 54854ec1: 출발 부산역, 범위 해운대. 범위만 훑어 후보 200곳이 전부 출발지에서 8~16km 였고 첫 구간이
 * 15km 걷기였다. 출발지 둘레(걸어서 30분)에서만 나온 곳에는 「첫날 전용」 이유 코드를 단다 — 일정 조립이 그곳을
 * 첫날에만 앉힌다.
 */
class BaselineRecommendationEngineWalkOnlyTest {

	private static final String TRIP_ID = UUID.randomUUID().toString();

	private static final double ORIGIN_LAT = 35.1152;

	private static final double ORIGIN_LNG = 129.0403;

	private static final BaselineEngineProperties PROPERTIES = new BaselineEngineProperties(
			"rule-v1", "feature-v1", "ontology-v1", "policy-v1", 5000, 20_000, 10, null, null);

	private static final UUID IN_AREA = new UUID(34L, 1L);

	private static final UUID NEAR_ORIGIN = new UUID(34L, 2L);

	private final TripRepository tripRepository = mock(TripRepository.class);

	private final PlaceCandidateQueryService queryService = mock(PlaceCandidateQueryService.class);

	private final UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);

	private final TripSeedPlaceRepository seedPlaceRepository = mock(TripSeedPlaceRepository.class);

	private final TripTravelAreaRepository travelAreas = mock(TripTravelAreaRepository.class);

	@Test
	@DisplayName("🔴 걷기만 고른 여행은 출발지 둘레(걸어서 30분)도 훑고, 거기서만 나온 곳에 「첫날 전용」을 단다")
	void walkOnlyTripsAlsoSweepAroundTheOrigin() {
		givenTrip("WALK");

		EngineCandidateBatch batch = engine().generate(request());

		assertThat(reasonsOf(batch, NEAR_ORIGIN)).contains(WalkOnlyFirstDay.REASON_CODE);
		assertThat(reasonsOf(batch, IN_AREA)).as("고른 범위 안의 곳은 어느 날이든 된다")
				.doesNotContain(WalkOnlyFirstDay.REASON_CODE);
	}

	@Test
	@DisplayName("걷기만이 아니면 출발지 둘레를 따로 훑지 않는다 — 지금과 같다")
	void otherTripsDoNotSweepTheOrigin() {
		givenTrip("BUS", "SUBWAY");

		EngineCandidateBatch batch = engine().generate(request());

		verify(this.queryService, never()).findCandidates(argThat(this::isOriginSweep));
		assertThat(batch.candidates().stream().map(EngineCandidate::placeId)).doesNotContain(NEAR_ORIGIN);
	}

	private boolean isOriginSweep(PlaceCandidateRequest asked) {
		return asked != null && asked.radiusM() == WalkOnlyFirstDay.RADIUS_M
				&& Math.abs(asked.center().lat() - ORIGIN_LAT) < 1e-6 && Math.abs(asked.center().lng() - ORIGIN_LNG) < 1e-6;
	}

	private void givenTrip(String... modes) {
		when(this.tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(Trip.builder()
				.tripId(TRIP_ID).createdBy(UUID.randomUUID().toString())
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 2))
				.originLat(ORIGIN_LAT).originLng(ORIGIN_LNG).partySize(2).timezone("Asia/Seoul")
				.travelModes(modes)
				.build()));
		when(this.travelAreas.findByTripId(TRIP_ID)).thenReturn(List.of(TravelArea.HAEUNDAE));
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(any())).thenReturn(List.of());
		when(this.codeMapRepository.findByIdUserInputKindAndIdUserInputCode(UserInputKind.PREFERENCE, "CATEGORY"))
				.thenReturn(List.of());
		when(this.seedPlaceRepository.findByTripId(TRIP_ID)).thenReturn(List.of());
		// 출발지 둘레를 물으면 부산역 곁의 곳, 그 밖(범위·출발지 5km)은 해운대의 곳만 준다.
		when(this.queryService.findCandidates(any())).thenAnswer(invocation -> {
			PlaceCandidateRequest asked = invocation.getArgument(0);
			return isOriginSweep(asked)
					? response(new PlaceCandidateResponse.Candidate(NEAR_ORIGIN, "초량 이바구길", "CULTURE_TEMPLE",
							35.1170, 129.0390, 250L, List.of()))
					: response(new PlaceCandidateResponse.Candidate(IN_AREA, "해운대해수욕장", "SEA_BEACH",
							35.1587, 129.1604, 11_000L, List.of()));
		});
	}

	private static List<String> reasonsOf(EngineCandidateBatch batch, UUID placeId) {
		return batch.candidates().stream()
				.filter((candidate) -> candidate.placeId().equals(placeId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("후보에 없다: " + placeId))
				.reasonCodes();
	}

	private BaselineRecommendationEngine engine() {
		return new BaselineRecommendationEngine(this.tripRepository, this.queryService,
				new BaselineCandidateTranslator(PROPERTIES, this.codeMapRepository, new ObjectMapper()),
				new BaselineCandidateScorer(new ObjectMapper()), PROPERTIES,
				new PreferenceAlignmentWeights(null, null, null, null, null),
				this.codeMapRepository, this.seedPlaceRepository, mock(PlaceRepository.class),
				Optional.of(this.travelAreas), emptyProvider(), emptyProvider());
	}

	private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyProvider() {
		@SuppressWarnings("unchecked")
		org.springframework.beans.factory.ObjectProvider<T> provider =
				mock(org.springframework.beans.factory.ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	private EngineRequest request() {
		return new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(TRIP_ID), 1,
				null, null, null, null, 10);
	}

	private static PlaceCandidateResponse response(PlaceCandidateResponse.Candidate candidate) {
		return new PlaceCandidateResponse(List.of(candidate), 1, 0, false,
				List.of("CENTER_RADIUS"), List.of(), false, List.of("fixture"));
	}
}
