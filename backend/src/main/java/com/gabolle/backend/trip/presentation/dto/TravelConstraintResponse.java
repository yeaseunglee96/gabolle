package com.gabolle.backend.trip.presentation.dto;

/**
 * {@code GET·PUT /api/v1/me/preferences/constraints} 응답 — S15P21E201-1231.
 *
 * <h2>🔴 한 번도 저장 안 했으면 404 가 아니라 200 이다</h2>
 *
 * {@link #neverAsked()} 로 {@code {status: null, value: null}} 을 낸다.
 * {@code SpendProfileResponse} 주석이 같은 이유를 적고 있다 — <i>「한 번도 저장한 적 없다는
 * 오류가 아니라 정상 상태다. 404 로 답하면 화면이 그것을 오류로 다뤄야 한다」</i>.
 *
 * <p>🔴 씀씀이는 그 자리를 {@code "UNKNOWN"} 이라는 <b>글자</b>로 냈는데 여기서는
 * {@code null} 이다. 시안이 그렇게 정했고, 뜻도 더 곧다 — 「모름」이라는 답을 한 것이
 * 아니라 <b>답 자체가 없는 것</b>이다.
 *
 * @param status {@code SAVED} · {@code LATER} · {@code NEVER}, 또는 한 번도 안 물어봤으면 {@code null}
 * @param value {@code status == "SAVED"} 일 때만 채워진다. JSON 글자 한 덩어리다
 */
public record TravelConstraintResponse(String status, String value) {

	public static TravelConstraintResponse neverAsked() {
		return new TravelConstraintResponse(null, null);
	}
}
