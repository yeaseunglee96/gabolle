package com.gabolle.backend.recommendation.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;
import com.gabolle.backend.recommendation.config.DiversityProperties;

/**
 * 같은 카테고리·같은 동네·<b>같은 음식</b>이 상위를 독점하지 않게 순서를 다시 정한다.
 *
 * MMR(이미 뽑은 것과 얼마나 겹치는가를 점수에서 깎아 가며 하나씩 뽑는 방식) 계열의 탐욕
 * 선택이다. 매 자리마다 남은 후보 중 점수에서 벌점을 뺀 값이 가장 큰 것을 뽑고, 벌점은
 * categoryPenalty × 앞에 뽑힌 같은 카테고리 개수 + localityPenalty × 앞에 뽑힌 같은 지역 칸
 * 개수 + cuisinePenalty × 앞에 뽑힌 같은 음식 개수다. 있는지 없는지가 아니라 개수로 곱해야
 * 세 번째 카페가 두 번째보다 더 깎여 독점이 실제로 줄어든다.
 *
 * <p>음식 축이 뒤늦게 붙은 이유는 카테고리가 너무 굵었기 때문이다 — 돼지국밥집과 칼국수집이
 * 둘 다 {@code FOOD} 라 서로를 깎아도 순서가 안 갈렸다 (S15P21E201-1450).
 *
 * 벌점은 순서를 정하는 데만 쓰고 {@code preRankScore}·{@code finalScore} 에 반영하지 않는다 —
 * 반영하면 취향에 맞는 정도와 목록을 고르게 만들려고 깎은 양이 한 숫자에 섞인다. 순서가
 * 바뀐 사실은 {@code original_rank} 와 {@code final_rank} 가 함께 남는 것으로 표현된다.
 */
@Component
public class DiversityReranker {

	private final DiversityProperties properties;

	public DiversityReranker(DiversityProperties properties) {
		this.properties = properties;
	}

	/** @param byScore 점수 높은 순으로 이미 정렬된 후보. 이 목록을 바꾸지 않는다 */
	public Reranked rerank(List<EngineCandidate> byScore) {
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("enabled", this.properties.enabled());
		parameters.put("categoryPenalty", this.properties.categoryPenalty());
		parameters.put("localityPenalty", this.properties.localityPenalty());
		parameters.put("cuisinePenalty", this.properties.cuisinePenalty());

		Map<UUID, Integer> scoreRank = new LinkedHashMap<>();
		for (int index = 0; index < byScore.size(); index++) {
			scoreRank.put(byScore.get(index).placeId(), index + 1);
		}

		if (!this.properties.enabled() || byScore.size() < 2) {
			return new Reranked(List.copyOf(byScore), scoreRank, parameters, false);
		}

		List<EngineCandidate> remaining = new ArrayList<>(byScore);
		List<EngineCandidate> ordered = new ArrayList<>(byScore.size());
		Map<String, Integer> categoryCounts = new LinkedHashMap<>();
		Map<String, Integer> localityCounts = new LinkedHashMap<>();
		Map<String, Integer> cuisineCounts = new LinkedHashMap<>();

		while (!remaining.isEmpty()) {
			int bestIndex = 0;
			double bestAdjusted = Double.NEGATIVE_INFINITY;
			for (int index = 0; index < remaining.size(); index++) {
				EngineCandidate candidate = remaining.get(index);
				double adjusted = score(candidate) - penalty(candidate, categoryCounts, localityCounts, cuisineCounts);
				// > 로만 비교한다(>= 가 아니다). 같은 값이면 앞의 것이 이기고, 앞의 것은
				// 점수 순으로 정렬돼 있으므로 동점의 순서가 실행마다 흔들리지 않는다.
				if (adjusted > bestAdjusted) {
					bestAdjusted = adjusted;
					bestIndex = index;
				}
			}
			EngineCandidate chosen = remaining.remove(bestIndex);
			ordered.add(chosen);
			bump(categoryCounts, DiversityKeys.categoryOf(chosen));
			bump(localityCounts, DiversityKeys.localityOf(chosen));
			// 표식이 둘이면 둘 다 센다. 하나만 세면 남은 하나로 같은 음식이 다시 올라온다.
			for (String cuisine : DiversityKeys.cuisinesOf(chosen)) {
				bump(cuisineCounts, cuisine);
			}
		}
		return new Reranked(List.copyOf(ordered), scoreRank, parameters, true);
	}

	private double penalty(EngineCandidate candidate, Map<String, Integer> categoryCounts,
			Map<String, Integer> localityCounts, Map<String, Integer> cuisineCounts) {

		double penalty = 0.0;
		String category = DiversityKeys.categoryOf(candidate);
		if (category != null) {
			penalty += this.properties.categoryPenalty() * categoryCounts.getOrDefault(category, 0);
		}
		String locality = DiversityKeys.localityOf(candidate);
		if (locality != null) {
			penalty += this.properties.localityPenalty() * localityCounts.getOrDefault(locality, 0);
		}
		// 표식마다 따로 깎는다. 「회」이면서 「돼지국밥」인 가게는 앞에 회집이 하나, 국밥집이
		// 하나 있으면 두 번 깎인다 — 둘 다 「이미 본 음식」이 맞다.
		for (String cuisine : DiversityKeys.cuisinesOf(candidate)) {
			penalty += this.properties.cuisinePenalty() * cuisineCounts.getOrDefault(cuisine, 0);
		}
		return penalty;
	}

	private static void bump(Map<String, Integer> counts, String key) {
		if (key != null) {
			counts.merge(key, 1, Integer::sum);
		}
	}

	/** 점수가 없는 후보는 이 자리에 오지 않는다({@code CandidateAssembler} 가 먼저 뺀다). */
	private static double score(EngineCandidate candidate) {
		return (candidate.preRankScore() == null) ? 0.0 : candidate.preRankScore();
	}

	/**
	 * {@code scoreRankByPlace} 는 점수만으로 세웠을 때의 순위로 {@code original_rank} 에
	 * 남고, {@code applied} 는 실제로 재정렬을 돌렸는지다 — {@code enabled=false} 이거나
	 * 후보가 1건이면 아니다.
	 */
	public record Reranked(
			List<EngineCandidate> ordered,
			Map<UUID, Integer> scoreRankByPlace,
			Map<String, Object> parameters,
			boolean applied) {
	}
}
