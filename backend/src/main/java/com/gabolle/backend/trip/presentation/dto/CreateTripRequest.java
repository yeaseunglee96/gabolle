package com.gabolle.backend.trip.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 여행 생성 요청 — TRIP-01 {@code POST /api/v1/trips}.
 *
 * <p>명세가 받는 것으로 {@code dates · origin · budget · party · transport · timezone}
 * 을 지정한다. 네 단계 화면에서 모은 조건이 하나로 온다.
 *
 * <p>🔴 필수 항목이 빠지면 <b>어느 항목이 빠졌는지</b>가 응답에 들어가야 한다
 * (티켓 완료 기준). {@code @NotNull} 이 필드 이름을 담아 준다.
 */
public record CreateTripRequest(

        @NotNull LocalDate startDate,
        @NotNull LocalDate finishDate,

        /** 출발지. 매일 여기서 일정이 시작된다. */
        Double originLat,
        Double originLng,

        Integer budgetKrw,

        @NotNull @Min(1) Integer partySize,

        /** 하루 활동 시간대. 예: {@code MORNING_TO_EVENING} */
        String timeWindow,

        /** 비우면 {@code Asia/Seoul}. API-03 이 시간대를 공통 사전으로 고정한다. */
        String timezone,

        /** 명시 취향. 예: {@code {"pace":"RELAXED","theme":"NATURE"}} */
        Map<String, String> preferences,

        @Valid List<ConstraintInput> constraints) {

    /**
     * 사용자가 반드시(HARD) 또는 가급적(SOFT) 지키길 원하는 조건.
     *
     * <p>🔴 알레르기·건강 식단은 <b>M1 에서 값을 받지 않는다.</b> 암호화 경로가
     * 준비되지 않았고, 평문으로 한 번 저장하면 그 데이터가 남는다.
     */
    public record ConstraintInput(
            /** {@code ALLERGY} · {@code DIET} · {@code MOBILITY} · {@code BUDGET} */
            @NotNull String type,
            /** {@code HARD} 또는 {@code SOFT} */
            @NotNull String severity,
            /** {@code EXCLUDES} · {@code LTE} · {@code GTE} */
            String operator,
            String value,
            Double threshold) {
    }
}
