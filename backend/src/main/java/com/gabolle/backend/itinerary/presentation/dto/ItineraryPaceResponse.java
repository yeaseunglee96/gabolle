package com.gabolle.backend.itinerary.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 하루치 지연 경고 조회 응답.
 * {@code paceFactor} 가 {@code null} 이면 아직 속도 계수를 만들 만큼 방문 기록이 없다는 뜻이다.
 * 이때 1.0(계획대로)을 대신 채우지 않는다 — 그러면 화면이 틀린 안심을 그린다. 왜 못 정했는지는
 * {@link #notChecked()} 에 남는다.
 */
public record ItineraryPaceResponse(
        String itineraryId,
        int dayIndex,

        /** 속도 계수. 표본이 모자라 아직 못 만들었으면 {@code null}. */
        BigDecimal paceFactor,

        /**
         * 계수를 만든 표본 수. 계수가 없을 때는 지금까지 모인 표본 수(0 이상)를 그대로 담는다 —
         * "몇 개 중 몇 개가 모였는지" 를 화면이 보여줄 수 있게 한다.
         */
        int sampleCount,

        /** 계수를 만들려면 몇 개가 필요한가. {@code PaceFactorCalculator.MIN_SAMPLES} 와 같은 값. */
        int minSamples,

        /** 그날 계획상 끝나는 시각. 항목에 시각이 하나도 없으면 {@code null}. */
        String plannedDayEnd,

        List<Item> items,

        /** 하루 끝을 넘길 위험이 있는 항목의 id 만. 화면이 목록을 따로 강조할 때 쓴다. */
        List<String> atRiskItemIds,

        /**
         * 못 한 검사와 그 이유. 계수를 못 만들었으면 {@code PACE_FACTOR}/{@code NOT_ENOUGH_RECORDS}
         * 가 담긴다. 비어 있으면 계수를 실제로 구해서 썼다는 뜻이다.
         */
        List<NotChecked> notChecked) {

    /**
     * 방문지 하나의 예측.
     *
     * @param itemId 항목의 열쇠. {@code ItineraryDelayProjector.Entry.itemKey()} 를 그대로 옮긴 것이다
     * @param visited 이미 다녀온 곳인가. 참이면 아래 시각들은 기록된 사실 그대로다
     * @param plannedArrival 원래 계획된 도착. 계획 시각이 없으면 {@code null}
     * @param delayMinutes 계획보다 몇 분 늦나. 음수면 이르다. 계획 시각이 없으면 {@code null}
     * @param atRisk 이 항목이 하루 끝을 넘길 위험이 있나
     */
    public record Item(
            String itemId,
            boolean visited,
            String predictedArrival,
            String predictedDeparture,
            String plannedArrival,
            Long delayMinutes,
            boolean atRisk) {
    }

    public record NotChecked(String check, String reason) {
    }
}
