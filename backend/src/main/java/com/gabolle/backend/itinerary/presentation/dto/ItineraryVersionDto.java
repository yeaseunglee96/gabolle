package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

/**
 * 일정 한 판의 응답 — API 명세 4.4 ItineraryVersion.
 */
public record ItineraryVersionDto(
        String itineraryId,
        int version,
        Integer baseVersion,
        String createdBy,
        /** CREATE · REGENERATE · REGENERATE_DAY · REPLACE_ITEM · REMOVE_ITEM · LOCK_ITEM · REORDER */
        String operation,
        String requestId,
        /** 항상 "Asia/Seoul" — 단위·시간대를 공통 사전으로 고정한다 (API-03) */
        String timezone,
        List<Day> days,
        BudgetSummary budgetSummary,
        WalkingSummary walkingSummary,
        List<String> warnings) {

    public record Day(String date, List<Item> items, List<Leg> legs) {}

    public record Item(
            String itemId,
            int sequence,
            String placeId,
            String startTime,
            String endTime,
            Integer stayMinutes,
            /** 사용자가 "이건 고정" 한 항목. 재계산해도 자리를 지킨다. */
            boolean locked,
            List<String> reasonCodes) {}

    /** 🔴 ascentM·stairSteps 가 bigData 의 경사·계단 데이터가 닿는 지점이다. */
    public record Leg(
            int sequence,
            String mode,
            String fromPlaceId,
            String toPlaceId,
            Integer distanceM,
            Integer durationMin,
            Integer walkingMeters,
            Integer ascentM,
            Integer stairSteps) {}

    public record BudgetSummary(
            Integer targetKrw, Integer capKrw, Integer estimatedKrw, boolean exceeded) {}

    public record WalkingSummary(
            Integer targetMeters, Integer capMeters, Integer estimatedMeters, boolean exceeded) {}
}
