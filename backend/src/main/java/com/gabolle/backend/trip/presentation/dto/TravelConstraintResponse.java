package com.gabolle.backend.trip.presentation.dto;

/**
 * {@code GET·PUT /api/v1/me/preferences/constraints} 응답.
 *
 * <p>한 번도 저장 안 했으면 404 가 아니라 {@link #neverAsked()} 로 200 을 낸다 — 오류가 아니라
 * 정상 상태다. 씀씀이는 같은 자리를 {@code "UNKNOWN"} 이라는 글자로 내지만 여기서는 {@code null}
 * 이다. 「모름」이라고 답한 것이 아니라 답 자체가 없는 것이다.
 *
 * @param status {@code SAVED} · {@code LATER} · {@code NEVER}, 또는 한 번도 안 물어봤으면 {@code null}
 * @param value {@code status == "SAVED"} 일 때만 채워진다. JSON 글자 한 덩어리다
 */
public record TravelConstraintResponse(String status, String value) {

	public static TravelConstraintResponse neverAsked() {
		return new TravelConstraintResponse(null, null);
	}
}
