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
 * 같은 카테고리·같은 동네가 상위를 독점하지 않게 순서를 다시 정한다 (S15P21E201-548).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>점수만으로 줄을 세우면 <b>가장 잘 맞는 것들이 서로 비슷하다</b>. 조용한 카페를
 * 좋아하는 사람에게는 같은 동네 카페 다섯 개가 1~5위를 차지하고, 그 목록은 "잘 맞지만
 * 고를 것이 없는" 목록이 된다. FR-REC-05 · FR-REC-08.
 *
 * <h2>어떻게</h2>
 *
 * <p>MMR(Maximal Marginal Relevance — <b>"이미 뽑은 것과 얼마나 겹치는가" 를 점수에서
 * 깎아 가며 하나씩 뽑는 방식</b>) 계열의 탐욕 선택이다. 매 자리마다 남은 후보 중
 * <b>점수 − 겹침 벌점</b>이 가장 큰 것을 뽑는다.
 *
 * <pre>
 * 벌점 = categoryPenalty × (앞에 뽑힌 같은 카테고리 개수)
 *      + localityPenalty × (앞에 뽑힌 같은 지역 칸 개수)
 * </pre>
 *
 * <p>🔴 <b>개수</b>로 곱한다(있는지 없는지가 아니라). 그래야 세 번째 카페가 두 번째
 * 카페보다 더 깎여서 "독점" 이 실제로 줄어든다 — 있는지 없는지로만 보면 두 번째부터
 * 벌점이 같아 네 개가 연달아 붙는 것을 못 막는다.
 *
 * <h2>🔴 점수 자체는 건드리지 않는다</h2>
 *
 * <p>벌점은 <b>순서를 정하는 데만</b> 쓰고 {@code preRankScore}·{@code finalScore} 에
 * 반영하지 않는다. 반영하면 "취향에 얼마나 맞는가" 와 "목록을 고르게 만들려고 깎았는가"
 * 가 한 숫자에 섞여, 나중에 점수 분포를 보는 어느 질의도 두 가지를 가를 수 없다.
 * 순서가 바뀐 사실은 {@code original_rank} 와 {@code final_rank} 가 함께 남는 것으로
 * 표현된다 — 그 두 칸은 이 재정렬기를 위해 처음부터 비워 둔 자리다
 * ({@code CandidateAssembler} 의 "재정렬기가 아직 없다" 주석).
 */
@Component
public class DiversityReranker {

	private final DiversityProperties properties;

	public DiversityReranker(DiversityProperties properties) {
		this.properties = properties;
	}

	/**
	 * @param byScore 점수 높은 순으로 이미 정렬된 후보. 이 목록을 바꾸지 않는다
	 * @return 재정렬된 순서와 되짚기에 필요한 것들
	 */
	public Reranked rerank(List<EngineCandidate> byScore) {
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("enabled", this.properties.enabled());
		parameters.put("categoryPenalty", this.properties.categoryPenalty());
		parameters.put("localityPenalty", this.properties.localityPenalty());

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

		while (!remaining.isEmpty()) {
			int bestIndex = 0;
			double bestAdjusted = Double.NEGATIVE_INFINITY;
			for (int index = 0; index < remaining.size(); index++) {
				EngineCandidate candidate = remaining.get(index);
				double adjusted = score(candidate) - penalty(candidate, categoryCounts, localityCounts);
				// 🔴 > 로만 비교한다(>= 가 아니다). 같은 값이면 앞의 것이 이기고, 앞의 것은
				//    점수 순으로 정렬돼 있으므로 동점의 순서가 실행마다 흔들리지 않는다.
				if (adjusted > bestAdjusted) {
					bestAdjusted = adjusted;
					bestIndex = index;
				}
			}
			EngineCandidate chosen = remaining.remove(bestIndex);
			ordered.add(chosen);
			bump(categoryCounts, DiversityKeys.categoryOf(chosen));
			bump(localityCounts, DiversityKeys.localityOf(chosen));
		}
		return new Reranked(List.copyOf(ordered), scoreRank, parameters, true);
	}

	private double penalty(EngineCandidate candidate, Map<String, Integer> categoryCounts,
			Map<String, Integer> localityCounts) {

		double penalty = 0.0;
		String category = DiversityKeys.categoryOf(candidate);
		if (category != null) {
			penalty += this.properties.categoryPenalty() * categoryCounts.getOrDefault(category, 0);
		}
		String locality = DiversityKeys.localityOf(candidate);
		if (locality != null) {
			penalty += this.properties.localityPenalty() * localityCounts.getOrDefault(locality, 0);
		}
		return penalty;
	}

	private static void bump(Map<String, Integer> counts, String key) {
		if (key != null) {
			counts.merge(key, 1, Integer::sum);
		}
	}

	/** 🔴 점수가 없는 후보는 이 자리에 오지 않는다({@code CandidateAssembler} 가 먼저 뺀다). */
	private static double score(EngineCandidate candidate) {
		return (candidate.preRankScore() == null) ? 0.0 : candidate.preRankScore();
	}

	/**
	 * @param ordered 재정렬된 순서
	 * @param scoreRankByPlace 점수만으로 세웠을 때의 순위 — {@code original_rank} 로 남는다
	 * @param parameters 이번에 쓰인 설정값 — 후보 행에 함께 적는다
	 * @param applied 실제로 재정렬을 돌렸는가({@code enabled=false} 나 후보 1건이면 아니다)
	 */
	public record Reranked(
			List<EngineCandidate> ordered,
			Map<UUID, Integer> scoreRankByPlace,
			Map<String, Object> parameters,
			boolean applied) {
	}
}
