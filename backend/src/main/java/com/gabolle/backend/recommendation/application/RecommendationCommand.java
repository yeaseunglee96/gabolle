package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.RequestLocation;

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
 * @param edit 🔴 S15P21E201-249 — 장소 제외·재계산 편집 Job 만 채운다. 일정 생성 등 다른
 *     jobType 은 {@code null} 이다
 * @param location 🔴 S15P21E201-550 — 이 요청 하나에만 쓰이는 위치. <b>저장되지 않는다</b>
 *     (자세한 것은 {@link RequestLocation}). {@code null} 이면 여행 출발지를 쓴다.
 *     "지금 어디 있는가" 가 무의미한 요청(일정 편집 등)은 늘 {@code null} 이다
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

	/**
	 * 위치 없는 요청 — S15P21E201-550 이전의 모양 그대로다.
	 *
	 * <p>🔴 편의 생성자를 둔 이유. 위치는 <b>선택</b>이고(일정 편집·재계산은 "지금 어디
	 * 있는가" 가 무의미하다), 그런 호출부까지 {@code null} 을 한 칸 더 적게 만들면 그
	 * {@code null} 이 무슨 뜻인지 읽는 사람이 매번 되짚어야 한다. 게다가 이 기록을 만드는
	 * 곳 하나가 {@code itinerary} 패키지라 다른 담당의 자리다 — 위치와 무관한 변경으로
	 * 그쪽 파일을 건드리지 않는다.
	 */
	public RecommendationCommand(UUID userId, JobType jobType, UUID tripId, Integer tripVersion,
			UUID preferenceSnapshotId, UUID constraintSnapshotId, UUID itineraryId, Integer itineraryVersion,
			Integer baseVersion, String appVersion, Integer topK, ItineraryEdit edit) {
		this(userId, jobType, tripId, tripVersion, preferenceSnapshotId, constraintSnapshotId, itineraryId,
				itineraryVersion, baseVersion, appVersion, topK, edit, null);
	}

	/**
	 * 🔴 S15P21E201-249 — 장소 제외(ITN-08)·재계산(ITN-09) 편집 요청이 실어 보내는 입력.
	 *
	 * @param dayIndex 다시 계산할 날짜(0부터). 바탕 판에서 {@code itemKey} 가 속했던 날이다
	 * @param itemKey 편집 대상 항목의 열쇠 — 판을 건너 같은 항목을 가리키는 값이다(PK 가
	 *     아니다)
	 * @param newlyExcludedPlaceIds 이번에 새로 제외 목록에 올릴 장소. 장소 제외(ITN-08)는
	 *     그 항목의 장소 하나를 담고, 단순 재계산(ITN-09)은 새로 뺄 장소가 없으므로 빈 목록이다
	 * @param operationalReason 사용자 자유 입력. {@code null} 일 수 있다. 이벤트 payload 에는
	 *     싣지 않는다 — {@link com.gabolle.backend.itinerary.domain.ItineraryExclusion} 의
	 *     같은 칸과 같은 이유다
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
		// 🔴 S15P21E201-249 — 편집 Job 은 반드시 어느 일정의 어느 판을 바탕으로 하는지와
		//    편집 내용을 함께 실어야 한다. 셋 중 하나라도 비면 재계산이 무엇을 다시 채워야
		//    하는지 알 수 없다. 편집이 아닌 jobType 에 edit 이 실리면 그것도 잘못이다 —
		//    "편집인데 대상이 없다" 와 "편집이 아닌데 편집 내용이 있다" 를 같은 자리에서
		//    함께 막는다.
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
