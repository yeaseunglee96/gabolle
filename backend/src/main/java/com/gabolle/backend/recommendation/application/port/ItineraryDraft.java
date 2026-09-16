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

    /**
     * @param dataStatus S15P21E201-179 — 거리·시간이 길찾기 실제 응답인지({@code VERIFIED})
     *        직선거리 어림값인지({@code ESTIMATED}) 아예 못 쟀는지({@code UNKNOWN})
     * @param fareKrw S15P21E201-1109 — 이 구간의 이동 요금(원). 🔴 {@code null} 은 "모른다"
     *        이고 {@code 0} 은 "공짜" 다. 지금 값이 있는 것은 자동차 계열뿐이다
     */
    public record DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId,
            String travelMode, Integer distanceM, Integer durationMin, Integer walkingMeters,
            com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus, Integer fareKrw) {

        /** 요금 없이 만든다 — S15P21E201-1109 이전의 모양 그대로다. */
        public DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId, String travelMode,
                Integer distanceM, Integer durationMin, Integer walkingMeters,
                com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus) {
            this(dayIndex, sequence, fromPlaceId, toPlaceId, travelMode, distanceM, durationMin,
                    walkingMeters, dataStatus, null);
        }
    }
}
