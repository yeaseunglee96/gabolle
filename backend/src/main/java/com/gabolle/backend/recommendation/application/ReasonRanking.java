package com.gabolle.backend.recommendation.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;

/**
 * 이 장소가 왜 이 순위인가 — 축별 기여도. 점수 계산에 실제로 쓰인 weight 와 value 를 그대로
 * 실어 내보내고, 근거 문장을 만드는 쪽은 이 값을 읽기만 하고 다시 계산하지 않는다.
 *
 * {@code topAxes}(절대 기여)와 {@code distinctiveAxes}(같은 결과 안의 평균과의 차이)를 둘 다
 * 싣는다. 반환된 후보가 모두 같은 성질을 가지면 절대 기여 1위 축은 순위를 설명하지 못하고,
 * 그때 대조 기여가 실제로 갈린 축을 가리킨다. 어느 쪽을 쓸지는 문장을 만드는 쪽이 정한다.
 */
final class ReasonRanking {

	/** {@code score_components} 안에 이 이름으로 들어간다 — 근거 문장이 읽는 자리. */
	static final String COMPONENT_KEY = "reasonRanking";

	private static final int TOP_N = 3;

	private static final String WEIGHT = "weight";

	private static final String VALUE = "value";

	private ReasonRanking() {
	}

	/**
	 * 후보 하나의 축별 절대 기여도. 값을 못 구한 축은 빠진다 — 결측을 0 으로 바꾸면
	 * "기여가 없었다" 로 읽히는데 실제로는 "재지 못했다" 다.
	 */
	static Map<String, Double> contributionsOf(EngineCandidate candidate) {
		Map<String, Double> contributions = new LinkedHashMap<>();
		Map<String, Object> components = candidate.scoreComponents();
		if (components == null) {
			return contributions;
		}
		for (Map.Entry<String, Object> entry : components.entrySet()) {
			if (!(entry.getValue() instanceof Map<?, ?> detail)) {
				continue;
			}
			Double weight = number(detail.get(WEIGHT));
			Double value = number(detail.get(VALUE));
			if (weight == null || value == null) {
				continue;
			}
			contributions.put(entry.getKey(), weight * value);
		}
		return contributions;
	}

	/** 반환될 후보들에 걸친 축별 평균 기여도 — 대조 기여의 기준선이다. */
	static Map<String, Double> cohortMeans(List<EngineCandidate> returned) {
		Map<String, Double> sums = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (EngineCandidate candidate : returned) {
			for (Map.Entry<String, Double> entry : contributionsOf(candidate).entrySet()) {
				sums.merge(entry.getKey(), entry.getValue(), Double::sum);
				counts.merge(entry.getKey(), 1, Integer::sum);
			}
		}
		Map<String, Double> means = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : sums.entrySet()) {
			means.put(entry.getKey(), entry.getValue() / counts.get(entry.getKey()));
		}
		return means;
	}

	/**
	 * {@code score_components.reasonRanking} 에 들어갈 내용.
	 *
	 * @param cohortMeans {@link #cohortMeans(List)} 결과. 비어 있으면 대조 기여는 빈 목록이다
	 */
	static Map<String, Object> of(EngineCandidate candidate, Map<String, Double> cohortMeans) {
		Map<String, Double> contributions = contributionsOf(candidate);
		Map<String, Object> ranking = new LinkedHashMap<>();

		List<Map<String, Object>> topAxes = new ArrayList<>();
		contributions.entrySet().stream()
				// 동점이면 축 이름으로 가른다. 없으면 같은 입력에서 순서가 흔들려, 같은
				// 조건으로 두 번 만든 근거 문장의 글자가 달라진다.
				.sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
						.thenComparing(Map.Entry::getKey))
				.limit(TOP_N)
				.forEach((entry) -> topAxes.add(axis(candidate, entry.getKey(), entry.getValue(), null)));
		ranking.put("topAxes", topAxes);

		List<Map<String, Object>> distinctive = new ArrayList<>();
		if (!cohortMeans.isEmpty()) {
			contributions.entrySet().stream()
					.sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(
									(entry) -> entry.getValue() - cohortMeans.getOrDefault(entry.getKey(), 0.0))
							.reversed()
							.thenComparing(Map.Entry::getKey))
					.limit(TOP_N)
					.forEach((entry) -> distinctive.add(axis(candidate, entry.getKey(), entry.getValue(),
							cohortMeans.get(entry.getKey()))));
		}
		ranking.put("distinctiveAxes", distinctive);
		return ranking;
	}

	/** 절대 기여 1위 축 — 이유 코드로 붙는다. 없으면 {@code null}. */
	static String topAxisOf(EngineCandidate candidate) {
		return contributionsOf(candidate).entrySet().stream()
				.sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed()
						.thenComparing(Map.Entry::getKey))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);
	}

	private static Map<String, Object> axis(EngineCandidate candidate, String name, double contribution,
			Double cohortMean) {
		Map<String, Object> axis = new LinkedHashMap<>();
		axis.put("axis", name);
		// weight·value 를 그대로 옮긴다 — 근거 문장이 이 숫자를 쓴다.
		Object detail = (candidate.scoreComponents() == null) ? null : candidate.scoreComponents().get(name);
		if (detail instanceof Map<?, ?> map) {
			axis.put(WEIGHT, map.get(WEIGHT));
			axis.put(VALUE, map.get(VALUE));
		}
		axis.put("contribution", contribution);
		if (cohortMean != null) {
			axis.put("cohortMean", cohortMean);
			axis.put("delta", contribution - cohortMean);
		}
		return axis;
	}

	private static Double number(Object raw) {
		return (raw instanceof Number n) ? n.doubleValue() : null;
	}
}
