package com.gabolle.backend.recommendation.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;

/**
 * 이 장소가 왜 이 순위인가 — 축별 기여도 (S15P21E201-548).
 *
 * <h2>🔴 이것이 S15P21E201-205 와의 계약이다</h2>
 *
 * <p>-205(추천 근거 문장 조립)의 참고란이 이렇게 못 박았다.
 *
 * <blockquote>축별 기여도는 다기준 추천 점수 계산이 함께 돌려준다.
 * <b>여기서 다시 계산하지 않는다.</b></blockquote>
 *
 * <p>그래서 <b>기여도를 내보내는 것은 이쪽 일</b>이고, 문장으로 만드는 것이 -205 다.
 * -205 의 완료 기준이 "문구의 숫자가 실제 점수 계산 결과와 일치한다" 이므로 축 이름과
 * 함께 <b>계산에 실제로 쓰인 weight 와 value 를 그대로</b> 싣는다 — 문장이 "로컬점수 4.6"
 * 이라고 쓸 때 그 4.6 이 여기서 온다.
 *
 * <h2>🔴 두 가지 순위를 함께 낸다 — 절대 기여와 대조 기여</h2>
 *
 * <p>-205 의 작업 내용은 "최종 점수에 <b>가장 크게 기여한</b> 상위 2~3개 축" 이라고
 * 적었다. 말 그대로 하면 절대 기여도({@code weight × value})다. 그런데 그것만으로는
 * <b>설명이 되지 않는 경우가 있다.</b>
 *
 * <pre>
 * 후보 넷이 모두 조용한 동네에 있을 때
 *   절대 기여 1위 : QUIETNESS (0.198)  ← 네 곳 다 그렇다. "조용해서 1위" 는 아무 설명이 아니다
 *   대조 기여 1위 : SLOPE (+0.048)     ← 이 곳만 평평하다. 이것이 순위를 만든 이유다
 * </pre>
 *
 * <p>그래서 {@code topAxes}(절대)와 {@code distinctiveAxes}(같은 결과 안의 평균과의 차이)를
 * 둘 다 싣는다. -205 는 지금 명세 그대로 {@code topAxes} 를 쓰면 되고, 문장이 모든 카드에서
 * 똑같이 읽히는 문제가 보이면 {@code distinctiveAxes} 가 이미 옆에 있다. 어느 쪽을 쓸지는
 * 문장을 만드는 쪽의 결정이라 여기서 하나로 정하지 않았다.
 */
final class ReasonRanking {

	/** {@code score_components} 안에 이 이름으로 들어간다 — -205 가 읽는 자리. */
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
				// 🔴 동점이면 축 이름으로 가른다. 없으면 같은 입력에서 순서가 흔들리고,
				//    -205 의 "같은 조건으로 두 번 생성하면 글자까지 같다" 가 깨진다.
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
		// 🔴 weight·value 를 그대로 옮긴다 — -205 의 문장이 이 숫자를 쓴다.
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
