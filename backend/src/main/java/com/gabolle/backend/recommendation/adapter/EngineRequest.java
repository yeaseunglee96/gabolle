package com.gabolle.backend.recommendation.adapter;

import java.util.UUID;

import com.gabolle.backend.recommendation.domain.RequestLocation;

/**
 * 추천 엔진에 넘기는 입력.
 *
 * {@code requestId} 는 백엔드가 만들어서 넘긴다 — 엔진이 만들면 같은 요청을 두 번 부를 때
 * 키가 갈라져 후보와 노출이 이어지지 않는다. {@code topK} 는 응답에 담을 최대 개수이고
 * 후보 생성 개수를 제한하는 값이 아니다. {@code location} 은 후보 조회의 중심으로만 쓰고
 * 저장하지 않으며, {@code null} 이면 엔진이 여행 출발지를 쓴다.
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
		int topK,
		RequestLocation location) {

	public EngineRequest(UUID requestId, UUID userId, UUID tripId, Integer tripVersion,
			UUID preferenceSnapshotId, UUID constraintSnapshotId, UUID itineraryId, Integer itineraryVersion,
			int topK) {
		this(requestId, userId, tripId, tripVersion, preferenceSnapshotId, constraintSnapshotId, itineraryId,
				itineraryVersion, topK, null);
	}
}
