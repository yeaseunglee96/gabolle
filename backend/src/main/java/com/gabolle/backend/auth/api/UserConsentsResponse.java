package com.gabolle.backend.auth.api;

import java.time.Instant;
import java.util.List;

/**
 * 내 동의 상태 — {@code GET · PATCH /api/v1/auth/me/consents} 응답 (S15P21E201-735).
 *
 * <p>조회와 변경이 <b>같은 모양</b>을 돌려준다. 바꾼 쪽이 다시 조회하지 않아도 되고,
 * 두 응답이 갈라져서 한쪽만 낡는 일도 없다({@code PATCH /api/v1/auth/me} 가 이미 그렇게 한다).
 */
public record UserConsentsResponse(

		/**
		 * 행동 기반 개인화가 지금 켜져 있는가.
		 *
		 * <p>🔴 {@link #consents} 목록에서 골라 쓰면 되는데도 따로 내보내는 이유 —
		 * 이 값은 동의 기록이 아니라 <b>서버가 실제로 판정에 쓰는 값</b>
		 * ({@code app_user.personalization_mode})이다. 둘이 어긋나면 그 사실이 여기서 보인다.
		 * 목록에서 계산해 내보내면 어긋난 상태가 응답에서 사라진다.
		 */
		boolean behaviorPersonalizationEnabled,

		List<Item> consents) {

	/**
	 * @param consentType {@code TERMS_OF_SERVICE} · {@code PRIVACY_POLICY} ·
	 *     {@code BEHAVIOR_PERSONALIZATION} · {@code PRECISE_LOCATION} · {@code HEALTH_CONSTRAINTS}
	 * @param status {@code GRANTED} · {@code REVOKED}
	 * @param policyVersion 어느 판의 약관에 대한 결정인가. 판이 바뀌면 다시 물어야 한다
	 * @param decidedAt 마지막으로 정한 시각. 🔴 켜고 끈 이력이 아니라 <b>마지막 결정</b>이다
	 */
	public record Item(String consentType, String status, String policyVersion, Instant decidedAt) {
	}
}
