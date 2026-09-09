package com.gabolle.backend.auth.api;

import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/oauth/signup} — 소셜 인증 뒤 회원가입 완료 (S15P21E201-689).
 *
 * <p>{@link LocalSignupRequest} 에서 이메일·비밀번호를 빼고 가입 티켓을 넣은 모양이다. 닉네임·언어를 비우면
 * 티켓에 담긴 provider 값(미리 채운 값)을 그대로 쓴다.
 */
public record OAuthSignupRequest(
		@NotBlank String signupTicket,
		@Size(max = 50) String displayName,
		@Pattern(regexp = "KO|EN") String language,
		/**
		 * 🔴 원시형이 아니라 {@code Boolean} + {@code @NotNull} 이다. 원시형으로 두면 이 키를 빼고 보낸 요청이
		 * <b>검증 오류가 아니라 형식 오류</b>로 끝나 응답에 "어느 항목이 빠졌는지" 가 남지 않는다(Jackson 이
		 * record 생성자에 null 을 넘기다 실패한다). {@code @AssertTrue} 는 null 을 통과시키므로 {@code @NotNull}
		 * 이 함께 있어야 빠뜨린 것을 막는다 — 둘 중 하나만 두면 14세 확인 없이 가입이 된다.
		 */
		@NotNull(message = "14세 이상 확인이 필요합니다.")
		@AssertTrue(message = "14세 이상 확인이 필요합니다.") Boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		/**
		 * 🔴 원시 {@code boolean} 이 아니라 {@code Boolean} 이다. record 로 본문을 받을 때 JSON 에서 이 키를
		 * <b>빼면</b> Jackson 이 생성자에 {@code null} 을 넘기고, 원시형이면 거기서 실패한다 —
		 * {@code JSON parse error: Cannot map null into type boolean}. 그러면 화면에는 티켓이 왜 거절됐는지가
		 * 아니라 "요청 형식이 올바르지 않습니다" 가 뜬다. 2026-09-07 에 배포에서 실제로 그랬다. 선택 항목은
		 * 반드시 감싼 타입으로 둔다. 비우면 {@code false} 로 본다.
		 */
		Boolean behaviorPersonalizationEnabled) {

	/** 비우면 개인화를 끄는 것으로 본다. */
	public boolean behaviorPersonalizationEnabledOrFalse() {
		return Boolean.TRUE.equals(behaviorPersonalizationEnabled);
	}
}
