package com.gabolle.backend.recommendation.application;

import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobType;

/**
 * 추천 요청 한 건의 입력.
 *
 * <p>🔴 여기 {@code requestId} 가 <b>없는 것이 설계다.</b> request_id 는 서버가 만든다.
 * 클라이언트가 정하게 두면 남의 요청 키를 덮어쓰거나 분석 키를 조작할 수 있고, 같은 키가
 * 두 번 오면 후보와 노출이 엉뚱하게 이어진다.
 *
 * @param userId 사용자
 * @param jobType 무엇을 하려는 요청인가 (GB-API-001 4.2 JobDto.type)
 * @param tripId 여행. 여행 맥락 없는 추천이면 {@code null}
 * @param tripVersion 여행 조건의 불변 버전
 * @param preferenceSnapshotId S15P21E201-542 취향 스냅샷 ID
 * @param constraintSnapshotId S15P21E201-542 제약 스냅샷 ID
 * @param itineraryId 일정. 없으면 {@code null}
 * @param itineraryVersion 일정 버전
 * @param baseVersion 편집 기준 일정 버전 (FR-ITN-08 의 409 판정에 쓰인다)
 * @param appVersion 클라이언트 빌드 버전. 클라이언트만 아는 값이라 여기로 받는다
 * @param topK 응답에 담을 최대 개수. {@code null} 이면 설정 기본값
 */
public record RecommendationCommand(
		UUID userId,
		JobType jobType,
		UUID tripId,
		Integer tripVersion,
		UUID preferenceSnapshotId,
		UUID constraintSnapshotId,
		UUID itineraryId,
		Integer itineraryVersion,
		Integer baseVersion,
		String appVersion,
		Integer topK) {

	public RecommendationCommand {
		if (userId == null) {
			throw new IllegalArgumentException("userId 는 필수다");
		}
		if (jobType == null) {
			throw new IllegalArgumentException("jobType 은 필수다 (GB-API-001 4.2 JobDto.type)");
		}
		// 🔴 스냅샷 ID 없이 추천을 시작할 수 없다. 이것이 없으면 나중에 "그때 어떤 취향과
		//    제약으로 추천했는가" 를 물을 수 없고, 그 요청은 분석에서 버려진다.
		//    DB 도 성공한 Job 에 같은 것을 요구하지만, 여기서 먼저 막아야 읽을 수 있는
		//    오류가 난다 — 제약 위반은 JDBC 안쪽에서 터져 어느 호출자인지 못 가리킨다.
		if (preferenceSnapshotId == null) {
			throw new IllegalArgumentException(
					"preferenceSnapshotId 는 필수다 (S15P21E201-542 취향 스냅샷)");
		}
		if (constraintSnapshotId == null) {
			throw new IllegalArgumentException(
					"constraintSnapshotId 는 필수다 (S15P21E201-542 제약 스냅샷)");
		}
		if (topK != null && topK < 1) {
			throw new IllegalArgumentException("topK 는 1 이상이어야 한다: " + topK);
		}
	}
}
