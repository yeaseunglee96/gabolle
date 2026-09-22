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

		List<Item> consents) {

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
