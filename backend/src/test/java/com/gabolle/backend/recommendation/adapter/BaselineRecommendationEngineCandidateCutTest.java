package com.gabolle.backend.recommendation.adapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 후보를 자르는 자리가 채점 뒤인가를 본다. {@code candidateLimit} 이 장소 조회로 그대로
 * 넘어가면 그 조회는 점수를 모르므로 거리순으로 잘라, 상한이 "가까운 순 N곳만 채점 대상" 이
 * 된다. 그러면 멀지만 취향에 맞는 장소는 점수를 매길 기회조차 없다.
 *
 * 그래서 재는 것은 점수 계산이 아니라 순서다 — 조회에 무엇을 요구하는가, 자르기가 채점
 * 앞인가 뒤인가.
 */
class BaselineRecommendationEngineCandidateCutTest {

	private static final String TRIP_ID = UUID.randomUUID().toString();

	private static final int RADIUS_M = 5000;

	private static final int SCAN_LIMIT = 20_000;

	private static final int KEEP = 10;

	private static final BaselineEngineProperties PROPERTIES = new BaselineEngineProperties(
			"rule-v1", "feature-v1", "ontology-v1", "policy-v1", RADIUS_M, SCAN_LIMIT, KEEP, null, null);

	private final TripRepository tripRepository = mock(TripRepository.class);

	private final PlaceCandidateQueryService queryService = mock(PlaceCandidateQueryService.class);

	private final UserPlaceCodeMapRepository codeMapRepository = mock(UserPlaceCodeMapRepository.class);

	private final TripSeedPlaceRepository seedPlaceRepository = mock(TripSeedPlaceRepository.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 대역을 미리 만들어 둔다. {@code when(...)} 안에서 또 {@code when(...)} 을 부르면
	 * Mockito 가 {@code UnfinishedStubbingException} 을 던진다.
	 */
	private final List<UserPlaceCodeMap> foodPreferenceCodeMap = List.of(codeMap("FOOD_PREFERENCE", "CUISINE_TAG"));

	private BaselineRecommendationEngine engine() {
		when(this.tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(Trip.builder()
				.tripId(TRIP_ID).createdBy(UUID.randomUUID().toString())
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.originLat(35.15).originLng(129.05).partySize(2).timezone("Asia/Seoul")
				.build()));
		when(this.seedPlaceRepository.findByTripId(TRIP_ID)).thenReturn(List.of());
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE))
				.thenReturn(this.foodPreferenceCodeMap);
		when(this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.CONSTRAINT))
				.thenReturn(List.of());
		when(this.codeMapRepository.findByIdUserInputKindAndIdUserInputCode(UserInputKind.PREFERENCE, "CATEGORY"))
				.thenReturn(List.of());

		return new BaselineRecommendationEngine(this.tripRepository, this.queryService,
				new BaselineCandidateTranslator(PROPERTIES, this.codeMapRepository, this.objectMapper),
				new BaselineCandidateScorer(this.objectMapper), PROPERTIES,
				new PreferenceAlignmentWeights(null, null, null, null, null),
				this.codeMapRepository, this.seedPlaceRepository, mock(PlaceRepository.class), Optional.empty(),
				// 벡터 빈이 없는 자리 — 채점이 벡터 없던 때와 완전히 같아야 한다
				emptyProvider(), emptyProvider());
	}

	/** 빈이 없는 슬라이스를 흉내 낸다 — 아홉 슬라이스 중 preference 를 스캔하는 것이 사실상 없다. */
	private static <T> org.springframework.beans.factory.ObjectProvider<T> emptyProvider() {
		@SuppressWarnings("unchecked")
		org.springframework.beans.factory.ObjectProvider<T> provider =
				org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
		org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	@Test
	@DisplayName("🔴 장소 조회에는 남길 수(10)가 아니라 채점 대상 상한(20000)을 요구한다")
	void 조회에는_채점대상_상한을_요구한다() {
		// 후보 하나를 넣어 준다. 재는 것은 조회에 넘어간 상한이지 결과가 아닌데, 빈 응답은
		// "고른 갈래에 맞는 곳이 없다" 는 예외가 되어 여기까지 못 온다.
		when(this.queryService.findCandidates(any())).thenReturn(response(List.of(
				new PlaceCandidateResponse.Candidate(new UUID(3L, 1L), "아무 곳", "FOOD", 35.15, 129.05, 100L,
						List.of()))));

		engine().generate(request());

		ArgumentCaptor<PlaceCandidateRequest> captured = ArgumentCaptor.forClass(PlaceCandidateRequest.class);
		org.mockito.Mockito.verify(this.queryService).findCandidates(captured.capture());
		assertThat(captured.getValue().limit()).isEqualTo(SCAN_LIMIT);
		assertThat(captured.getValue().radiusM()).isEqualTo(RADIUS_M);
	}

	@Test
	@DisplayName("🔴 가장 먼 곳이라도 취향에 맞으면 남는다 — 거리로 먼저 자르면 이 장소는 채점조차 안 된다")
	void 멀지만_취향에_맞는_곳이_살아남는다() {
		// 500곳. 가까운 순으로 1,000m 부터 1m 씩 멀어진다.
		// 정답은 가장 먼 한 곳이고, 그 한 곳만 사용자가 고른 음식 태그를 갖는다.
		List<PlaceCandidateResponse.Candidate> pool = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			pool.add(new PlaceCandidateResponse.Candidate(new UUID(0L, i), "후보" + i, "FOOD",
					35.15, 129.05, 1000L + i, List.of()));
		}
		UUID farthestButMatching = new UUID(0L, 500L);
		pool.add(new PlaceCandidateResponse.Candidate(farthestButMatching, "멀지만 취향에 맞는 곳", "FOOD",
				35.15, 129.05, 1500L,
				List.of(new PlaceFeatureView("CUISINE_TAG", "PORK_SOUP", "ESTIMATED",
						this.objectMapper.readTree("true"), null, "FIXTURE"))));

		when(this.queryService.findCandidates(any())).thenReturn(response(pool));
		when(this.tripRepository.findSnapshotById(any())).thenReturn(Optional.of(
				snapshot("FOOD_PREFERENCE", "{\"codes\": [\"PORK_SOUP\"]}")));

		EngineCandidateBatch batch = engine().generate(request());

		List<UUID> kept = batch.candidates().stream().map(EngineCandidate::placeId).toList();
		assertThat(kept).hasSize(KEEP);
		assertThat(kept).contains(farthestButMatching);
		// 취향 태그가 총점 0.15 를 더하고, 501곳 중 1m 씩의 거리 차이는 0.30/5000 밖에 안 된다.
		// 그래서 이 장소가 1등이어야 한다 — 점수가 순서를 정한다는 뜻이다.
		assertThat(kept.get(0)).isEqualTo(farthestButMatching);
	}

	@Test
	@DisplayName("남기는 수는 candidateLimit 그대로다 — 저장되는 후보 행 수가 늘지 않는다")
	void 남기는_수는_candidateLimit_그대로다() {
		List<PlaceCandidateResponse.Candidate> pool = new ArrayList<>();
		for (int i = 0; i < 300; i++) {
			pool.add(new PlaceCandidateResponse.Candidate(new UUID(1L, i), "후보" + i, "FOOD",
					35.15, 129.05, 100L + i, List.of()));
		}
		when(this.queryService.findCandidates(any())).thenReturn(response(pool));

		assertThat(engine().generate(request()).candidates()).hasSize(KEEP);
	}

	@Test
	@DisplayName("후보가 남길 수보다 적으면 그대로 전부 돌려준다")
	void 후보가_적으면_전부_돌려준다() {
		List<PlaceCandidateResponse.Candidate> pool = List.of(
				new PlaceCandidateResponse.Candidate(new UUID(2L, 1L), "한 곳", "FOOD", 35.15, 129.05, 100L,
						List.of()));
		when(this.queryService.findCandidates(any())).thenReturn(response(pool));

		assertThat(engine().generate(request()).candidates()).hasSize(1);
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private EngineRequest request() {
		return new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(TRIP_ID), 1,
				UUID.randomUUID(), null, null, null, 10);
	}

	private static PlaceCandidateResponse response(List<PlaceCandidateResponse.Candidate> candidates) {
		return new PlaceCandidateResponse(candidates, candidates.size(), 0, false,
				List.of("CENTER_RADIUS"), List.of(), false, List.of("fixture"));
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(MatchKind.TAG_OVERLAP);
		return row;
	}

	private static PreferenceSnapshot snapshot(String dimension, String valueJson) {
		return new PreferenceSnapshot(UUID.randomUUID().toString(), TRIP_ID, 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer(dimension, valueJson,
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), java.time.Instant.now());
	}
}
