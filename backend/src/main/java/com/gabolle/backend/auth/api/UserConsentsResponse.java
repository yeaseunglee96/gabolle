package com.gabolle.backend.auth.api;

import java.time.Instant;
import java.util.List;

/**
 * 내 동의 상태 — {@code GET · PATCH /api/v1/auth/me/consents} 응답. 조회와 변경이 같은 모양을
 * 돌려주므로 바꾼 쪽이 다시 조회하지 않아도 된다.
 */
public record UserConsentsResponse(

		/**
		 * 행동 기반 개인화가 지금 켜져 있는가. {@link #consents} 목록에서 계산하지 않는다 —
		 * 이 값은 동의 기록이 아니라 서버가 실제로 판정에 쓰는 값
		 * ({@code app_user.personalization_mode})이고, 둘이 어긋난 상태가 여기서 보여야 한다.
		 */
		boolean behaviorPersonalizationEnabled,

		List<Item> consents,

		/**
		 * 지금 서버가 쓰는 처리방침 판 (S15P21E201-1693). 항목의 {@code policyVersion} 이 이보다 옛것이면
		 * 앱이 「처리방침이 바뀌었어요」를 알릴 수 있다 — 알림은 강제가 아니고, 확인을 누르면
		 * {@code PATCH {PRIVACY_POLICY: true}} 로 이 판의 행이 새로 생긴다. 판은 동의 종류마다 따로가 아니라
		 * 모든 종류에 같이 붙는다. 동의했나의 판정은 판과 무관하게 종류별 마지막 결정을 본다.
		 */
		String currentPolicyVersion) {

	/**
	 * @param consentType {@code TERMS_OF_SERVICE} · {@code PRIVACY_POLICY} ·
	 *     {@code BEHAVIOR_PERSONALIZATION} · {@code PRECISE_LOCATION} · {@code HEALTH_CONSTRAINTS}
	 * @param status {@code GRANTED} · {@code REVOKED}
	 * @param policyVersion 어느 판의 약관에 대한 결정인가. 판이 바뀌면 다시 물어야 한다
	 * @param decidedAt 마지막으로 정한 시각. 켜고 끈 이력이 아니라 마지막 결정이다
	 */
	public record Item(String consentType, String status, String policyVersion, Instant decidedAt) {
	}
}
