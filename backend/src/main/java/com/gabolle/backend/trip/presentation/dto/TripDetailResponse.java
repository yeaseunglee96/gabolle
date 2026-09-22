package com.gabolle.backend.trip.presentation.dto;

import java.util.List;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;

/**
 * 여행 조회 응답 — {@code GET /api/v1/trips/{tripId}}. {@link TripDto} 를 감싸 조건 목록을
 * 함께 낸다. 생성 응답({@code POST /trips})에는 조건 목록이 없다.
 */
public record TripDetailResponse(TripDto trip, List<TripConstraintDto> constraints) {

	public static TripDetailResponse of(Trip t, List<TripConstraint> constraints, PreferenceSnapshot s) {
		return new TripDetailResponse(
				TripDto.of(t, s),
				constraints.stream().map(TripConstraintDto::of).toList());
	}
}
