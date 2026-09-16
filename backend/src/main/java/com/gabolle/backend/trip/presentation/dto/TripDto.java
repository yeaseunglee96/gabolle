package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 여행 응답 — TRIP-01 은 {@code 201 TripDto + preferenceSnapshot} 을 돌려준다.
 *
 * <p>🔴 스냅샷을 함께 주는 이유 — 화면이 곧바로 {@code REC-01} 을 부를 때
 * {@code preferenceSnapshotVersion} 이 필요하다. 안 주면 조회를 한 번 더 해야 한다.
 */
public record TripDto(
        String tripId,
        String createdBy,
        /**
         * 사용자가 붙인 이름 — S15P21E201-1023. {@code null} 이면 아직 이름이 없다.
         * 서버가 날짜 문자열을 대신 채우지 않는다 — 그러면 사용자가 붙인 이름과
         * 서버가 만든 이름이 같은 칸에서 구분이 안 된다.
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
        /** 매일 여기서 시작하고 여기로 돌아온다. 안 정했으면 {@code null} (S15P21E201-456). */
        String accommodationPlaceId,
        boolean englishMenuRequired,
        boolean foreignCardRequired,
        boolean soloFriendlyPriority,
        /** {@code null} 이면 제한 없음(또는 자차 이동이라 뜻이 없어 무시됨). */
        Integer maxTransitTransfers,
        PreferenceSnapshotDto preferenceSnapshot) {

    /**
     * 선호 스냅샷.
     *
     * <p>🔴 {@code snapshotId}(UUID)와 {@code version}(정수)을 둘 다 준다.
     * <ul>
     *   <li>{@code version} — {@code REC-01} 이 받는 값. 순서 비교에 쓴다</li>
     *   <li>{@code snapshotId} — 로그·이벤트가 가리키는 값(S15P21E201-542 3장)</li>
     * </ul>
     * UUID 에는 순서가 없어서 "내가 본 판이 최신인가" 를 판정할 수 없다.
     *
     * <p>🔴 2026-09-03 — {@code dimensions: Map<String,String>} 을
     * {@code answers: List<PreferenceAnswerDto>} 로 바꿨다. Map 은 "값 있음" 과
     * "값 없음" 만 말할 수 있어서 SKIPPED(건너뜀)와 UNKNOWN(안 물어봄)이 응답에서도
     * 똑같이 사라졌다. {@code PreferenceSnapshot} 도메인과 같은 이유다.
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
