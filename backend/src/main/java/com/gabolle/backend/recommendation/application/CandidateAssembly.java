package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.Map;

import com.gabolle.backend.recommendation.domain.RecommendationCandidate;

/**
 * 후보 전부를 저장 가능한 형태로 만든 결과.
 *
 * <p>개수 셋은 Candidate 행마다 반복하지 않고 Job 에 한 번만 적히는 값이다.
 *
 * @param candidates 저장할 후보 전부 — 탈락한 것 포함
 * @param returnedItems 실제로 응답에 담길 것만
 * @param generatedCount 만들어진 후보 전체 수
 * @param eligibleCount 랭킹 대상이 된 후보 수
 * @param returnedCount 반환된 후보 수
 * @param diversityMetrics 재정렬 전·후 다양성 지표와 그때 쓰인 설정값 (S15P21E201-548).
 *     완료 기준 "재정렬 전후 성능·다양성 지표가 함께 남는다" 가 요구하는 것이고,
 *     {@code RecommendationService} 가 이것을 {@code recommendation_requested} 이벤트에
 *     싣는다 — 요청마다 하나씩 남아야 나중에 기준선과 견줄 수 있다
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
