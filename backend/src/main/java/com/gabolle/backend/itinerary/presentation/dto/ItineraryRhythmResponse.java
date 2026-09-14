package com.gabolle.backend.itinerary.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 여행 전체의 리듬 요약 응답 — S15P21E201-308.
 *
 * <p>{@code travelShare} 와 {@code plannedVsActual} 은 잴 수 없으면 각각 {@code null} 이다.
 * 0.0 과 {@code null} 은 다른 사실을 말한다 — 0.0 은 "재 보니 그 값이었다", {@code null} 은
 * "아직 못 쟀다"다. 못 잰 이유는 {@link #notChecked()} 에 남는다.
 */
public record ItineraryRhythmResponse(
        String itineraryId,

        int dayCount,

        /** 하루 평균 항목 수. 소수 둘째 자리 반올림. */
        double averageItemsPerDay,

        /**
         * 이동시간 / (이동시간 + 머문시간). 일정의 구간 중 이동 시간을 하나도 못 쟀으면(길찾기
         * 응답이 하나도 없었으면) {@code null} — 0.0(이동이 없었다는 관측)과 구분한다.
         */
        Double travelShare,

        /** 계획 대비 실제 배수. 표본이 모자라 못 만들었으면 {@code null}. */
        BigDecimal plannedVsActual,

        /** {@code plannedVsActual} 을 만든(또는 만들다 만) 표본 수. 계수가 없으면 지금까지 모인 수. */
        int sampleCount,

        /** 못 한 검사와 그 이유. {@code plannedVsActual} 이 {@code null} 이면 여기 이유가 담긴다. */
        List<NotChecked> notChecked) {

    /**
     * @param check  못 한 검사의 이름
     * @param reason 왜 못 했나
     */
    public record NotChecked(String check, String reason) {
    }
}
