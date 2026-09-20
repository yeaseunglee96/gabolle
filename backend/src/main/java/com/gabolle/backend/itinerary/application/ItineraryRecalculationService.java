package com.gabolle.backend.itinerary.application;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.recommendation.application.RecommendationCommand;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 장소 제외·재계산 요청을 받아 그 날짜를 다시 계산하는 추천 Job 을 접수한다.
 * 하는 일은 접수까지다. 하루를 어떻게 다시 채우는가(알고리즘)와 그 결과를 어떻게
 * 게시하는가(Worker)는 이 클래스 밖에 있다.
 * 두 메서드가 같은 순서로 검사하고 그 순서가 계약이다 — {@link ItineraryAccess#requireEditor}
 * (비회원 404, VIEWER 403), {@code itinerary.assertEditableFrom(baseVersion)}(낡았으면 즉시
 * 409, 어차피 버려질 Job 을 접수하지 않는다), 바탕 판에서 {@code itemKey} 찾기(없으면 404,
 * 그 항목의 {@code dayIndex} 가 재계산할 날이다), 최신 취향·제약 스냅샷 읽기(없으면
 * {@link IllegalStateException}), 마지막으로 {@link RecommendationCommand} 조립.
 * 고정(locked)된 항목도 제외할 수 있다. 고정은 "재계산이 이 장소를 건드리지 마라" 는 표시이지
 * "사용자가 직접 빼지 못한다" 가 아니다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryRecalculationService {

	private final ItineraryAccess itineraryAccess;

	private final ItineraryRepository itineraryRepository;

	private final TripRepository tripRepository;

	private final RecommendationJobRunner runner;

	public ItineraryRecalculationService(ItineraryAccess itineraryAccess, ItineraryRepository itineraryRepository,
			TripRepository tripRepository, RecommendationJobRunner runner) {
		this.itineraryAccess = itineraryAccess;
		this.itineraryRepository = itineraryRepository;
		this.tripRepository = tripRepository;
		this.runner = runner;
	}

	/**
	 * 그 항목을 빼고 그 날짜만 다시 계산하는 Job 을 만든다.
	 *
	 * @param operationalReason 사용자가 왜 뺐는지 적은 자유 입력. {@code null} 일 수 있다
	 */
	public RecommendationJob removeItem(String itineraryId, String itemKey, int baseVersion,
			String operationalReason, String userId) {
		return submit(itineraryId, itemKey, null, baseVersion, operationalReason, userId, JobType.ITEM_REMOVE);
	}

	/** {@code fromItemKey} 가 속한 날짜를, 그 항목부터 뒤만 다시 계산하는 Job 을 만든다. */
	public RecommendationJob recalculate(String itineraryId, String fromItemKey, int baseVersion, String userId) {
		return submit(itineraryId, fromItemKey, null, baseVersion, null, userId, JobType.ITINERARY_RECALCULATE);
	}

	/** {@code dayIndex} 날 전체를(고정 항목만 남기고) 다시 계산하는 Job 을 만든다. */
	public RecommendationJob recalculateDay(String itineraryId, int dayIndex, int baseVersion, String userId) {
		return submit(itineraryId, null, dayIndex, baseVersion, null, userId, JobType.ITINERARY_RECALCULATE);
	}

	/**
	 * @param itemKey 뺄 항목(ITEM_REMOVE, 필수) 또는 그 항목부터 다시 채울 기준(RECALCULATE, 선택)
	 * @param dayIndex {@code itemKey} 가 없을 때 다시 계산할 날. 있으면 항목이 속한 날이 우선이다
	 */
	private RecommendationJob submit(String itineraryId, String itemKey, Integer dayIndex, int baseVersion,
			String operationalReason, String userId, JobType jobType) {

		// 편집 권한 — 비회원 404(존재를 감춘다), 회원이지만 VIEWER 면 403.
		ItineraryAccess.Access access = this.itineraryAccess.requireEditor(itineraryId, userId);

		// 낡은 바탕이면 즉시 409 — 어차피 버려질 Job 을 접수하지 않는다.
		access.itinerary().assertEditableFrom(baseVersion);

		// 바탕 판에서 대상 항목을 찾는다. 그 항목의 dayIndex 가 다시 계산할 날이다.
		ItineraryContent base = this.itineraryRepository.findContent(itineraryId, baseVersion)
				.orElseThrow(() -> new IllegalStateException(
						"바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));

		//    항목 없이 날짜만 온 재계산은 그 날 전체가 대상이라 찾을 항목이 없다.
		ItineraryItem target = (itemKey == null) ? null
				: base.items().stream()
						.filter((item) -> itemKey.equals(item.itemKey()))
						.findFirst()
						.orElseThrow(() -> new ItineraryRevision.ItemNotFoundException(itemKey));
		int targetDayIndex = (target != null) ? target.dayIndex() : dayIndex;

		// 스냅샷 둘 — 새 일정 생성 경로와 같은 조회다. 제약을 하나도 안 답한 여행은 추천을
		//    요청할 수 없다.
		String tripId = access.trip().tripId();
		PreferenceSnapshot preferenceSnapshot = this.tripRepository.findLatestSnapshot(tripId)
				.orElseThrow(() -> new IllegalStateException("여행에 취향 스냅샷이 없다: " + tripId));
		String constraintSnapshotId = this.tripRepository.findLatestConstraintSnapshotId(tripId)
				.orElseThrow(() -> new IllegalStateException(
						"이 여행은 제약을 하나도 답하지 않아 추천을 요청할 수 없다: " + tripId));

		// 장소 제외만 이번에 새로 뺄 장소가 있다. 단순 재계산은 없다.
		List<UUID> newlyExcludedPlaceIds = (jobType == JobType.ITEM_REMOVE)
				? List.of(UUID.fromString(target.placeId()))
				: List.of();

		RecommendationCommand.ItineraryEdit edit = new RecommendationCommand.ItineraryEdit(
				targetDayIndex, itemKey, newlyExcludedPlaceIds, operationalReason);

		RecommendationCommand command = new RecommendationCommand(
				UUID.fromString(userId),
				jobType,
				UUID.fromString(tripId),
				null, // tripVersion — RecommendationJobRunner.enqueue 와 같은 이유로 아직 없다
				UUID.fromString(preferenceSnapshot.snapshotId()),
				UUID.fromString(constraintSnapshotId),
				UUID.fromString(itineraryId),
				null, // itineraryVersion — 아직 모른다. 게시된 뒤 attachItinerary 가 채운다
				baseVersion,
				null, // appVersion — 아직 헤더로 안 받는다
				null, // topK — 편집 Job 은 개수 제한 개념이 없다
				edit);

		return this.runner.enqueue(command);
	}
}
