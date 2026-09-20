package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.RequestLocation;

import com.gabolle.backend.recommendation.domain.JobType;

/**
 * 추천 요청 한 건의 입력.
 *
 * 여기 {@code requestId} 가 없는 것이 설계다 — 서버가 만든다. 클라이언트가 정하게 두면 남의
 * 요청 키를 덮어쓰거나 분석 키를 조작할 수 있다.
 *
 * @param baseVersion 편집 기준 일정 버전. 편집 충돌의 409 판정에 쓰인다
 * @param topK 응답에 담을 최대 개수. {@code null} 이면 설정 기본값
 * @param edit 장소 제외·재계산 편집 Job 만 채운다. 다른 jobType 은 {@code null} 이다
 * @param location 이 요청 하나에만 쓰이는 위치이고 저장되지 않는다({@link RequestLocation}).
 *     {@code null} 이면 여행 출발지를 쓰며, "지금 어디 있는가" 가 무의미한 요청은 늘
 *     {@code null} 이다
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
		Integer topK,
		ItineraryEdit edit,
		RequestLocation location) {

	/** 위치가 무의미한 요청(일정 편집·재계산)이 쓰는 생성자. */
	public RecommendationCommand(UUID userId, JobType jobType, UUID tripId, Integer tripVersion,
			UUID preferenceSnapshotId, UUID constraintSnapshotId, UUID itineraryId, Integer itineraryVersion,
			Integer baseVersion, String appVersion, Integer topK, ItineraryEdit edit) {
		this(userId, jobType, tripId, tripVersion, preferenceSnapshotId, constraintSnapshotId, itineraryId,
				itineraryVersion, baseVersion, appVersion, topK, edit, null);
	}

	/**
	 * 장소 제외·재계산 편집 요청이 실어 보내는 입력.
	 *
	 * @param dayIndex 다시 계산할 날짜(0부터). 바탕 판에서 {@code itemKey} 가 속했던 날이다
	 * @param itemKey 판을 건너 같은 항목을 가리키는 값이다 — PK 가 아니다
	 * @param newlyExcludedPlaceIds 이번에 새로 제외 목록에 올릴 장소. 단순 재계산은 새로 뺄
	 *     장소가 없으므로 빈 목록이다
	 * @param operationalReason 사용자 자유 입력이라 이벤트 payload 에는 싣지 않는다
	 */
	public record ItineraryEdit(Integer dayIndex, String itemKey, List<UUID> newlyExcludedPlaceIds,
			String operationalReason) {

		public ItineraryEdit {
			newlyExcludedPlaceIds = newlyExcludedPlaceIds == null ? List.of() : List.copyOf(newlyExcludedPlaceIds);
		}
	}

	/** 이 명령이 일정 편집(제외·재계산) 요청인가. */
	public boolean isItineraryEdit() {
		return edit != null;
	}

	private static final List<JobType> ITINERARY_EDIT_JOB_TYPES = List.of(
			JobType.ITEM_REMOVE, JobType.ITINERARY_RECALCULATE, JobType.ITEM_REPLACE, JobType.ITEM_ORDER_CHANGE);

	public RecommendationCommand {
		if (userId == null) {
			throw new IllegalArgumentException("userId 는 필수다");
		}
		if (jobType == null) {
			throw new IllegalArgumentException("jobType 은 필수다 (GB-API-001 4.2 JobDto.type)");
		}
		// 스냅샷 ID 없이 추천을 시작할 수 없다. 없으면 그때 어떤 취향과 제약으로 추천했는지를
		// 나중에 물을 수 없다. DB 도 성공한 Job 에 같은 것을 요구하지만, 여기서 먼저 막아야
		// 읽을 수 있는 오류가 난다 — 제약 위반은 JDBC 안쪽에서 터져 호출자를 못 가리킨다.
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
		// 편집 Job 은 어느 일정의 어느 판을 바탕으로 하는지와 편집 내용을 함께 실어야 한다.
		// 셋 중 하나라도 비면 재계산이 무엇을 다시 채울지 알 수 없다. 편집이 아닌 jobType 에
		// edit 이 실리는 것도 잘못이라 같은 자리에서 함께 막는다.
		boolean requiresEdit = ITINERARY_EDIT_JOB_TYPES.contains(jobType);
		if (requiresEdit) {
			if (itineraryId == null || baseVersion == null || edit == null) {
				throw new IllegalArgumentException(
						"jobType=" + jobType + " 은 itineraryId·baseVersion·edit 이 모두 있어야 한다");
			}
		}
		else if (edit != null) {
			throw new IllegalArgumentException("jobType=" + jobType + " 은 edit 을 실을 수 없다");
		}
	}
}
