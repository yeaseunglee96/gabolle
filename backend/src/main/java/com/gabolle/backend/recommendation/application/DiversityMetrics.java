package com.gabolle.backend.recommendation.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;

/**
 * 결과 하나가 얼마나 고른가. 같은 계산을 재정렬 전 목록과 후 목록에 각각 돌린다.
 *
 * {@code topCategoryShare} 는 상위 결과에서 가장 많은 카테고리가 차지하는 비율이고
 * 1.0 이면 전부 같은 카테고리다. 키를 못 구한 후보는 분모에서도 빠진다 — 넣으면 표식이
 * 비어 있는 만큼 다양성이 좋아 보인다. 몇 건으로 잰 값인지는 {@code countedFor...} 에 남는다.
 */
final class DiversityMetrics {

	private DiversityMetrics() {
	}

	static Map<String, Object> of(List<EngineCandidate> items) {
		Map<String, Integer> categoryCounts = new LinkedHashMap<>();
		Map<String, Integer> localityCounts = new LinkedHashMap<>();
		for (EngineCandidate candidate : items) {
			String category = DiversityKeys.categoryOf(candidate);
			if (category != null) {
				categoryCounts.merge(category, 1, Integer::sum);
			}
			String locality = DiversityKeys.localityOf(candidate);
			if (locality != null) {
				localityCounts.merge(locality, 1, Integer::sum);
			}
		}

		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("size", items.size());
		put(metrics, "category", categoryCounts);
		put(metrics, "locality", localityCounts);
		return metrics;
	}

	private static void put(Map<String, Object> metrics, String prefix, Map<String, Integer> counts) {
		int counted = counts.values().stream().mapToInt(Integer::intValue).sum();
		metrics.put("countedFor" + capitalize(prefix), counted);
		metrics.put(prefix + "Distinct", counts.size());
		if (counted == 0) {
			// 0 이 아니라 null 이다. "다양성이 0" 과 "잴 수 없었다" 는 다른 사실이고,
			// 0 을 넣으면 표식이 비어 있는 요청이 최악의 다양성으로 집계된다.
			metrics.put(prefix + "Diversity", null);
			metrics.put("top" + capitalize(prefix) + "Share", null);
			return;
		}
		metrics.put(prefix + "Diversity", counts.size() / (double) counted);
		int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		metrics.put("top" + capitalize(prefix) + "Share", max / (double) counted);
	}

	private static String capitalize(String text) {
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** 재정렬 전·후를 한 덩어리로. */
	static Map<String, Object> beforeAndAfter(List<EngineCandidate> before, List<EngineCandidate> after,
			Map<String, Object> parameters, boolean applied) {

		Map<String, Object> all = new LinkedHashMap<>();
		all.put("applied", applied);
		all.put("parameters", parameters);
		all.put("before", of(before));
		all.put("after", of(after));
		return all;
	}

	/** 이 목록 그대로의 지표만 (재정렬을 안 하는 경로 — Editor's Pick). */
	static Map<String, Object> unchanged(List<EngineCandidate> items) {
		Map<String, Object> all = new LinkedHashMap<>();
		all.put("applied", false);
		all.put("parameters", Map.of());
		Map<String, Object> metrics = of(items);
		all.put("before", metrics);
		all.put("after", metrics);
		return all;
	}

	/** 후보 목록의 앞 {@code topK} 개만 — 지표는 "상위 결과" 에 대해 잰다. */
	static List<EngineCandidate> topOf(List<EngineCandidate> items, int topK) {
		return items.subList(0, Math.min(topK, items.size()));
	}
}
