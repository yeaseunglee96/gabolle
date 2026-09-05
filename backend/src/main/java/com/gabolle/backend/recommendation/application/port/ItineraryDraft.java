package com.gabolle.backend.recommendation.application.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * {@link ItineraryDraftPort#assemble} 이 만든, 아직 저장되지 않은 일정 — 판 하나를 이룰
 * 항목·구간 전부.
 */
public record ItineraryDraft(
        String tripId, String userId, UUID requestId,
        String modelVersion, String featureVersion, String ontologyVersion,
        String policyVersion, String datasetVersion,
        List<DraftItem> items, List<DraftLeg> legs) {

    public record DraftItem(int dayIndex, LocalDate visitDate, int sequence, UUID placeId,
            UUID itemKey, LocalTime startTime, LocalTime endTime, Integer stayMinutes,
            String dataStatus, List<String> reasonCodes, List<String> warningCodes) { }

    public record DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId,
            String travelMode, Integer distanceM, Integer durationMin, Integer walkingMeters) { }
}
