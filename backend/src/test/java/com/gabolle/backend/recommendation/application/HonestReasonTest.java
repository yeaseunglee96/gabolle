package com.gabolle.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.recommendation.adapter.BaselineCandidateScorer;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties;
import com.gabolle.backend.recommendation.config.DiversityProperties;
import com.gabolle.backend.recommendation.config.PreferenceAlignmentWeights;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;
import com.gabolle.backend.recommendation.support.FakeRecommendationEngine;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 이유 표시 (S15P21E201-1638). 「출발지에서 가까움」이 모든 곳에 붙고 「가장 크게 기여」가 거의 늘 거리였다 —
 * 거리 비중 조사(K, 2026-09-25)에서 상위 20곳 80/80 · 68~76/80.
 *
 * <p>🔴 이 작업은 <b>표시만</b> 고친다. 순위가 한 곳이라도 바뀌면 그것은 이 작업의 결과가 아니라 사고다 —
 * {@link #rankingIsUnchanged} 가 고치기 전 코드로 뜬 순서를 지킨다.
 */
class HonestReasonTest {

	private static final BaselineEngineProperties.Weights WEIGHTS =
			new BaselineEngineProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10);

	/**
	 * 고치기 전 코드(back/dev 929d65b79)가 {@link #fixture()} 로 낸 순서 — {@code 번호@총점}. 거리 비중·채점식·다양성
	 * 재정렬을 <b>일부러</b> 바꾸는 작업이면 새 값으로 갈아 끼운다. 이 시험이 지키는 것은 「이유 표시를 바꾼 것만으로는
	 * 순위가 안 바뀐다」이다.
	 */
	private static final String BEFORE =
			"0@0.4910 2@0.4580 6@0.4900 4@0.4801 1@0.3660 7@0.3500 3@0.2430 5@0.2400";

	private final BaselineCandidateScorer scorer = new BaselineCandidateScorer(new ObjectMapper());

	@Test
	@DisplayName("🔴 순위는 한 곳도 안 바뀐다 — 고치기 전과 순서·총점이 같다")
	void rankingIsUnchanged() {
		CandidateAssembly assembly = assemble(scoreAll(fixture()), 8);

		assertThat(orderOf(assembly)).isEqualTo(BEFORE);
	}

	@Test
	@DisplayName("🔴 「출발지에서 가까움」은 채점에 쓴 거리가 1km 미만일 때만 — 999m 는 붙고 1,000m 부터는 안 붙는다")
	void nearOriginOnlyWithinOneKilometre() {
		CandidateAssembly assembly = assemble(scoreAll(fixture()), 8);

		// 픽스처의 0~4번이 1km 안(150·400·700·950·999m), 5번부터 1,000m 이상이다.
		reasonsOf(assembly).forEach((id, reasons) -> assertThat(reasons.contains("NEAR_ORIGIN"))
				.as("장소 %s 의 이유 %s", id, reasons)
				.isEqualTo(Integer.parseInt(id) <= 4));
	}

	@Test
	@DisplayName("🔴 「가장 크게 기여」는 평소보다 튄 축 — 모두 같은 거리면 거리가 아니라 갈린 축(테마)이다")
	void topContributorIsTheAxisThatStandsOut() {
		// 거리 기여는 0.27 과 0.24 로 거의 같고 테마만 갈린다. 절댓값으로 고르면 1번은 「거리」(0.27 > 테마 0.20)라
		// 순위를 설명하지 못한다. 1번은 거리도 평균(0.255)보다 조금 높다 — 그래도 더 튄 것은 테마(평균 0.10 보다 +0.10)다.
		EngineCandidate matchesTheme = scored(1, Map.of("distance", detail(0.30, 0.9), "interest", detail(0.20, 1.0)),
				0.47);
		EngineCandidate doesNot = scored(2, Map.of("distance", detail(0.30, 0.8), "interest", detail(0.20, 0.0)), 0.24);

		Map<String, List<String>> reasons = reasonsOf(assemble(List.of(matchesTheme, doesNot), 2));

		assertThat(reasons.get("1")).contains("TOP_CONTRIBUTOR_interest").doesNotContain("TOP_CONTRIBUTOR_distance");
		assertThat(reasons.get("2")).as("평균보다 높은 축이 하나도 없으면 붙이지 않는다 — 가장 덜 나쁜 축을 「가장 크게」라고 하면 거짓이다")
				.noneMatch((code) -> code.startsWith(RecommendationCodes.REASON_TOP_CONTRIBUTOR_PREFIX));
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	/**
	 * 해운대 근처 열 곳 — 거리(150m~4.7km)·갈래·인기가 섞여 있다. 바다·맛집을 고른 사람이다. 999m 와 1,000m 를 둘 다 넣어
	 * 가까움의 경계 양쪽을 본다.
	 */
	private static List<PlaceCandidateResponse.Candidate> fixture() {
		long[] distances = { 150, 400, 700, 950, 999, 1000, 1500, 2500, 3800, 4700 };
		String[] categories = { "FOOD", "CAFE_HEALING", "SEA_BEACH", "CULTURE_TEMPLE", "FOOD", "NATURE_WALK", "FOOD",
				"SEA_BEACH", "CAFE_HEALING", "FOOD" };
		Double[] popularity = { null, 0.9, null, null, 0.4, null, 0.8, null, null, 1.0 };
		List<PlaceCandidateResponse.Candidate> candidates = new ArrayList<>();
		for (int i = 0; i < distances.length; i++) {
			List<PlaceFeatureView> features = (popularity[i] == null) ? List.of()
					: List.of(new PlaceFeatureView("POPULARITY_SCORE", null, "ESTIMATED",
							new ObjectMapper().readTree(String.valueOf(popularity[i])), null, "FIXTURE"));
			candidates.add(new PlaceCandidateResponse.Candidate(new UUID(0x1638L, i), "장소 " + i, categories[i],
					35.15 + (i * 0.004), 129.15 + (i * 0.003), distances[i], features));
		}
		return candidates;
	}

	private List<EngineCandidate> scoreAll(List<PlaceCandidateResponse.Candidate> candidates) {
		PreferenceSnapshot seaAndFood = new PreferenceSnapshot(UUID.randomUUID().toString(),
				UUID.randomUUID().toString(), 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer("CATEGORY", "[\"SEA_BEACH\", \"FOOD\"]",
						PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), Instant.now());
		List<UserPlaceCodeMap> codeMap = List.of(codeMap("CATEGORY", "INTEREST_TAG"),
				codeMap("ATMOSPHERE", "ATMOSPHERE_TAG"), codeMap("FOOD_PREFERENCE", "CUISINE_TAG"));
		List<EngineCandidate> scored = new ArrayList<>();
		for (PlaceCandidateResponse.Candidate candidate : candidates) {
			scored.add(this.scorer.score(candidate, seaAndFood, List.of(), 5000, WEIGHTS,
					new PreferenceAlignmentWeights(null, null, null, null, null), codeMap, List.of(), List.of(), 0.05));
		}
		// 엔진이 넘기는 차례 그대로 — 점수 내림차순, 동점은 번호.
		scored.sort(Comparator.comparing(EngineCandidate::preRankScore, Comparator.reverseOrder())
				.thenComparing(EngineCandidate::placeId));
		return scored;
	}

	private CandidateAssembly assemble(List<EngineCandidate> candidates, int topK) {
		CandidateAssembler assembler = new CandidateAssembler(new JsonPayloads(JsonMapper.builder().build()),
				new SensitivePayloadGuard(), new DiversityReranker(new DiversityProperties(true, null, null, null)));
		return assembler.assemble(UUID.randomUUID(), FakeRecommendationEngine.batchOf(candidates), topK,
				UnknownExclusionThreshold.REQUIRED, ConstraintSeverity.REQUIRED, OffsetDateTime.now());
	}

	private static String orderOf(CandidateAssembly assembly) {
		List<String> order = new ArrayList<>();
		for (RecommendedPlace place : assembly.returnedItems()) {
			order.add(place.placeId().getLeastSignificantBits() + "@" + String.format("%.4f", place.finalScore()));
		}
		return String.join(" ", order);
	}

	private static Map<String, List<String>> reasonsOf(CandidateAssembly assembly) {
		Map<String, List<String>> reasons = new LinkedHashMap<>();
		for (RecommendedPlace place : assembly.returnedItems()) {
			reasons.put(String.valueOf(place.placeId().getLeastSignificantBits()), place.reasonCodes());
		}
		return reasons;
	}

	private static UserPlaceCodeMap codeMap(String userInputCode, String placeFeatureType) {
		UserPlaceCodeMap row = mock(UserPlaceCodeMap.class);
		when(row.getUserInputCode()).thenReturn(userInputCode);
		when(row.getPlaceFeatureType()).thenReturn(placeFeatureType);
		when(row.getMatchKind()).thenReturn(MatchKind.TAG_OVERLAP);
		return row;
	}

	private static EngineCandidate scored(int id, Map<String, Object> components, double score) {
		Map<String, Object> features = new LinkedHashMap<>();
		features.put("category", "FOOD");
		features.put("localityBucket", "3515:12915");
		return new EngineCandidate(new UUID(0x1638L, id), "BASELINE_PLACE_QUERY", ConstraintVerdict.PASS, List.of(),
				List.of(), null, features, components, score, List.of(), List.of());
	}

	private static Map<String, Object> detail(double weight, double value) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("weight", weight);
		detail.put("value", value);
		return detail;
	}
}
