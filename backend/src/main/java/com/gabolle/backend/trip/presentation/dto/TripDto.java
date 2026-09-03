package com.gabolle.backend.trip.presentation.dto;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import java.util.Map;

/**
 * 여행 응답 — TRIP-01 은 {@code 201 TripDto + preferenceSnapshot} 을 돌려준다.
 *
 * <p>🔴 스냅샷을 함께 주는 이유 — 화면이 곧바로 {@code REC-01} 을 부를 때
 * {@code preferenceSnapshotVersion} 이 필요하다. 안 주면 조회를 한 번 더 해야 한다.
 */
public record TripDto(
        String tripId,
        String createdBy,
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
     */
    public record PreferenceSnapshotDto(
            String snapshotId,
            int version,
            Map<String, String> dimensions) {
    }

    public static TripDto of(Trip t, PreferenceSnapshot s) {
        return new TripDto(
                t.tripId(), t.createdBy(),
                t.startDate().toString(), t.finishDate().toString(),
                t.originLat(), t.originLng(),
                t.budgetKrw(), t.partySize(),
                t.timeWindow(), t.timezone(),
                t.status().name(), t.days(),
                s == null ? null
                        : new PreferenceSnapshotDto(s.snapshotId(), s.version(), s.dimensions()));
    }
}
