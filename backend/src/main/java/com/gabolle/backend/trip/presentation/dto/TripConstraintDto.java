package com.gabolle.backend.trip.presentation.dto;

import com.gabolle.backend.trip.domain.TripConstraint;

/**
 * 여행에 걸린 조건 한 줄 — {@code GET /api/v1/trips/{tripId}} 응답의 일부.
 *
 * <p>🔴 {@code value} 를 그대로 내보내도 안전하다. 민감 종류(알레르기 등)의 자유
 * 입력({@code constraintKey == "OTHER"})은 {@link TripConstraint} 생성자가 저장 자체를
 * 거부한다 — 여기 도달한 값은 코드로 된 것(정보가 {@code constraintKey} 에 있고
 * {@code value} 는 비어 있음)이거나 민감하지 않은 종류뿐이다.
 *
 * <p>🔴 2026-09-03 — {@code answerStatus}·{@code scope} 추가. 이게 없으면
 * "알레르기 없음"(NONE)과 "안 물어봄"(UNKNOWN)이 응답에서도 구분되지 않는다.
 *
 * <p>🔴 2026-09-04 — {@code constraintKey} 추가(고지혁 님 리뷰). 알레르기·식단은
 * 이 코드가 정보 전부다 — 없으면 "무슨 알레르기인지" 가 응답에서 사라진다.
 */
public record TripConstraintDto(
		String constraintId,
		String type,
		String constraintKey,
		String severity,
		String operator,
		String value,
		Double threshold,
		String evidenceStatus,
		String answerStatus,
		String scope) {

	public static TripConstraintDto of(TripConstraint c) {
		return new TripConstraintDto(
				c.constraintId(), c.type(), c.constraintKey(), c.severity().name(),
				c.operator(), c.value(), c.threshold(), c.evidenceStatus().name(),
				c.answerStatus().name(), c.scope().name());
	}
}
