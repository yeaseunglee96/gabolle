package com.gabolle.backend.recommendation.adapter;

import java.util.UUID;

/**
 * 추천 엔진에 넘기는 입력.
 *
 * <p>🔴 {@code requestId} 는 <b>백엔드가 만들어서 넘긴다</b> — 엔진이 만들면 같은 요청을 두 번
 * 부를 때 키가 갈라져 후보와 노출이 이어지지 않는다.
 *
 * @param requestId 이 추천 요청의 정본 키
 * @param userId 사용자
 * @param tripId 여행. 여행 맥락 없는 추천이면 {@code null}
 * @param tripVersion 여행 조건의 불변 버전
 * @param preferenceSnapshotId S15P21E201-542 취향 스냅샷 ID
 * @param constraintSnapshotId S15P21E201-542 제약 스냅샷 ID
 * @param itineraryId 일정. 없으면 {@code null}
 * @param itineraryVersion 일정 버전
 * @param topK 응답에 담을 최대 개수. 후보 <b>생성</b> 개수를 제한하는 값이 아니다
 */
public record EngineRequest(
		UUID requestId,
		UUID userId,
		UUID tripId,
		Integer tripVersion,
		UUID preferenceSnapshotId,
		UUID constraintSnapshotId,
		UUID itineraryId,
		Integer itineraryVersion,
		int topK) {
}
