package com.gabolle.backend.recommendation.adapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.ConstraintVerdict;

/**
 * 추천 엔진이 돌려준 후보 하나. 아직 순위는 없다 — 순위는 백엔드가 매기고 기록한다.
 *
 * {@code placeId} 는 내부 정본 장소 ID 다. 카카오·네이버 지도 API 의 원본 응답은 여기
 * 담지 않고 내부 place_id 와 허용된 출처 코드만 남긴다. {@code preRankScore} 는 판정이
 * FAIL 이면 없을 수 있다.
 */
public record EngineCandidate(
		UUID placeId,
		String candidateSource,
		ConstraintVerdict constraintVerdict,
		List<Map<String, Object>> violations,
		List<Map<String, Object>> unknownFacts,
		Double constraintConfidence,
		Map<String, Object> featureValues,
		Map<String, Object> scoreComponents,
		Double preRankScore,
		List<String> reasonCodes,
		List<String> warningCodes) {

	public EngineCandidate {
		if (placeId == null) {
			throw new IllegalArgumentException("placeId 는 필수다");
		}
		if (candidateSource == null || candidateSource.isBlank()) {
			throw new IllegalArgumentException(
					"candidateSource 는 필수다 — 후보가 어디서 왔는지는 나중에 복원할 수 없다");
		}
		if (constraintVerdict == null) {
			throw new IllegalArgumentException(
					"constraintVerdict 는 필수다. 판정하지 못했다면 그것이 UNKNOWN 이다");
		}
		violations = copyList(violations);
		unknownFacts = copyList(unknownFacts);
		featureValues = copyMap(featureValues);
		scoreComponents = copyMap(scoreComponents);
		reasonCodes = copyList(reasonCodes);
		warningCodes = copyList(warningCodes);
	}

	/**
	 * 꼭 지켜야 하는 조건을 어겼다고 확인된 후보인가 — 이 후보는 순위에도 결과에도 못 들어간다.
	 *
	 * <p>🔴 <b>「빠질 후보」의 정의는 이것 하나다.</b> 결과를 조립할 때 빼는 쪽({@code CandidateAssembler})과
	 * 그보다 앞에서 상한으로 자르는 쪽({@code BaselineRecommendationEngine})이 같은 말을 써야 한다.
	 * 두 곳에 따로 적으면 한쪽만 고쳐지는 날, 자르기가 「남을 것」이라 믿고 지킨 후보를 조립이 버린다.
	 */
	public boolean hardFailed() {
		return this.constraintVerdict == ConstraintVerdict.FAIL;
	}

	// List.copyOf · Map.copyOf 를 쓰지 않는 이유: 그것들은 null 원소·null 값을 거부하는데,
	// 피처 값에는 "그 피처를 못 구했다" 를 뜻하는 null 이 정상적으로 들어온다.
	private static <T> List<T> copyList(List<T> source) {
		return (source == null) ? List.of() : Collections.unmodifiableList(new java.util.ArrayList<>(source));
	}

	private static Map<String, Object> copyMap(Map<String, Object> source) {
		return (source == null) ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
