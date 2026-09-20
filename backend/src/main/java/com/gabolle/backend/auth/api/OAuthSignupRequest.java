package com.gabolle.backend.auth.api;

import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/oauth/signup} — 소셜 인증 뒤 회원가입 완료.
 *
 * <p>{@link LocalSignupRequest} 에서 이메일·비밀번호를 빼고 가입 티켓을 넣은 모양이다. 닉네임·언어를
 * 비우면 티켓에 담긴 provider 값을 그대로 쓴다.
 */
public record OAuthSignupRequest(
		@NotBlank String signupTicket,
		@Size(max = 50) String displayName,
		@Pattern(regexp = "KO|EN") String language,
		/**
		 * 원시형이 아니라 {@code Boolean} + {@code @NotNull} 이어야 한다. 원시형이면 이 키를 빼고
		 * 보낸 요청이 검증 오류가 아니라 형식 오류로 끝나 어느 항목이 빠졌는지가 응답에 안 남는다.
		 * {@code @AssertTrue} 는 null 을 통과시키므로 둘 중 하나만 두면 14세 확인 없이 가입이 된다.
		 */
		@NotNull(message = "14세 이상 확인이 필요합니다.")
		@AssertTrue(message = "14세 이상 확인이 필요합니다.") Boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		/** 선택 항목은 반드시 감싼 타입이어야 한다 — 위 참고. 비우면 {@code false} 로 본다. */
		Boolean behaviorPersonalizationEnabled) {

	/** 비우면 개인화를 끄는 것으로 본다. */
	public boolean behaviorPersonalizationEnabledOrFalse() {
		return Boolean.TRUE.equals(behaviorPersonalizationEnabled);
	}
}
