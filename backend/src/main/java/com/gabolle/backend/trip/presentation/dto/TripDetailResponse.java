package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;

/**
 * 여행 조회 응답 — {@code GET /api/v1/trips/{tripId}}.
 *
 * <p>🔴 {@link TripDto} 를 그대로 재사용하지 않고 감쌌다. {@code POST /trips} 응답에
 * 조건 목록을 넣으려면 {@link com.gabolle.backend.trip.application.TripCreationService.Result}
 * 에 조건 목록을 추가해야 하는데, 그건 이미 머지되고 테스트된 생성 경로를 다시 건드리는
 * 것이다. 조회 전용 응답을 새로 두면 생성 경로는 그대로 두고 완료 기준
 * ("조회하면 보낸 조건이 그대로 나온다")만 채울 수 있다.
 */
public record TripDetailResponse(TripDto trip, List<TripConstraintDto> constraints) {

	public static TripDetailResponse of(Trip t, List<TripConstraint> constraints, PreferenceSnapshot s) {
		return new TripDetailResponse(
				TripDto.of(t, s),
				constraints.stream().map(TripConstraintDto::of).toList());
	}
}
