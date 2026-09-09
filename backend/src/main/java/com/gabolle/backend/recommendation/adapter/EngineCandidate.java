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
 * <p>🔴 {@code placeId} 는 <b>내부 정본 장소 ID</b> 다. 카카오·네이버 지도 API 의 원본 응답을
 * 여기 담지 않는다. Candidate 에는 내부 place_id 와 허용된 출처 코드만 남는다.
 *
 * @param placeId 내부 정본 장소 ID
 * @param candidateSource 이 후보가 어디서 나왔는지 (허용된 출처 코드)
 * @param constraintVerdict 하드 제약 판정
 * @param violations 위반 코드와 근거
 * @param unknownFacts 확인하지 못한 사실
 * @param constraintConfidence 판정 신뢰도
 * @param featureValues 랭킹 시점 피처 스냅샷
 * @param scoreComponents 점수 구성 요소
 * @param preRankScore 재정렬 전 점수. 판정이 FAIL 이면 없을 수 있다
 * @param reasonCodes 추천 이유 코드
 * @param warningCodes 경고 코드
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

	// List.copyOf · Map.copyOf 를 쓰지 않는 이유: 그것들은 null 원소·null 값을 거부하는데,
	// 피처 값에는 "그 피처를 못 구했다" 를 뜻하는 null 이 정상적으로 들어온다.
	private static <T> List<T> copyList(List<T> source) {
		return (source == null) ? List.of() : Collections.unmodifiableList(new java.util.ArrayList<>(source));
	}

	private static Map<String, Object> copyMap(Map<String, Object> source) {
		return (source == null) ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
