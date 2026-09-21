package com.gabolle.backend.trip.presentation.dto;

/**
 * {@code GET /api/v1/me/preferences/spend} 응답.
 *
 * <p>계정 기본값을 한 번도 저장한 적 없으면 404 가 아니라 {@link #unknown()} 으로 200 을 낸다 —
 * 오류가 아니라 정상 상태다.
 *
 * @param status {@code SELECTED} · {@code SKIPPED} · {@code UNKNOWN}
 * @param value {@code status == "SELECTED"} 일 때만 채워진다
 */
public record SpendProfileResponse(String status, String value) {

	public static SpendProfileResponse unknown() {
		return new SpendProfileResponse("UNKNOWN", null);
	}
}
