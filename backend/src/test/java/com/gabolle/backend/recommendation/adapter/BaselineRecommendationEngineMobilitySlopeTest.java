package com.gabolle.backend.recommendation.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.recommendation.application.CandidateAssembler;
import com.gabolle.backend.recommendation.application.CandidateAssembly;
import com.gabolle.backend.recommendation.application.DiversityReranker;
import com.gabolle.backend.recommendation.application.RecommendedPlace;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.DiversityProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 유아차를 「반드시」로 고른 여행 — S15P21E201-1625. 운영 여행 79da403f 의 일정에 봉래산·사자봉 조망 지점 같은 산이
 * 「미확인」 경고만 달고 들어갔다. 접근성 표식이 6,933곳 중 111줄뿐이라 거의 모든 곳이 「미확인」이었기 때문이다.
 *
 * <p>엔진이 채점하고 후보 고르기({@link CandidateAssembler})가 돌려줄 곳을 고르는 데까지 잇는다 — 「빠진다」만
 * 보면 안 되고 <b>그 자리가 다음 후보로 채워지는지</b>까지 봐야 한다. 경사 값은 새 산출물(place-slope-by-id)의
 * 운영 값이다.
 */
class BaselineRecommendationEngineMobilitySlopeTest {

	private static final String TRIP_ID = UUID.randomUUID().toString();

	private static final UUID CONSTRAINT_SNAPSHOT = UUID.randomUUID();

	private static final BaselineEngineProperties PROPERTIES = new BaselineEngineProperties(
			"rule-v1", "feature-v1", "ontology-v1", "policy-v1", 5000, 20_000, 10, null, null);

	/** 가장 가깝다 — 경사를 안 보면 1순위다. */
	private static final UUID BONGNAESAN = new UUID(25L, 1L);

	/** 사용자가 이름을 적어 넣은 산. */
	private static final UUID SAJABONG = new UUID(25L, 2L);

	private static final UUID GALMAETGIL = new UUID(25L, 3L);

	private static final UUID YONGDUSAN = new UUID(25L, 4L);

	private static final UUID CAFE = new UUID(25L, 5L);

	private static final UUID RESTAURANT = new UUID(25L, 6L);

	private final TripRepository tripRepository = mock(TripRepository.class);

	private final PlaceCandidateQueryService queryService = mock(PlaceCandidateQueryService.class);

	private final UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);

	private final TripSeedPlaceRepository seedPlaceRepository = mock(TripSeedPlaceRepository.class);

	private final CandidateAssembler assembler = new CandidateAssembler(
			new JsonPayloads(JsonMapper.builder().build()), new SensitivePayloadGuard(),
			new DiversityReranker(new DiversityProperties(null, null, null, null)));

	@Test
	@DisplayName("🔴 유아차 「반드시」 — 가파른 산은 빠지고 그 자리를 다음 후보가 채운다. 사용자가 적은 산은 경고를 달고 남는다")
	void steepPlacesDropOutAndOthersFillIn() {
		givenStrollerTrip(TripConstraint.Severity.HARD);

		EngineCandidateBatch batch = engine().generate(request(4));
		CandidateAssembly assembly = this.assembler.assemble(UUID.randomUUID(), batch, 4,
				UnknownExclusionThreshold.REQUIRED, ConstraintSeverity.REQUIRED, OffsetDateTime.now());

		List<UUID> returned = assembly.returnedItems().stream().map(RecommendedPlace::placeId).toList();
		assertThat(returned).as("가장 가까운 산이지만 16% 라 빠진다").doesNotContain(BONGNAESAN);
		assertThat(returned).as("사용자가 적어 넣은 곳은 추정값으로 지우지 않는다").contains(SAJABONG);
		assertThat(returned).as("빠진 자리를 다음 후보가 채운다").hasSize(4);
		assertThat(warningsOf(batch, SAJABONG)).contains("SLOPE_OVER_LIMIT");
	}

	@Test
	@DisplayName("유아차 「되도록」 — 가파른 산도 빠지지 않고 경고를 단다")
	void preferredOnlyWarns() {
		givenStrollerTrip(TripConstraint.Severity.SOFT);

		EngineCandidateBatch batch = engine().generate(request(4));
		CandidateAssembly assembly = this.assembler.assemble(UUID.randomUUID(), batch, 4,
				UnknownExclusionThreshold.REQUIRED, ConstraintSeverity.REQUIRED, OffsetDateTime.now());

		assertThat(assembly.returnedItems().stream().map(RecommendedPlace::placeId)).contains(BONGNAESAN);
		assertThat(warningsOf(batch, BONGNAESAN)).contains("SLOPE_OVER_LIMIT");
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private void givenStrollerTrip(TripConstraint.Severity severity) {
		when(this.tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(Trip.builder()
				.tripId(TRIP_ID).createdBy(UUID.randomUUID().toString())
				.startDate(LocalDate.of(2026, 10, 15)).finishDate(LocalDate.of(2026, 10, 15))
				.originLat(35.0990).originLng(129.0320).partySize(4).timezone("Asia/Seoul")
				.build()));
		String operator = (severity == TripConstraint.Severity.HARD) ? "EXCLUDES" : null;
		when(this.tripRepository.findConstraintsBySnapshotId(CONSTRAINT_SNAPSHOT.toString())).thenReturn(List.of(
				new TripConstraint(UUID.randomUUID().toString(), TRIP_ID, "MOBILITY", "STROLLER", severity, operator,
						"true", null, TripConstraint.EvidenceStatus.VERIFIED, TripConstraint.AnswerStatus.SELECTED,
						PersonalizationScope.TRIP, null)));
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE))
				.thenReturn(List.of());
		UserPlaceCodeMap mobility = mock(UserPlaceCodeMap.class);
		when(mobility.getUserInputCode()).thenReturn("MOBILITY");
		when(mobility.getPlaceFeatureType()).thenReturn("ACCESSIBILITY_TAG");
		when(mobility.getMatchKind()).thenReturn(MatchKind.HARD_FILTER);
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT))
				.thenReturn(List.of(mobility));
		when(this.codeMapRepository.findByIdUserInputKindAndIdUserInputCode(UserInputKind.PREFERENCE, "CATEGORY"))
				.thenReturn(List.of());
		when(this.seedPlaceRepository.findByTripId(TRIP_ID)).thenReturn(List.of(
				new TripSeedPlace(TRIP_ID, SAJABONG.toString(), 1, null, null, Instant.now())));
		List<PlaceCandidateResponse.Candidate> candidates = new ArrayList<>(List.of(
				candidate(BONGNAESAN, "봉래산(부산)", "NATURE_WALK", 100L, 16.0),
				candidate(SAJABONG, "사자봉 조망 지점", "NATURE_WALK", 900L, 19.1),
				candidate(GALMAETGIL, "[부산 갈맷길] 2코스 2구간", "NATURE_WALK", 400L, 2.8),
				candidate(YONGDUSAN, "용두산공원", "NATURE_WALK", 500L, 3.9),
				candidate(CAFE, "카페", "CAFE_HEALING", 600L, 2.0),
				candidate(RESTAURANT, "밥집", "FOOD", 700L, 3.0)));
		when(this.queryService.findCandidates(any())).thenReturn(new PlaceCandidateResponse(candidates,
				candidates.size(), 0, false, List.of("CENTER_RADIUS"), List.of(), false, List.of("fixture")));
	}

	private static PlaceCandidateResponse.Candidate candidate(UUID placeId, String name, String category,
			long distanceM, double slopePercent) {
		PlaceFeatureView slope = new PlaceFeatureView("SLOPE_PERCENT", null, "ESTIMATED",
				new ObjectMapper().readTree("{\"score\": " + slopePercent + ", \"stat\": \"p50\"}"), null,
				"DERIVED_SLOPE");
		return new PlaceCandidateResponse.Candidate(placeId, name, category, 35.10, 129.03, distanceM, List.of(slope));
	}

	private static List<String> warningsOf(EngineCandidateBatch batch, UUID placeId) {
		return batch.candidates().stream()
				.filter(c -> c.placeId().equals(placeId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("후보에 없다: " + placeId))
				.warningCodes();
	}

	private BaselineRecommendationEngine engine() {
		return new BaselineRecommendationEngine(this.tripRepository, this.queryService,
				new BaselineCandidateTranslator(PROPERTIES, this.codeMapRepository, new ObjectMapper()),
				new BaselineCandidateScorer(new ObjectMapper()), PROPERTIES,
				new PreferenceAlignmentWeights(null, null, null, null, null),
				this.codeMapRepository, this.seedPlaceRepository, mock(PlaceRepository.class), Optional.empty(),
				emptyProvider(), emptyProvider());
	}

	private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyProvider() {
		@SuppressWarnings("unchecked")
		org.springframework.beans.factory.ObjectProvider<T> provider =
				mock(org.springframework.beans.factory.ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	private EngineRequest request(int topK) {
		return new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(TRIP_ID), 1,
				null, CONSTRAINT_SNAPSHOT, null, null, topK);
	}
}
