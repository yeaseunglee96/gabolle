package com.gabolle.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.common.json.JsonPayloads;
import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.config.DiversityProperties;
import com.gabolle.backend.recommendation.domain.ConstraintSeverity;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.UnknownExclusionThreshold;
import com.gabolle.backend.recommendation.support.FakeRecommendationEngine;

import tools.jackson.databind.json.JsonMapper;

/**
 * 다양성 재정렬과 기여 축. 도커 없이 도는 단위 테스트다. 실데이터 분포로는 아직 잴 수 없어
 * 독점 상황을 여기서 만들어 놓고 재정렬이 그것을 깨는지 본다.
 */
class DiversityRerankTest {

	private final OffsetDateTime now = OffsetDateTime.now();

	@Test
	@DisplayName("🔴 같은 카테고리가 상위를 독점하지 않는다 — 카페 다섯 사이로 식당이 올라온다")
	void 같은_카테고리_독점이_깨진다() {
		List<EngineCandidate> candidates = new ArrayList<>();
		// 점수가 높은 순서로 카페 다섯, 그 아래 식당 둘. 점수만으로는 1~5위가 전부 카페다.
		for (int i = 0; i < 5; i++) {
			candidates.add(candidate("CAFE", 35.15, 129.05, 0.90 - (i * 0.01)));
		}
		candidates.add(candidate("RESTAURANT", 35.20, 129.10, 0.70));
		candidates.add(candidate("RESTAURANT", 35.21, 129.11, 0.69));

		CandidateAssembly assembly = assemble(candidates, 3, enabled());

		List<String> topCategories = categoriesOf(assembly, candidates);
		assertThat(topCategories).contains("RESTAURANT");
		// 상위 3개가 전부 한 카테고리이던 것이 깨졌다.
		assertThat(assembly.returnedCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("🔴 독점 지표가 재정렬 뒤에 내려간다 — before/after 가 함께 남는다")
	void 독점_지표가_내려간다() {
		// 점수를 가깝게 둔다. 벌점(기본 0.05+0.05)은 점수 차가 그보다 작을 때만 순서를
		// 바꾼다 — 0.30 차이는 못 뒤집고, 그것이 맞는 동작이다.
		List<EngineCandidate> candidates = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			candidates.add(candidate("CAFE", 35.15, 129.05, 0.90 - (i * 0.01)));
		}
		candidates.add(candidate("MUSEUM", 35.30, 129.20, 0.84));

		CandidateAssembly assembly = assemble(candidates, 3, enabled());

		Map<String, Object> metrics = assembly.diversityMetrics();
		assertThat(metrics.get("applied")).isEqualTo(true);
		double before = share(metrics, "before");
		double after = share(metrics, "after");
		assertThat(before).isEqualTo(1.0);
		assertThat(after).isLessThan(before);
	}

	@Test
	@DisplayName("점수는 재정렬로 바뀌지 않는다 — 순서만 바뀐다")
	void 점수는_바뀌지_않는다() {
		EngineCandidate first = candidate("CAFE", 35.15, 129.05, 0.90);
		EngineCandidate second = candidate("CAFE", 35.15, 129.05, 0.85);
		EngineCandidate third = candidate("PARK", 35.40, 129.30, 0.50);

		CandidateAssembly assembly = assemble(List.of(first, second, third), 3, enabled());

		assertThat(assembly.returnedItems()).extracting(RecommendedPlace::finalScore)
				.containsExactlyInAnyOrder(0.90, 0.85, 0.50);
	}

	@Test
	@DisplayName("🔴 original_rank 와 final_rank 가 함께 남는다 — 무엇이 움직였는지 되짚을 수 있다")
	void 두_순위가_함께_남는다() {
		EngineCandidate cafeTop = candidate("CAFE", 35.15, 129.05, 0.90);
		EngineCandidate cafeSecond = candidate("CAFE", 35.15, 129.05, 0.89);
		EngineCandidate park = candidate("PARK", 35.40, 129.30, 0.80);

		CandidateAssembly assembly = assemble(List.of(cafeTop, cafeSecond, park), 3, enabled());

		RecommendationCandidate movedRow = row(assembly, cafeSecond.placeId());
		// 점수로는 2위였는데 같은 카테고리 벌점 때문에 뒤로 갔다.
		assertThat(movedRow.getOriginalRank()).isEqualTo(2);
		assertThat(movedRow.getFinalRank()).isEqualTo(3);
		assertThat(List.of(movedRow.getReasonCodes())).contains("DIVERSITY_RERANKED");
	}

	@Test
	@DisplayName("🔴 다양성 키가 없는 후보는 서로 깎지 않는다 — 표식이 비어 있다고 뒤로 밀리면 안 된다")
	void 키가_없으면_순서가_그대로다() {
		List<EngineCandidate> candidates = List.of(
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.9),
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.8),
				FakeRecommendationEngine.passing(UUID.randomUUID(), 0.7));

		CandidateAssembly assembly = assemble(candidates, 3, enabled());

		assertThat(assembly.returnedItems()).extracting(RecommendedPlace::finalRank).containsExactly(1, 2, 3);
		assertThat(assembly.returnedItems()).extracting(RecommendedPlace::finalScore)
				.containsExactly(0.9, 0.8, 0.7);
	}

	@Test
	@DisplayName("enabled=false 면 점수 순서가 그대로다 — 되돌리는 길")
	void 끄면_점수_순서다() {
		EngineCandidate a = candidate("CAFE", 35.15, 129.05, 0.90);
		EngineCandidate b = candidate("CAFE", 35.15, 129.05, 0.89);
		EngineCandidate c = candidate("PARK", 35.40, 129.30, 0.10);

		CandidateAssembly assembly = assemble(List.of(a, b, c), 3,
				new DiversityProperties(false, null, null, null));

		assertThat(assembly.returnedItems()).extracting(RecommendedPlace::placeId)
				.containsExactly(a.placeId(), b.placeId(), c.placeId());
		assertThat(assembly.diversityMetrics().get("applied")).isEqualTo(false);
	}

	@Test
	@DisplayName("같은 입력이면 같은 순서다 — 재현 가능해야 한다")
	void 같은_입력이면_같은_순서다() {
		List<EngineCandidate> candidates = List.of(
				candidate("CAFE", 35.15, 129.05, 0.90),
				candidate("CAFE", 35.15, 129.05, 0.90),
				candidate("CAFE", 35.16, 129.06, 0.90),
				candidate("PARK", 35.40, 129.30, 0.90));

		List<UUID> first = assemble(candidates, 4, enabled()).returnedItems().stream()
				.map(RecommendedPlace::placeId).toList();
		List<UUID> second = assemble(candidates, 4, enabled()).returnedItems().stream()
				.map(RecommendedPlace::placeId).toList();

		assertThat(first).isEqualTo(second);
	}

	@Test
	@DisplayName("🔴 기여 축이 반환 후보에 남는다 — S15P21E201-205 가 읽는 자리")
	void 기여_축이_남는다() {
		EngineCandidate candidate = scored("CAFE", 35.15, 129.05, 0.6,
				Map.of("distance", detail(0.30, 1.0), "interest", detail(0.20, 0.5)));

		CandidateAssembly assembly = assemble(List.of(candidate), 1, enabled());

		RecommendationCandidate stored = row(assembly, candidate.placeId());
		assertThat(stored.getScoreComponents()).contains("reasonRanking");
		// distance 기여 0.30 이 interest 0.10 보다 크므로 절대 기여 1위는 distance 다.
		assertThat(List.of(stored.getReasonCodes())).contains("TOP_CONTRIBUTOR_distance");
	}

	@Test
	@DisplayName("반환되지 않은 후보에는 기여 축을 붙이지 않는다 — 대조 기준선이 다르다")
	void 안_반환된_후보에는_기여_축이_없다() {
		EngineCandidate top = scored("CAFE", 35.15, 129.05, 0.9,
				Map.of("distance", detail(0.30, 1.0)));
		EngineCandidate cut = scored("PARK", 35.40, 129.30, 0.1,
				Map.of("distance", detail(0.30, 0.2)));

		CandidateAssembly assembly = assemble(List.of(top, cut), 1, enabled());

		assertThat(row(assembly, top.placeId()).getScoreComponents()).contains("reasonRanking");
		assertThat(row(assembly, cut.placeId()).getScoreComponents()).doesNotContain("reasonRanking");
	}

	@Test
	@DisplayName("🔴 같은 음식이 상위를 독점하지 않는다 — 돼지국밥 넷 사이로 다른 음식이 올라온다")
	void 같은_음식이_독점하지_않는다() {
		// S15P21E201-1450 실측: 「맛집」을 고르면 7곳 중 6곳이 돼지국밥이었다. 카테고리는
		// 전부 FOOD 라 그 축으로는 서로 안 갈리고, 갈리는 것은 음식 표식뿐이다.
		List<EngineCandidate> candidates = List.of(
				food(35.10, 129.01, 0.90, "PORK_SOUP"),
				food(35.11, 129.02, 0.90, "PORK_SOUP"),
				food(35.12, 129.03, 0.90, "PORK_SOUP"),
				food(35.13, 129.04, 0.90, "PORK_SOUP"),
				food(35.14, 129.05, 0.85, "SEAFOOD"));

		CandidateAssembly assembly = assemble(candidates, 5, enabled());

		assertThat(cuisinesOf(assembly, candidates))
				.as("점수가 낮은 회집이 돼지국밥 사이로 올라와야 한다 — 안 올라오면 축이 안 걸린 것이다")
				.containsSubsequence("PORK_SOUP", "SEAFOOD", "PORK_SOUP");
	}

	@Test
	@DisplayName("🔴 음식 표식이 없는 가게끼리는 이 축으로 안 깎인다 — 한식집과 중식집이 서로를 밀면 안 된다")
	void 표식_없는_가게끼리는_안_깎인다() {
		// 지금 어휘는 넷뿐이라(회·카페/디저트·돼지국밥·밀면) 한식 일반에는 표식이 안 붙는다.
		// 빈 것을 한 덩어리로 묶으면 서로 다른 음식이 「같은 음식」으로 깎인다.
		EngineCandidate hansik = food(35.10, 129.01, 0.90);
		EngineCandidate jungsik = food(35.20, 129.11, 0.80);
		EngineCandidate park = candidate("PARK", 35.40, 129.30, 0.72);

		CandidateAssembly assembly = assemble(List.of(hansik, jungsik, park), 3, enabled());

		// 표식을 묶었다면 둘째 음식점이 0.80 - 0.05(갈래) - 0.10(음식) = 0.65 로 공원(0.72)
		// 밑으로 내려간다. 안 묶으므로 0.75 로 공원보다 위에 남는다.
		assertThat(assembly.returnedItems().stream().map(RecommendedPlace::placeId).toList())
				.containsExactly(hansik.placeId(), jungsik.placeId(), park.placeId());
	}

	@Test
	@DisplayName("표식이 둘인 가게는 둘 다 세어진다 — 하나만 세면 남은 하나로 같은 음식이 다시 올라온다")
	void 표식이_둘이면_둘_다_센다() {
		EngineCandidate cafe = food(35.10, 129.01, 0.90, "CAFE_DESSERT");
		EngineCandidate both = food(35.20, 129.11, 0.85, "CAFE_DESSERT", "PORK_SOUP");
		EngineCandidate pork = food(35.30, 129.21, 0.80, "PORK_SOUP");

		CandidateAssembly assembly = assemble(List.of(cafe, both, pork), 3, enabled());

		// 카페를 먼저 뽑은 뒤: both 는 CAFE_DESSERT 가 겹쳐 0.85-0.05-0.10 = 0.70,
		// pork 는 겹치는 표식이 없어 0.80-0.05 = 0.75 다. 점수가 낮은 pork 가 먼저 온다.
		assertThat(assembly.returnedItems().stream().map(RecommendedPlace::placeId).toList())
				.containsExactly(cafe.placeId(), pork.placeId(), both.placeId());
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private static DiversityProperties enabled() {
		return new DiversityProperties(true, null, null, null);
	}

	private CandidateAssembly assemble(List<EngineCandidate> candidates, int topK,
			DiversityProperties properties) {
		CandidateAssembler assembler = new CandidateAssembler(
				new JsonPayloads(JsonMapper.builder().build()), new SensitivePayloadGuard(),
				new DiversityReranker(properties));
		return assembler.assemble(UUID.randomUUID(), FakeRecommendationEngine.batchOf(candidates), topK,
				UnknownExclusionThreshold.REQUIRED, ConstraintSeverity.REQUIRED, this.now);
	}

	private static EngineCandidate candidate(String category, double lat, double lng, double score) {
		return scored(category, lat, lng, score, Map.of("base", detail(1.0, score)));
	}

	/**
	 * 음식점 하나. 갈래는 언제나 {@code FOOD} 다 — 그것이 이 축이 필요한 이유이기도 하다.
	 *
	 * @param cuisines 음식 표식. 안 주면 표식 없는 가게다(지금 어휘로 못 가르는 대부분이 그렇다)
	 */
	private static EngineCandidate food(double lat, double lng, double score, String... cuisines) {
		Map<String, Object> features = new LinkedHashMap<>();
		features.put("category", "FOOD");
		features.put("localityBucket", Math.round(lat * 100) + ":" + Math.round(lng * 100));
		features.put("cuisine", List.of(cuisines));
		return new EngineCandidate(UUID.randomUUID(), "BASELINE_PLACE_QUERY", ConstraintVerdict.PASS,
				List.of(), List.of(), null, features, Map.of("base", detail(1.0, score)), score,
				List.of(), List.of());
	}

	private static EngineCandidate scored(String category, double lat, double lng, double score,
			Map<String, Object> scoreComponents) {
		Map<String, Object> features = new LinkedHashMap<>();
		features.put("category", category);
		// 좌표가 아니라 굵은 구역 번호다 — 정밀 좌표는 feature_values 에 들어가지 않는다.
		features.put("localityBucket", Math.round(lat * 100) + ":" + Math.round(lng * 100));
		return new EngineCandidate(UUID.randomUUID(), "BASELINE_PLACE_QUERY", ConstraintVerdict.PASS,
				List.of(), List.of(), null, features, scoreComponents, score, List.of(), List.of());
	}

	private static Map<String, Object> detail(double weight, double value) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("weight", weight);
		detail.put("value", value);
		return detail;
	}

	private static RecommendationCandidate row(CandidateAssembly assembly, UUID placeId) {
		return assembly.candidates().stream()
				.filter((candidate) -> candidate.getPlaceId().equals(placeId))
				.findFirst()
				.orElseThrow();
	}

	/** 반환된 차례대로 음식 표식을 늘어놓는다. 표식이 없는 가게는 {@code "-"} 다. */
	@SuppressWarnings("unchecked")
	private List<String> cuisinesOf(CandidateAssembly assembly, List<EngineCandidate> pool) {
		List<String> cuisines = new ArrayList<>();
		for (RecommendedPlace item : assembly.returnedItems()) {
			pool.stream()
					.filter((c) -> c.placeId().equals(item.placeId()))
					.findFirst()
					.ifPresent((c) -> {
						List<String> tags = (List<String>) c.featureValues().get("cuisine");
						cuisines.add((tags == null || tags.isEmpty()) ? "-" : tags.get(0));
					});
		}
		return cuisines;
	}

	private List<String> categoriesOf(CandidateAssembly assembly, List<EngineCandidate> pool) {
		List<String> categories = new ArrayList<>();
		for (RecommendedPlace item : assembly.returnedItems()) {
			pool.stream()
					.filter((c) -> c.placeId().equals(item.placeId()))
					.findFirst()
					.ifPresent((c) -> categories.add(String.valueOf(c.featureValues().get("category"))));
		}
		return categories;
	}

	@SuppressWarnings("unchecked")
	private static double share(Map<String, Object> metrics, String phase) {
		Map<String, Object> section = (Map<String, Object>) metrics.get(phase);
		return ((Number) section.get("topCategoryShare")).doubleValue();
	}
}
