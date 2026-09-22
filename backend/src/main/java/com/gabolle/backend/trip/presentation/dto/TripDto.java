package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 여행 응답. 선호 스냅샷을 함께 주는 것은 화면이 곧바로 추천을 부를 때
 * {@code preferenceSnapshotVersion} 이 필요해서다 — 안 주면 조회를 한 번 더 해야 한다.
 */
public record TripDto(
        String tripId,
        String createdBy,
        /**
         * 사용자가 붙인 이름. {@code null} 이면 아직 이름이 없다. 서버가 날짜 문자열을 대신
         * 채우지 않는다 — 그러면 사용자가 붙인 이름과 서버가 만든 이름이 구분되지 않는다.
         */
        String title,
        String startDate,
        String finishDate,
        Double originLat,
        Double originLng,
        Integer budgetKrw,
        int partySize,
        String timeWindow,
        String timezone,
        String status,
        int days,
        /** 매일 여기서 시작하고 여기로 돌아온다. 안 정했으면 {@code null}. */
        String accommodationPlaceId,
        boolean englishMenuRequired,
        boolean foreignCardRequired,
        boolean soloFriendlyPriority,
        /** {@code null} 이면 제한 없음(또는 자차 이동이라 뜻이 없어 무시됨). */
        Integer maxTransitTransfers,
        PreferenceSnapshotDto preferenceSnapshot) {

    /**
     * 선호 스냅샷. {@code snapshotId}(UUID)와 {@code version}(정수)을 둘 다 준다 — 로그·이벤트는
     * ID 로 가리키지만, UUID 에는 순서가 없어 "내가 본 판이 최신인가" 는 {@code version} 으로만
     * 판정할 수 있다.
     *
     * <p>답을 Map 이 아니라 목록으로 내는 것은 SKIPPED(건너뜀)와 UNKNOWN(안 물어봄)을 구분하기
     * 위해서다. Map 은 값의 있고 없음만 말할 수 있어 둘이 똑같이 사라진다.
     */
    public record PreferenceSnapshotDto(
            String snapshotId,
            int version,
            String scope,
            List<PreferenceAnswerDto> answers) {
    }

    /** 취향 답 하나. {@code valueJson} 은 {@code status == "SELECTED"} 일 때만 있다. */
    public record PreferenceAnswerDto(String dimension, String valueJson, String status) {

        public static PreferenceAnswerDto of(PreferenceSnapshot.PreferenceAnswer a) {
            return new PreferenceAnswerDto(a.dimension(), a.valueJson(), a.status().name());
        }
    }

    public static TripDto of(Trip t, PreferenceSnapshot s) {
        return new TripDto(
                t.tripId(), t.createdBy(), t.title(),
                t.startDate().toString(), t.finishDate().toString(),
                t.originLat(), t.originLng(),
                t.budgetKrw(), t.partySize(),
                t.timeWindow(), t.timezone(),
                t.status().name(), t.days(),
                t.accommodationPlaceId(),
                t.englishMenuRequired(), t.foreignCardRequired(), t.soloFriendlyPriority(),
                t.maxTransitTransfers(),
                s == null ? null
                        : new PreferenceSnapshotDto(
                                s.snapshotId(), s.version(), s.scope().name(),
                                s.answers().stream().map(PreferenceAnswerDto::of).toList()));
    }
}
