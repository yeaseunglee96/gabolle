package com.gabolle.backend.recommendation.application.port;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobType;

/**
 * 이 판의 이 날짜만 다시 채워라. {@link ItineraryDraftCommand} 와 따로 둔 것은 보존해야 할
 * 것이 있느냐가 다르기 때문이다 — 새 일정에는 보존할 것이 없고, 재계산에는 다른 날짜·고정
 * 항목·이미 지나간 자리가 있다.
 *
 * {@code rankedPool} 은 추천 엔진이 이번에 내놓은 순위표이고, 여기서 제외 목록과 이미 배치된
 * 장소를 뺀 것으로 빈 자리를 채운다 — 후보가 없으면 그 자리는 빈다.
 *
 * @param itemKey 편집의 기준 항목. {@code ITEM_REMOVE} 면 빼는 항목,
 *     {@code ITINERARY_RECALCULATE} 면 이 항목부터 다시 계산하고 그 앞은 보존한다
 * @param operationalReason 사용자가 적은 제외 사유. 자유 입력이라 이벤트 payload 에 싣지 않는다
 */
public record ItineraryRevisionCommand(
        UUID requestId,
        String itineraryId,
        int baseVersion,
        String userId,
        JobType jobType,
        /** 다시 채울 날. {@code ITEM_REMOVE} 는 -1 이면 {@code itemKey} 가 있는 날을 바탕 판에서 읽는다. */
        int dayIndex,
        /** {@code ITEM_REMOVE}: 뺄 항목(필수). {@code ITINERARY_RECALCULATE}: 있으면 이 항목부터 뒤를 다시 채운다. */
        String itemKey,
        List<UUID> newlyExcludedPlaceIds,
        String operationalReason,
        List<ItineraryDraftCommand.PlannedPlace> rankedPool,
        String modelVersion,
        String featureVersion,
        String ontologyVersion,
        String policyVersion,
        String datasetVersion) {

    public ItineraryRevisionCommand {
        if (requestId == null || itineraryId == null || userId == null || jobType == null) {
            throw new IllegalArgumentException("requestId·itineraryId·userId·jobType 는 필수다");
        }
        if (baseVersion < 1) {
            throw new IllegalArgumentException("baseVersion 은 1 이상이어야 한다: " + baseVersion);
        }
        if (jobType != JobType.ITEM_REMOVE && jobType != JobType.ITINERARY_RECALCULATE) {
            throw new IllegalArgumentException("하루 재계산이 아는 편집은 ITEM_REMOVE·ITINERARY_RECALCULATE 다: " + jobType);
        }
        // ITEM_REMOVE 는 itemKey 가 곧 위치다 — URL 에 itemId 만 오고 날짜는 안 온다. 그 항목이 어느 날에
        // 있는지는 바탕 판이 안다. dayIndex 는 있으면 대조용이고 없으면(-1) 판에서 읽는다.
        // ITINERARY_RECALCULATE 는 반대로 dayIndex 가 곧 위치라 필수다.
        if (jobType == JobType.ITEM_REMOVE && (itemKey == null || itemKey.isBlank())) {
            throw new IllegalArgumentException("ITEM_REMOVE 는 뺄 항목(itemKey)이 있어야 한다");
        }
        if (jobType == JobType.ITINERARY_RECALCULATE && dayIndex < 0) {
            throw new IllegalArgumentException("ITINERARY_RECALCULATE 는 dayIndex 가 0 이상이어야 한다: " + dayIndex);
        }
        newlyExcludedPlaceIds = newlyExcludedPlaceIds == null ? List.of() : List.copyOf(newlyExcludedPlaceIds);
        rankedPool = rankedPool == null ? List.of() : List.copyOf(rankedPool);
    }
}
