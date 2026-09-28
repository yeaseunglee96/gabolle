package com.gabolle.backend.recommendation.adapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
	// ── 갈래를 안 고른 사람에게 여행마다 다르게 (S15P21E201-1463) ──────────────

	/** 같은 조건에서 tripId 만 바꿔 두 번 돌린다. */
	private List<UUID> keptFor(String tripId, List<PlaceCandidateResponse.Candidate> pool) {
		when(this.tripRepository.findById(tripId)).thenReturn(Optional.of(Trip.builder()
				.tripId(tripId).createdBy(UUID.randomUUID().toString())
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.originLat(35.15).originLng(129.05).partySize(2).timezone("Asia/Seoul")
				.build()));
		when(this.seedPlaceRepository.findByTripId(tripId)).thenReturn(List.of());
		when(this.queryService.findCandidates(any())).thenReturn(response(pool));
		EngineRequest req = new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(tripId), 1,
				UUID.randomUUID(), null, null, null, 10);
		return engine().generate(req).candidates().stream().map(EngineCandidate::placeId).toList();
	}

	private static List<PlaceCandidateResponse.Candidate> manyCandidates(int count) {
		List<PlaceCandidateResponse.Candidate> pool = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			pool.add(new PlaceCandidateResponse.Candidate(new UUID(7L, i), "후보" + i, "FOOD",
					35.15, 129.05, 100L + i, List.of()));
		}
		return pool;
	}

	@Test
	@DisplayName("🔴 같은 여행은 두 번 돌려도 같은 곳이 나온다 — 난수를 쓰면 이게 깨진다")
	void 같은_여행은_늘_같다() {
		String tripId = new UUID(9L, 1L).toString();
		List<PlaceCandidateResponse.Candidate> pool = manyCandidates(300);

		assertThat(keptFor(tripId, pool)).isEqualTo(keptFor(tripId, pool));
	}

	@Test
	@DisplayName("🔴 여행이 다르면 다른 곳이 나온다 — 갈래를 안 고른 사람에게도 개성이 생긴다")
	void 여행마다_다르다() {
		List<PlaceCandidateResponse.Candidate> pool = manyCandidates(300);

		List<UUID> a = keptFor(new UUID(9L, 2L).toString(), pool);
		List<UUID> b = keptFor(new UUID(9L, 3L).toString(), pool);

		assertThat(a).isNotEqualTo(b);
		assertThat(a).hasSize(KEEP);
		assertThat(b).hasSize(KEEP);
	}

	@Test
	@DisplayName("🔴 점수 맨 위쪽은 섞이지 않는다 — 가장 잘 맞는 곳을 다양성 때문에 잃지 않는다")
	void 맨_위쪽은_지킨다() {
		List<PlaceCandidateResponse.Candidate> pool = manyCandidates(300);

		// 앞쪽 절반(반올림)은 어느 여행에서든 같아야 한다.
		List<UUID> a = keptFor(new UUID(9L, 4L).toString(), pool);
		List<UUID> b = keptFor(new UUID(9L, 5L).toString(), pool);
		int anchor = Math.max(1, Math.round(KEEP * 0.5f));

		assertThat(a.subList(0, anchor)).isEqualTo(b.subList(0, anchor));
	}

	@Test
	@DisplayName("내보내는 순서는 점수 순이다 — 고르기만 섞고 순서는 안 섞는다")
	void 순서는_점수_순이다() {
		List<PlaceCandidateResponse.Candidate> pool = manyCandidates(300);

		EngineRequest req = new EngineRequest(UUID.randomUUID(), UUID.randomUUID(),
				UUID.fromString(TRIP_ID), 1, UUID.randomUUID(), null, null, null, 10);
		when(this.queryService.findCandidates(any())).thenReturn(response(pool));
		List<EngineCandidate> kept = engine().generate(req).candidates();

		List<Double> scores = kept.stream().map(EngineCandidate::preRankScore).toList();
		assertThat(scores).isSortedAccordingTo(Comparator.<Double>reverseOrder());
	}

	// ── 고른 갈래의 몫 ──────────────────────────────────────────────

	/**
	 * 운영 범위의 기울기를 줄여 흉내 낸다 — 밥집 300(가까워 점수가 높다) · 카페 5 · 바다 3 · 자연 3(멀다).
	 * 운영에서는 밥집이 인기도·예산 가산으로 앞섰다. 여기서는 거리로 앞서게 한다 — 재는 것은 채점이 아니라 자르기다.
	 */
	private static List<PlaceCandidateResponse.Candidate> skewedPool() {
		List<PlaceCandidateResponse.Candidate> pool = new ArrayList<>(manyCandidates(300));
		String[] others = { "CAFE_HEALING", "CAFE_HEALING", "CAFE_HEALING", "CAFE_HEALING", "CAFE_HEALING",
				"SEA_BEACH", "SEA_BEACH", "SEA_BEACH", "NATURE_WALK", "NATURE_WALK", "NATURE_WALK" };
		for (int i = 0; i < others.length; i++) {
			pool.add(new PlaceCandidateResponse.Candidate(new UUID(8L, i), others[i] + i, others[i],
					35.15, 129.05, 4000L + i, List.of()));
		}
		return pool;
	}

	/** 갈래를 고른 여행(취향 스냅샷에 CATEGORY 답)으로 돌려 남은 후보의 갈래를 센다. */
	private Map<String, Long> keptCategoriesChoosing(String categoriesJson) {
		UUID snapshotId = UUID.randomUUID();
		when(this.tripRepository.findSnapshotById(snapshotId.toString()))
				.thenReturn(Optional.of(snapshot("CATEGORY", categoriesJson)));
		when(this.queryService.findCandidates(any())).thenReturn(response(skewedPool()));
		EngineRequest req = new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(TRIP_ID), 1,
				snapshotId, null, null, null, 10);
		List<EngineCandidate> kept = engine().generate(req).candidates();
		assertThat(kept).hasSize(KEEP);
		assertThat(kept.stream().map(EngineCandidate::preRankScore).toList())
				.as("몫으로 골랐어도 내보내는 순서는 점수 순이다")
				.isSortedAccordingTo(Comparator.<Double>reverseOrder());
		return kept.stream().collect(Collectors.groupingBy(
				(c) -> String.valueOf(c.featureValues().get("category")), Collectors.counting()));
	}

	/** 운영(2026-09-24): 바다·맛집·자연을 고른 여행의 후보 200곳이 밥집 199 · 꼭 갈 곳 1 이었다. */
	@Test
	@DisplayName("🔴 맛집+바다+자연을 고르면 후보에 바다·자연이 들어온다 — 밥집이 상위를 다 채우지 않는다")
	void 고른_갈래는_몫을_받는다() {
		Map<String, Long> kept = keptCategoriesChoosing("[\"SEA_BEACH\", \"FOOD\", \"NATURE_WALK\"]");

		assertThat(kept.getOrDefault("SEA_BEACH", 0L)).isPositive();
		assertThat(kept.getOrDefault("NATURE_WALK", 0L)).isPositive();
		assertThat(kept.getOrDefault("FOOD", 0L)).isPositive();
	}

	@Test
	@DisplayName("맛집만 고르면 지금처럼 밥집 위주다 — 고르지 않은 바다·자연은 몫이 없다")
	void 맛집만_고르면_밥집_위주다() {
		Map<String, Long> kept = keptCategoriesChoosing("[\"FOOD\"]");

		assertThat(kept.getOrDefault("FOOD", 0L)).isGreaterThanOrEqualTo(KEEP - 1);
		assertThat(kept).doesNotContainKeys("SEA_BEACH", "NATURE_WALK");
	}

	@Test
	@DisplayName("바다·자연만 골라도 끼니를 채울 밥집이 남는다")
	void 밥집을_안_골라도_끼니_몫이_있다() {
		Map<String, Long> kept = keptCategoriesChoosing("[\"SEA_BEACH\", \"NATURE_WALK\"]");

		assertThat(kept.getOrDefault("FOOD", 0L)).isPositive();
		assertThat(kept.getOrDefault("SEA_BEACH", 0L) + kept.getOrDefault("NATURE_WALK", 0L)).isPositive();
	}

	@Test
	@DisplayName("🔴 아무것도 안 고르면 기존 다양성 규칙(-1463) 그대로다 — 몫을 안 나누고 뒤쪽을 여행마다 다르게 채운다")
	void 안_고르면_기존_규칙이다() {
		String tripA = new UUID(9L, 6L).toString();
		String tripB = new UUID(9L, 7L).toString();
		List<PlaceCandidateResponse.Candidate> pool = skewedPool();

		List<UUID> a = keptFor(tripA, pool);
		List<UUID> b = keptFor(tripB, pool);

		assertThat(a).isNotEqualTo(b);
		assertThat(a).as("안 골랐으면 몫이 없어 점수가 낮은 바다·자연·카페는 안 들어온다")
				.allMatch((id) -> id.getMostSignificantBits() == 7L);
	}

	// ── 테마 없이 설문만 있는 여행 — 섞지 않는다 (S15P21E201-1639) ─────────────────

	/**
	 * 고치기 전 코드(back/dev 929d65b79)가 낸 남은 후보 — {@code 상위비트:하위비트}. 설문도 테마도 없는 여행과 테마를 고른 여행은
	 * 이 작업 뒤에도 한 곳도 안 바뀌어야 한다. 섞기·몫 규칙을 <b>일부러</b> 바꾸는 작업이면 새 값으로 갈아 끼운다.
	 */
	private static final String NO_SURVEY_BEFORE = "7:0 7:1 7:2 7:3 7:4 7:5 7:7 7:18 7:22 7:29";

	private static final String THEME_BEFORE = "7:0 7:1 7:2 7:3 7:4 8:0 8:5 8:6 8:8 8:9";

	/** 여행 번호와 취향 스냅숏을 정해 돌리고, 남은 후보를 {@code 상위비트:하위비트} 로 늘어놓는다. */
	private String keptWith(String tripId, List<PlaceCandidateResponse.Candidate> pool, PreferenceSnapshot snapshot) {
		when(this.tripRepository.findById(tripId)).thenReturn(Optional.of(Trip.builder()
				.tripId(tripId).createdBy(UUID.randomUUID().toString())
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 3))
				.originLat(35.15).originLng(129.05).partySize(2).timezone("Asia/Seoul")
				.build()));
		when(this.seedPlaceRepository.findByTripId(tripId)).thenReturn(List.of());
		when(this.queryService.findCandidates(any())).thenReturn(response(pool));
		UUID snapshotId = UUID.randomUUID();
		when(this.tripRepository.findSnapshotById(snapshotId.toString())).thenReturn(Optional.ofNullable(snapshot));
		EngineRequest req = new EngineRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(tripId), 1,
				snapshotId, null, null, null, 10);
		return engine().generate(req).candidates().stream()
				.map((c) -> c.placeId().getMostSignificantBits() + ":" + c.placeId().getLeastSignificantBits())
				.collect(Collectors.joining(" "));
	}

	/** 답 여러 개 — {@code 차원, 값 JSON} 을 번갈아 준다. */
	private static PreferenceSnapshot answers(String... dimensionAndValue) {
		List<PreferenceSnapshot.PreferenceAnswer> list = new ArrayList<>();
		for (int i = 0; i < dimensionAndValue.length; i += 2) {
			list.add(new PreferenceSnapshot.PreferenceAnswer(dimensionAndValue[i], dimensionAndValue[i + 1],
					PreferenceSnapshot.AnswerStatus.SELECTED));
		}
		return new PreferenceSnapshot(UUID.randomUUID().toString(), TRIP_ID, 1, list, PersonalizationScope.TRIP,
				List.of(), java.time.Instant.now());
	}

	/** 운영 여행 답에서 흔한 설문 — 해산물 · 현지 5 · 경사 피함(2026-09-25 최근 열흘). */
	private static PreferenceSnapshot survey() {
		return answers("FOOD_PREFERENCE", "[\"SEAFOOD\"]", "LOCALITY", "5", "SLOPE_PREFERENCE", "\"AVOID\"");
	}

	@Test
	@DisplayName("🔴 설문도 테마도 없는 여행은 전과 같다 — 뒤쪽을 여행마다 섞는다(-1463)")
	void 설문이_없으면_전과_같다() {
		assertThat(keptWith(new UUID(9L, 11L).toString(), manyCandidates(300), null)).isEqualTo(NO_SURVEY_BEFORE);
	}

	@Test
	@DisplayName("🔴 테마를 고른 여행은 전과 같다 — 설문이 함께 있어도 갈래 몫이 먼저다")
	void 테마를_고르면_전과_같다() {
		PreferenceSnapshot themeAndSurvey = answers("CATEGORY", "[\"SEA_BEACH\", \"FOOD\", \"NATURE_WALK\"]",
				"FOOD_PREFERENCE", "[\"SEAFOOD\"]", "LOCALITY", "5");

		assertThat(keptWith(new UUID(9L, 12L).toString(), skewedPool(), themeAndSurvey)).isEqualTo(THEME_BEFORE);
	}

	@Test
	@DisplayName("🔴 테마 없이 설문만 있으면 섞지 않는다 — 여행이 달라도 같은 곳, 점수 순 상위 그대로")
	void 설문만_있으면_섞지_않는다() {
		List<PlaceCandidateResponse.Candidate> pool = manyCandidates(300);

		String a = keptWith(new UUID(9L, 13L).toString(), pool, survey());
		String b = keptWith(new UUID(9L, 14L).toString(), pool, survey());

		assertThat(a).as("같은 설문이면 여행이 달라도 같다 — 설문 효과가 섞기에 묻히지 않는다").isEqualTo(b);
		// 후보는 가까운 순으로 점수가 높다(표식이 없어 설문 항은 0) — 섞지 않으면 가장 가까운 열 곳이다.
		assertThat(a).isEqualTo("7:0 7:1 7:2 7:3 7:4 7:5 7:6 7:7 7:8 7:9");
	}

	@Test
	@DisplayName("🔴 채점에 안 쓰이는 답만 있으면 설문 없음과 같다 — 「상관없어요」·씀씀이는 순위를 가를 값이 아니다")
	void 채점에_안_쓰이는_답만이면_섞는다() {
		String tripId = new UUID(9L, 15L).toString();
		List<PlaceCandidateResponse.Candidate> pool = manyCandidates(300);
		PreferenceSnapshot noSignal = answers("SLOPE_PREFERENCE", "\"ALLOW\"", "SHADE_PREFERENCE",
				"\"NO_PREFERENCE\"", "SPEND_PROFILE", "\"MODERATE\"", "FOOD_PREFERENCE", "[]");

		assertThat(keptWith(tripId, pool, noSignal)).isEqualTo(keptWith(tripId, pool, null));
	}
}
