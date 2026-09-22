package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.Map;

import com.gabolle.backend.recommendation.domain.RecommendationCandidate;

/**
 * 후보 전부를 저장 가능한 형태로 만든 결과. {@code candidates} 에는 탈락한 후보도 들어 있고,
 * 개수 셋은 Candidate 행마다 반복하지 않고 Job 에 한 번만 적히는 값이다.
 * {@code diversityMetrics} 는 재정렬 전·후 지표와 그때 쓰인 설정값이며
 * {@code recommendation_requested} 이벤트에 요청마다 하나씩 실린다.
 */
public record CandidateAssembly(
		List<RecommendationCandidate> candidates,
		List<RecommendedPlace> returnedItems,
		int generatedCount,
		int eligibleCount,
		int returnedCount,
		Map<String, Object> diversityMetrics) {

	public CandidateAssembly {
		diversityMetrics = (diversityMetrics == null) ? Map.of() : Map.copyOf(diversityMetrics);
	}
}
