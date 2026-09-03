package com.gabolle.backend.trip.presentation.dto;

import com.gabolle.backend.trip.domain.TripConstraint;

/**
 * 여행에 걸린 조건 한 줄 — {@code GET /api/v1/trips/{tripId}} 응답의 일부.
 *
 * <p>🔴 {@code value} 를 그대로 내보내도 안전하다. 민감 종류(알레르기 등)는
 * {@link TripConstraint} 생성자가 값 있는 저장 자체를 M1 에서 거부하므로,
 * 여기 도달한 값은 애초에 민감하지 않은 것만 남는다.
 */
public record TripConstraintDto(
		String constraintId,
		String type,
		String severity,
		String operator,
		String value,
		Double threshold,
		String evidenceStatus) {

	public static TripConstraintDto of(TripConstraint c) {
		return new TripConstraintDto(
				c.constraintId(), c.type(), c.severity().name(),
				c.operator(), c.value(), c.threshold(), c.evidenceStatus().name());
	}
}
