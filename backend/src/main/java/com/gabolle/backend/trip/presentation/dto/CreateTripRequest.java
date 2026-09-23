package com.gabolle.backend.trip.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import com.gabolle.backend.place.api.PlaceSnapshotRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 여행 생성 요청. 네 단계 화면에서 모은 조건이 하나로 온다.
 *
 * <p>{@code scope}(계정 기본값 / 이번 여행 전용)는 요청에 없다. 이 엔드포인트가 만드는 스냅샷·
 * 제약은 항상 이번 여행 전용({@link com.gabolle.backend.trip.domain.PersonalizationScope#TRIP})
 * 이고 서버가 고정한다.
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
         * 씀씀이 성향. 안 보내면 계정 기본값으로 채워지고, 그것도 없으면 빈 채로 남는다.
         * {@code preferences} 목록 밖에 따로 두는 것은 목록 계약을 다시 건드리지 않기 위해서다 —
         * 서버 안에서는 {@link CreateTripRequestMapper} 가 목록의 다른 답과 같은 모양으로 합친다.
         */
        @Valid SpendProfileAnswerInput spendProfile,

        @Valid List<ConstraintInput> constraints,

        /** 숙소의 {@code place_id}. 안 보내면 아직 안 정한 것이다 — 필수가 아니다. */
        String accommodationPlaceId,

        /** 영어 메뉴가 있는 곳을 우선한다. 안 보내면 {@code false}(우선하지 않음). */
        Boolean englishMenuRequired,

        /** 해외 카드를 받는 곳을 우선한다. 안 보내면 {@code false}. */
        Boolean foreignCardRequired,

        /** 혼밥하기 편한 곳을 우선한다. 안 보내면 {@code false}. */
        Boolean soloFriendlyPriority,

        /**
         * 대중교통 최대 환승 횟수. 안 보내면 제한 없음. 이동 수단에 자차(PRIVATE_CAR)가 있으면
         * 서버가 이 값을 무시한다.
         */
        @Min(0) Integer maxTransitTransfers,

        /**
         * "꼭 가고 싶은 장소" 의 {@code place_id} 목록. 보낸 순서대로 {@code trip_seed_place} 에
         * 적히고 추천 엔진이 그 장소를 후보 앞으로 올린다. 안 보내도 된다.
         */
        List<String> mustVisitPlaceIds,

        /**
         * 여행 범위. {@link com.gabolle.backend.trip.domain.TravelArea} 코드다. 안 보내면 출발지
         * 하나를 중심으로 후보를 고르고, 보내면 그 지역들에서 고른다.
         */
        List<String> travelAreas,

        /**
         * 우리 표에 없는 숙소를 골랐을 때 그 자리에서 보내는 스냅샷 — S15P21E201-1522.
         *
         * <p>숙소 검색은 출발지 검색을 재사용하고 그 결과는 카카오에서 오므로 우리
         * {@code place_id} 가 없다. 그래서 지금까지 앱이 좌표로 보냈고 서버에 받을 칸이 없어
         * <b>그대로 버려졌다.</b> 이 칸이 있으면 서버가 장소를 찾거나 만들어
         * {@code trip.accommodation_place_id} 에 넣는다.
         *
         * <p>🔴 {@code accommodationPlaceId} 가 있으면 <b>이 칸은 안 본다.</b> 우리 표의 숙소를
         * 고른 것이 확실한데 스냅샷을 또 보면, 둘이 어긋났을 때 어느 쪽이 맞는지 서버가
         * 정하게 된다.
         */
        @Valid PlaceSnapshotRequest accommodation,

        /**
         * 묵는 동네 — {@code TravelArea} 코드다({@code HAEUNDAE}·{@code SEOMYEON} 등).
         * S15P21E201-1544.
         *
         * <p>앱의 숙소 칸은 검색어가 없을 때 추천 동네를 먼저 보여 준다. 그것을 위
         * {@code accommodation} 스냅샷으로 보내면 장소 행 「해운대」가 생기는데, 그 동네는
         * 서버에 {@code TravelArea} 로 <b>이미 있고 좌표까지 같다.</b> 장소로도 만들면 같은
         * 동네가 세 벌이 되므로 코드로 받는다.
         *
         * <p>🔴 모르는 코드가 와도 <b>400 이 아니다.</b> 그 칸만 비운다 —
         * {@code TravelArea.of} 가 그렇게 만들어져 있고 이유도 적혀 있다: 앱이 새 지역을 먼저
         * 내보내는 날 여행 생성이 막히면 안 된다.
         */
        @Size(max = 30) String accommodationArea) {

    /** 안 보냈으면 빈 목록이다. */
    public List<String> travelAreasOrEmpty() {
        return travelAreas == null ? List.of() : travelAreas;
    }

    /** 안 보냈으면 빈 목록이다. */
    public List<String> mustVisitPlaceIdsOrEmpty() {
        return mustVisitPlaceIds == null ? List.of() : mustVisitPlaceIds;
    }

    /** 짧은 생성자들은 뒤에 붙은 칸을 {@code null} 로 채운다 — 옛 요청 모양도 유효한 요청이다. */
    public CreateTripRequest(
            LocalDate startDate, LocalDate finishDate,
            Double originLat, Double originLng,
            Integer budgetKrw, Integer partySize,
            String timeWindow, String timezone,
            List<PreferenceAnswerInput> preferences,
            List<ConstraintInput> constraints) {
        this(startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                preferences, null, constraints, null, null, null, null, null, null, null, null, null);
    }

    public CreateTripRequest(
            LocalDate startDate, LocalDate finishDate,
            Double originLat, Double originLng,
            Integer budgetKrw, Integer partySize,
            String timeWindow, String timezone,
            List<PreferenceAnswerInput> preferences,
            SpendProfileAnswerInput spendProfile,
            List<ConstraintInput> constraints) {
        this(startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                preferences, spendProfile, constraints, null, null, null, null, null, null, null, null, null);
    }

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
                foreignCardRequired, soloFriendlyPriority, maxTransitTransfers, null, null, null, null);
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
     * {@code value} 는 {@code answerStatus == "SELECTED"} 일 때만 채운다. 상태와 값 유무가
     * 어긋나면 서버가 400 으로 거부한다.
     */
    public record PreferenceAnswerInput(
            @NotBlank String dimension,
            String value,
            /** {@code SELECTED} · {@code SKIPPED} · {@code UNKNOWN} */
            @NotBlank String answerStatus) {
    }

    /**
     * 사용자가 반드시(HARD) 또는 가급적(SOFT) 지키길 원하는 조건. 알레르기·필수 식단의 자유 입력은
     * 아직 받지 않는다 — 암호화 경로가 없고 평문으로 한 번 저장하면 그 데이터가 남는다.
     *
     * <p>{@code value}·{@code threshold} 는 {@code answerStatus == "SELECTED"} 일 때만 채운다.
     */
    public record ConstraintInput(
            /** {@code ALLERGY} · {@code DIET} · {@code MOBILITY} · {@code BUDGET} */
            @NotNull String type,
            /**
             * 종류 안에서 무엇에 대한 사실인가 — 알레르기 코드({@code PEANUT}), 식단 코드, 이동
             * 조건 이름({@code MAX_WALKING_METERS}). 자유 입력이면 {@code "OTHER"} 이고, 민감
             * 종류는 그때만 거부된다.
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
             * {@code type == "DIET"} 일 때만 채운다 — {@code REQUIRED}(의료·종교상 필수) ·
             * {@code PREFERRED}(선호). 민감 정보 판정이 이 값을 보므로, 비우면 필수 식단이
             * 일반 로그로 새어 나간다.
             */
            String dietRequirement) {
    }
}
