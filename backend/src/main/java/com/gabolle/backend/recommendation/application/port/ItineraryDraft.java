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
        List<DraftItem> items, List<DraftLeg> legs,
        /**
         * {@code itinerary_versions.warning_codes} 로 가는 값. 항목 경고와 대상이 달라서,
         * 담을 항목 행 자체가 없는 사실(빈 시간대 등)이 여기로 온다.
         */
        List<String> warningCodes) {

    public ItineraryDraft {
        warningCodes = (warningCodes == null) ? List.of() : List.copyOf(warningCodes);
    }


    public record DraftItem(int dayIndex, LocalDate visitDate, int sequence, UUID placeId,
            UUID itemKey, LocalTime startTime, LocalTime endTime, Integer stayMinutes,
            String dataStatus, List<String> reasonCodes, List<String> warningCodes) { }

    /**
     * {@code dataStatus} 는 거리·시간이 길찾기 실제 응답인지({@code VERIFIED}) 직선거리
     * 어림값인지({@code ESTIMATED}) 아예 못 쟀는지({@code UNKNOWN})를 가른다.
     * {@code fareKrw} 는 이동 요금(원)이고 {@code null} 은 모른다, {@code 0} 은 공짜다 —
     * 지금 값이 있는 것은 자동차 계열뿐이다.
     */
    /**
     * @param path 이 구간이 지나는 길의 좌표 목록. {@code [경도, 위도]} 순서다. 어느 길로
     *     가는지 모르면 {@code null} 이고, 그때 두 점을 이은 직선을 대신 넣지 않는다
     *     (S15P21E201-1251)
     * @param durationMin 이동 분 — 실제 이동으로 고친 배율이 켜져 있으면 고친 값이다. 화면과 시각 깔기가 이 값을 쓴다
     * @param uncalibratedDurationMin 엔진이 처음 어림한 이동 분(보정 전). 보정 계산이 「실제 ÷ 이 값」을 잰다 — 고친 값과
     *     견주면 배율이 겹쳐 곱해진다(S15P21E201-1700)
     * @param pieces {@code path} 를 경사·계단이 같은 조각으로 나눈 것. 선형이 없거나 조각을 모르면 {@code null}
     */
    public record DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId,
            String travelMode, Integer distanceM, Integer durationMin, Integer walkingMeters,
            com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus, Integer fareKrw,
            java.util.List<double[]> path, Integer uncalibratedDurationMin,
            java.util.List<com.gabolle.backend.itinerary.domain.ItineraryLeg.Piece> pieces) {

        /** 경사 조각 칸 이전의 생성자. 조각 없이 만든 구간이다. */
        public DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId, String travelMode,
                Integer distanceM, Integer durationMin, Integer walkingMeters,
                com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus, Integer fareKrw,
                java.util.List<double[]> path, Integer uncalibratedDurationMin) {
            this(dayIndex, sequence, fromPlaceId, toPlaceId, travelMode, distanceM, durationMin,
                    walkingMeters, dataStatus, fareKrw, path, uncalibratedDurationMin, null);
        }

        /** 보정 전 칸 이전의 생성자. 보정 없이 만든 구간이다. */
        public DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId, String travelMode,
                Integer distanceM, Integer durationMin, Integer walkingMeters,
                com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus, Integer fareKrw,
                java.util.List<double[]> path) {
            this(dayIndex, sequence, fromPlaceId, toPlaceId, travelMode, distanceM, durationMin,
                    walkingMeters, dataStatus, fareKrw, path, null);
        }

        public DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId, String travelMode,
                Integer distanceM, Integer durationMin, Integer walkingMeters,
                com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus) {
            this(dayIndex, sequence, fromPlaceId, toPlaceId, travelMode, distanceM, durationMin,
                    walkingMeters, dataStatus, null, null);
        }

        /** 선형 칸 이전의 생성자. 부르는 곳이 여럿이라 한 번에 안 고친다. */
        public DraftLeg(int dayIndex, int sequence, UUID fromPlaceId, UUID toPlaceId, String travelMode,
                Integer distanceM, Integer durationMin, Integer walkingMeters,
                com.gabolle.backend.itinerary.domain.ItineraryItem.DataStatus dataStatus, Integer fareKrw) {
            this(dayIndex, sequence, fromPlaceId, toPlaceId, travelMode, distanceM, durationMin,
                    walkingMeters, dataStatus, fareKrw, null);
        }
    }
}
