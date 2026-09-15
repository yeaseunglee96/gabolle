package com.gabolle.backend.trip.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 여행 생성 요청 — TRIP-01 {@code POST /api/v1/trips}.
 *
 * <p>명세가 받는 것으로 {@code dates · origin · budget · party · transport · timezone}
 * 을 지정한다. 네 단계 화면에서 모은 조건이 하나로 온다.
 *
 * <p>🔴 필수 항목이 빠지면 <b>어느 항목이 빠졌는지</b>가 응답에 들어가야 한다
 * (티켓 완료 기준). {@code @NotNull} 이 필드 이름을 담아 준다.
 *
 * <p>🔴 2026-09-03 — {@code preferences: Map<String,String>} 을
 * {@code List<PreferenceAnswerInput>} 으로, {@code ConstraintInput} 에
 * {@code answerStatus} 를 추가했다. FE 가 "골랐다/건너뜀/안 물어봄" 을 이미
 * 구분해서 보낼 준비가 됐는데(고지혁 님 확인), Map 으로는 그 구분을 받을 수 없었다.
 *
 * <p>🔴 {@code scope}(계정 기본값 / 이번 여행 전용)는 요청에 없다. TRIP-01 이 만드는
 * 스냅샷·제약은 <b>항상 이번 여행 전용</b>이다 — {@code trip_id} 가 항상 있는 자리라서
 * 서버가 고정한다({@link com.gabolle.backend.trip.domain.PersonalizationScope#TRIP}).
 * 계정 기본값(USER)을 만드는 흐름은 이 엔드포인트가 아니다.
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

        /** 명시 취향. 차원마다 답변 상태(골랐다/건너뜀/안 물어봄)를 함께 받는다. */
        @Valid List<PreferenceAnswerInput> preferences,

        /**
         * 씀씀이 성향 — S15P21E201-709. 선택 칸이다. 안 보내면 이 여행의 SPEND_PROFILE 은
         * 계정 기본값(있으면)으로 채워지고, 계정 기본값도 없으면 빈 채로 남는다.
         *
         * <p>🔴 다른 여덟 차원처럼 {@code preferences} 목록에 넣지 않고 따로 둔 이유 —
         * 그 목록은 이미 화면 네 단계가 함께 채우는 자리이고, 씀씀이 화면은 그 뒤에 별도로
         * 추가되는 칸이라 목록 계약을 다시 건드리지 않기 위해서다. 서버 안에서는
         * {@link CreateTripRequestMapper} 가 이 값을 {@code preferences} 목록의 다른 답과
         * 같은 모양({@code PreferenceSnapshot.PreferenceAnswer}, dimension=SPEND_PROFILE)
         * 으로 합쳐서 그 뒤(계정 기본값 겹치기 등)는 완전히 같은 길을 탄다.
         */
        @Valid SpendProfileAnswerInput spendProfile,

        @Valid List<ConstraintInput> constraints,

        /**
         * 🔴 S15P21E201-456 — 매일 여기서 시작하고 여기로 돌아온다. {@code place_id}(UUID
         * 문자열)를 가리킨다. 안 보내면 아직 안 정한 것이다 — 필수로 두지 않는다. 화면이
         * 숙소를 나중 단계에서 고를 수도 있다.
         */
        String accommodationPlaceId,

        /** 영어 메뉴가 있는 곳을 우선한다. 안 보내면 {@code false}(우선하지 않음). */
        Boolean englishMenuRequired,

        /** 해외 카드를 받는 곳을 우선한다. 안 보내면 {@code false}. */
        Boolean foreignCardRequired,

        /** 혼밥하기 편한 곳을 우선한다. 안 보내면 {@code false}. */
        Boolean soloFriendlyPriority,

        /**
         * 대중교통 최대 환승 횟수. 안 보내면 제한 없음. 🔴 이동 수단에 자차(PRIVATE_CAR)가
         * 있으면 서버가 이 값을 무시한다 — {@code TripCreationService} 참고.
         */
        @Min(0) Integer maxTransitTransfers,

        /**
         * 취향 단계에서 고른 "꼭 가고 싶은 장소" 의 {@code place_id} 목록 — S15P21E201-973.
         *
         * <p>안 보내면 지금까지와 똑같이 동작한다. 보내면 그 순서대로 {@code trip_seed_place}
         * 에 적히고, 추천 엔진이 그 장소를 후보 앞으로 올린다({@code SeedBoost}).
         *
         * <p>이 칸이 생기기 전에는 앱이 고른 장소를 기기 안에만 두고 보내지 않았다. 화면은
         * "일정에 반드시 포함돼요" 라고 적어 두고 있었는데 서버는 그 목록을 받은 적이 없었다.
         */
        List<String> mustVisitPlaceIds,

        /**
         * 기본 정보 화면에서 고른 여행 범위 — S15P21E201-980. 앱의 {@code AREAS} 코드다
         * (HAEUNDAE·GWANGALLI·NAMPO·SEOMYEON·YEONGDO·SONGJEONG).
         *
         * <p>안 보내면 지금까지와 똑같이 동작한다 — 출발지 하나를 중심으로 후보를 고른다.
         * 보내면 추천이 그 지역들에서 후보를 고른다.
         *
         * <p>이 칸이 생기기 전에는 칩이 화면에서만 받고 서버로 오지 않았다. 해운대를 골라도
         * 추천 스무 곳이 전부 출발지 근처였다.
         */
        List<String> travelAreas) {

    /** 안 보냈으면 빈 목록이다. */
    public List<String> travelAreasOrEmpty() {
        return travelAreas == null ? List.of() : travelAreas;
    }

    /** 안 보냈으면 빈 목록이다 — 부르는 쪽이 매번 null 을 보지 않게 여기서 한 번 고른다. */
    public List<String> mustVisitPlaceIdsOrEmpty() {
        return mustVisitPlaceIds == null ? List.of() : mustVisitPlaceIds;
    }

    /**
     * 🔴 spendProfile(709)·다섯 칸(456)이 생기기 전의 호출부(테스트 등)를 그대로 남긴다.
     * 새 칸은 전부 기본값(null·false)으로 채운다 — 옛 요청 모양도 여전히 유효한 요청이어야
     * 한다.
     */
    public CreateTripRequest(
            LocalDate startDate, LocalDate finishDate,
            Double originLat, Double originLng,
            Integer budgetKrw, Integer partySize,
            String timeWindow, String timezone,
            List<PreferenceAnswerInput> preferences,
            List<ConstraintInput> constraints) {
        this(startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                preferences, null, constraints, null, null, null, null, null, null, null);
    }

    /**
     * 🔴 다섯 칸(456)이 생기기 전의 호출부(테스트 등)를 그대로 남긴다 — {@code spendProfile}
     * 만 받고 싶은 자리가 있다.
     */
    public CreateTripRequest(
            LocalDate startDate, LocalDate finishDate,
            Double originLat, Double originLng,
            Integer budgetKrw, Integer partySize,
            String timeWindow, String timezone,
            List<PreferenceAnswerInput> preferences,
            SpendProfileAnswerInput spendProfile,
            List<ConstraintInput> constraints) {
        this(startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                preferences, spendProfile, constraints, null, null, null, null, null, null, null);
    }

    /**
     * 🔴 꼭 가고 싶은 장소(973)가 생기기 전의 열여섯 칸 시그니처를 그대로 남긴다 —
     * 기존 호출부(기능 테스트 다수)가 그 모양으로 요청을 만든다. 새 칸은 {@code null} 이고
     * {@link #mustVisitPlaceIdsOrEmpty()} 가 빈 목록으로 읽는다.
     */
    public CreateTripRequest(
            LocalDate startDate, LocalDate finishDate,
            Double originLat, Double originLng,
            Integer budgetKrw, Integer partySize,
            String timeWindow, String timezone,
            List<PreferenceAnswerInput> preferences,
            SpendProfileAnswerInput spendProfile,
            List<ConstraintInput> constraints,
            String accommodationPlaceId,
            Boolean englishMenuRequired,
            Boolean foreignCardRequired,
            Boolean soloFriendlyPriority,
            Integer maxTransitTransfers) {
        this(startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                preferences, spendProfile, constraints, accommodationPlaceId, englishMenuRequired,
                foreignCardRequired, soloFriendlyPriority, maxTransitTransfers, null, null);
    }

    /** 안 보냈으면 우선하지 않는 것으로 본다. */
    public boolean englishMenuRequiredOrDefault() {
        return this.englishMenuRequired != null && this.englishMenuRequired;
    }

    public boolean foreignCardRequiredOrDefault() {
        return this.foreignCardRequired != null && this.foreignCardRequired;
    }

    public boolean soloFriendlyPriorityOrDefault() {
        return this.soloFriendlyPriority != null && this.soloFriendlyPriority;
    }

    /**
     * 취향 차원 하나에 대한 답.
     *
     * <p>🔴 {@code value} 는 {@code answerStatus == "SELECTED"} 일 때만 채운다.
     * 건너뛰었거나(SKIPPED) 안 물어봤으면(UNKNOWN) 비운다 — 값을 채우면서 상태를
     * 다르게 보내면 서버가 400 으로 거부한다({@code PreferenceSnapshot.PreferenceAnswer}).
     */
    public record PreferenceAnswerInput(
            @NotBlank String dimension,
            String value,
            /** {@code SELECTED} · {@code SKIPPED} · {@code UNKNOWN} */
            @NotBlank String answerStatus) {
    }

    /**
     * 사용자가 반드시(HARD) 또는 가급적(SOFT) 지키길 원하는 조건.
     *
     * <p>🔴 알레르기·필수(REQUIRED) 식단은 <b>M1 에서 값을 받지 않는다.</b> 암호화 경로가
     * 준비되지 않았고, 평문으로 한 번 저장하면 그 데이터가 남는다.
     *
     * <p>🔴 {@code value}·{@code threshold} 는 {@code answerStatus == "SELECTED"}
     * 일 때만 채운다. "없다"(NONE)·"안 물어봄"(UNKNOWN)이면 둘 다 비운다.
     */
    public record ConstraintInput(
            /** {@code ALLERGY} · {@code DIET} · {@code MOBILITY} · {@code BUDGET} */
            @NotNull String type,
            /**
             * 🔴 2026-09-04 추가. 종류 안에서 무엇에 대한 사실인가 — {@code ALLERGY} 면
             * 알레르기 코드({@code PEANUT} 등, 자유 입력이면 {@code "OTHER"}),
             * {@code DIET} 면 식단 코드, {@code MOBILITY} 면 이동 조건 이름
             * ({@code MAX_WALKING_METERS} 등). 민감 종류는 이 값이 {@code "OTHER"}
             * 일 때만 거부된다 — 코드로 된 값은 구조화된 정보라 안전하다.
             */
            @NotBlank String constraintKey,
            /** {@code HARD} 또는 {@code SOFT} */
            @NotNull String severity,
            /** {@code EXCLUDES} · {@code LTE} · {@code GTE} */
            String operator,
            String value,
            Double threshold,
            /** {@code SELECTED} · {@code NONE} · {@code UNKNOWN} */
            @NotBlank String answerStatus,
            /**
             * 🔴 2026-09-04 추가. {@code type == "DIET"} 일 때만 채운다 —
             * {@code REQUIRED}(의료·종교상 필수) · {@code PREFERRED}(선호).
             * 민감 정보 판정({@code TripConstraint.isSensitive})이 이 값을 본다 —
             * 없으면(null) 필수 식단이 일반 로그로 새어 나간다(고지혁 님 실측).
             */
            String dietRequirement) {
    }
}
