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
 * 그때 대조 기여가 실제로 갈린 축을 가리킨다. 이유 코드는 대조 기여를 쓴다({@link #distinctiveAxisOf}).
 */
final class ReasonRanking {

	/** {@code score_components} 안에 이 이름으로 들어간다 — 근거 문장이 읽는 자리. */
	static final String COMPONENT_KEY = "reasonRanking";

	private static final int TOP_N = 3;

	/**
	 * 「평균보다 높다」로 칠 최소 차이. 같은 값들의 평균은 부동소수 반올림으로 원래 값과 끝자리가 어긋날 수 있다 —
	 * 그 찌꺼기를 「튀었다」로 읽지 않게 한다.
	 */
	private static final double STANDS_OUT = 1e-9;

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
					.sorted(distinctiveOrder(cohortMeans))
					.limit(TOP_N)
					.forEach((entry) -> distinctive.add(axis(candidate, entry.getKey(), entry.getValue(),
							cohortMeans.get(entry.getKey()))));
		}
		ranking.put("distinctiveAxes", distinctive);
		return ranking;
	}

	/**
	 * 평소보다 가장 많이 튄 축 — {@code TOP_CONTRIBUTOR_} 이유 코드로 붙는다 ({@code distinctiveAxes} 의 맨 앞과 같다).
	 * 평균보다 높은 축이 없으면 {@code null} 이다.
	 *
	 * <p>🔴 <b>절대 기여 1위가 아니다</b> (S15P21E201-1638). 후보가 출발지 가까이에 몰려 거리 기여가 누구나 약 0.24 인데
	 * 테마 기여는 커 봐야 0.20 이라, 절댓값으로 고르면 거의 모든 곳이 「거리」였다(거리 비중 조사 K: 상위 20곳 68~76/80).
	 * 모두가 같은 값인 축은 이 장소가 왜 여기 있는지를 설명하지 못한다.
	 *
	 * <p>평균보다 높은 축이 하나도 없으면 붙이지 않는다 — 가장 덜 나쁜 축을 「가장 크게」라고 하면 거짓이다. 혼자 반환된
	 * 후보도 그렇다(견줄 데가 없다).
	 */
	static String distinctiveAxisOf(EngineCandidate candidate, Map<String, Double> cohortMeans) {
		return contributionsOf(candidate).entrySet().stream()
				.filter((entry) -> entry.getValue() - cohortMeans.getOrDefault(entry.getKey(), 0.0) > STANDS_OUT)
				.sorted(distinctiveOrder(cohortMeans))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);
	}

	/** 평균과의 차이가 큰 순. 동점이면 축 이름으로 가른다 — 같은 입력에서 근거 문장의 글자가 흔들리지 않게. */
	private static Comparator<Map.Entry<String, Double>> distinctiveOrder(Map<String, Double> cohortMeans) {
		return Comparator.<Map.Entry<String, Double>>comparingDouble(
						(entry) -> entry.getValue() - cohortMeans.getOrDefault(entry.getKey(), 0.0))
				.reversed()
				.thenComparing(Map.Entry::getKey);
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
