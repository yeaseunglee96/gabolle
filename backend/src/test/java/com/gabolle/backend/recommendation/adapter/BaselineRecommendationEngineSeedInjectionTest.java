package com.gabolle.backend.recommendation.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「꼭 가고 싶은 장소」가 <b>반경 밖에 있어도</b> 후보에 들어오는가.
 *
 * <p>이 시험이 생긴 이유. 후보는 출발지 반경 5km 안에서만 뽑히는데 부산은 동서로 30km 가
 * 넘는다. 서면에서 출발하는 여행에 해운대를 적으면 그 장소는 후보 조회에 들어오지도 못했고,
 * 점수를 올려 주는 {@link SeedBoost} 도 볼 수 없었다 — <b>사용자가 이름을 직접 적어 넣은
 * 유일한 답이 조용히 사라졌다.</b> 그때도 일정은 멀쩡히 나와서 아무 데도 빨간불이 안 켜졌다.
 */
class BaselineRecommendationEngineSeedInjectionTest {

	private static final String TRIP_ID = UUID.randomUUID().toString();

	/** 출발지 — 서면. */
	private static final double ORIGIN_LAT = 35.1578;

	private static final double ORIGIN_LNG = 129.0594;

	/** 사용자가 적어 넣은 곳 — 해운대. 출발지에서 반경 5km 밖이다. */
	private static final double FAR_LAT = 35.1587;

	private static final double FAR_LNG = 129.1604;

	private static final int RADIUS_M = 5000;

	private static final BaselineEngineProperties PROPERTIES = new BaselineEngineProperties(
			"rule-v1", "feature-v1", "ontology-v1", "policy-v1", RADIUS_M, 20_000, 10, null, null);

	private static final UUID IN_POOL = new UUID(7L, 1L);

	private static final UUID FAR_AWAY = new UUID(7L, 2L);

	private final TripRepository tripRepository = mock(TripRepository.class);

	private final PlaceCandidateQueryService queryService = mock(PlaceCandidateQueryService.class);

	private final UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);

	private final TripSeedPlaceRepository seedPlaceRepository = mock(TripSeedPlaceRepository.class);

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("🔴 반경 밖의 「꼭 가고 싶은 장소」가 후보에 들어온다 — 예전에는 통째로 사라졌다")
	void 반경_밖의_꼭_가고_싶은_장소가_들어온다() {
		givenTrip();
		givenSeeds(mustVisit(FAR_AWAY));
		givenFarPlaceIsFindableByItsOwnCoordinates();

		EngineCandidateBatch batch = engine().generate(request());

		assertThat(batch.candidates().stream().map(EngineCandidate::placeId))
				.as("사용자가 이름을 적어 넣은 곳이 결과에 있어야 한다")
				.contains(FAR_AWAY);
	}

	@Test
	@DisplayName("사용자가 적은 곳과 공유 일정에서 따라온 곳을 이유 코드로 가른다")
	void 사용자가_적은_곳과_공유_복제를_가른다() {
		givenTrip();
		givenSeeds(mustVisit(FAR_AWAY), fromSharedItinerary(IN_POOL));
		givenFarPlaceIsFindableByItsOwnCoordinates();

		EngineCandidateBatch batch = engine().generate(request());

		assertThat(reasonsOf(batch, FAR_AWAY))
				.as("복제한 적 없는 사람에게 「공유 일정에서 왔다」가 붙으면 안 된다")
				.contains(SeedBoost.REASON_CODE_MUST_VISIT)
				.doesNotContain(SeedBoost.REASON_CODE);
		assertThat(reasonsOf(batch, IN_POOL))
				.contains(SeedBoost.REASON_CODE)
				.doesNotContain(SeedBoost.REASON_CODE_MUST_VISIT);
	}

	@Test
	@DisplayName("이미 후보에 있는 씨앗은 다시 조회하지 않는다")
	void 이미_후보에_있으면_다시_조회하지_않는다() {
		givenTrip();
		givenSeeds(mustVisit(IN_POOL));
		when(this.queryService.findCandidates(any())).thenReturn(response(List.of(inPoolCandidate())));

		engine().generate(request());

		verify(this.placeRepository, never()).findByPlaceIdIn(anyCollection());
	}

	@Test
	@DisplayName("좌표를 모르는 씨앗은 넣지 않는다 — 0 을 넣으면 「출발지에 붙어 있다」가 된다")
	void 좌표를_모르는_씨앗은_넣지_않는다() {
		givenTrip();
		givenSeeds(mustVisit(FAR_AWAY));
		when(this.queryService.findCandidates(any())).thenReturn(response(List.of(inPoolCandidate())));
		when(this.placeRepository.findByPlaceIdIn(anyCollection()))
				.thenReturn(List.of(place(FAR_AWAY, null, null)));

		EngineCandidateBatch batch = engine().generate(request());

		assertThat(batch.candidates().stream().map(EngineCandidate::placeId))
				.as("좌표가 없으면 거리 점수를 매길 수 없다 — 넣지 않고 넘어가되 일정 생성은 멈추지 않는다")
				.containsExactly(IN_POOL);
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private void givenTrip() {
		when(this.tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(Trip.builder()
				.tripId(TRIP_ID).createdBy(UUID.randomUUID().toString())
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.originLat(ORIGIN_LAT).originLng(ORIGIN_LNG).partySize(2).timezone("Asia/Seoul")
				.build()));
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE))
				.thenReturn(List.of());
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT))
				.thenReturn(List.of());
		when(this.codeMapRepository.findByIdUserInputKindAndIdUserInputCode(UserInputKind.PREFERENCE, "CATEGORY"))
				.thenReturn(List.of());
	}

	private void givenSeeds(TripSeedPlace... seeds) {
		when(this.seedPlaceRepository.findByTripId(TRIP_ID)).thenReturn(List.of(seeds));
	}

	/**
	 * 반경 조회에는 안 나오고, 자기 좌표를 중심으로 한 조회에만 나오는 장소를 만든다.
	 * 실제 DB 가 거리로 거르는 것을 대역에서 반경으로 흉내 낸다.
	 */
	private void givenFarPlaceIsFindableByItsOwnCoordinates() {
		when(this.queryService.findCandidates(any())).thenAnswer((invocation) -> {
			PlaceCandidateRequest asked = invocation.getArgument(0);
			boolean centeredOnFarPlace = Math.abs(asked.center().lng() - FAR_LNG) < 1e-6;
			return centeredOnFarPlace
					? response(List.of(farCandidate()))
					: response(List.of(inPoolCandidate()));
		});
		when(this.placeRepository.findByPlaceIdIn(anyCollection()))
				.thenReturn(List.of(place(FAR_AWAY, FAR_LAT, FAR_LNG)));
	}

	private static List<String> reasonsOf(EngineCandidateBatch batch, UUID placeId) {
		return batch.candidates().stream()
				.filter((candidate) -> candidate.placeId().equals(placeId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("결과에 그 장소가 없다: " + placeId))
				.reasonCodes();
	}

	private static TripSeedPlace mustVisit(UUID placeId) {
		// 사용자가 온보딩에서 적어 넣은 것 — 출처가 둘 다 비어 있다 (TripCreationService:195).
		return new TripSeedPlace(TRIP_ID, placeId.toString(), 1, null, null, Instant.now());
	}

	private static TripSeedPlace fromSharedItinerary(UUID placeId) {
		// 공유 일정을 복제한 것 — 원본 여행이 적혀 있다 (ShareCloneService:112).
		return new TripSeedPlace(TRIP_ID, placeId.toString(), 2, UUID.randomUUID().toString(), null, Instant.now());
	}

	private static PlaceCandidateResponse.Candidate inPoolCandidate() {
		return new PlaceCandidateResponse.Candidate(IN_POOL, "반경 안의 곳", "FOOD",
				ORIGIN_LAT, ORIGIN_LNG, 300L, List.of());
	}

	private static PlaceCandidateResponse.Candidate farCandidate() {
		return new PlaceCandidateResponse.Candidate(FAR_AWAY, "해운대해수욕장", "TOURIST",
				FAR_LAT, FAR_LNG, 9_200L, List.of());
	}

	private static Place place(UUID placeId, Double lat, Double lng) {
		return Place.imported(placeId, "해운대해수욕장", "TOURIST", "부산 해운대구", lat, lng,
				"TEST", placeId.toString(), OffsetDateTime.now(), null, "fixture");
	}

	private BaselineRecommendationEngine engine() {
		return new BaselineRecommendationEngine(this.tripRepository, this.queryService,
				new BaselineCandidateTranslator(PROPERTIES, this.codeMapRepository, this.objectMapper),
				new BaselineCandidateScorer(this.objectMapper), PROPERTIES,
				new PreferenceAlignmentWeights(null, null, null, null, null),
				this.codeMapRepository, this.seedPlaceRepository, this.placeRepository, Optional.empty(),
				emptyProvider(), emptyProvider());
	}

	/** 빈이 없는 슬라이스를 흉내 낸다 — 채점이 취향 벡터 없던 때와 완전히 같아야 한다. */
	private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyProvider() {
		@SuppressWarnings("unchecked")
		org.springframework.beans.factory.ObjectProvider<T> provider =
				mock(org.springframework.beans.factory.ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	private EngineRequest request() {
		return new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(TRIP_ID), 1,
				UUID.randomUUID(), null, null, null, 10);
	}

	private static PlaceCandidateResponse response(List<PlaceCandidateResponse.Candidate> candidates) {
		return new PlaceCandidateResponse(candidates, candidates.size(), 0, false,
				List.of("CENTER_RADIUS"), List.of(), false, List.of("fixture"));
	}
}
