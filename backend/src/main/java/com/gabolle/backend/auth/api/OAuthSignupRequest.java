package com.gabolle.backend.auth.api;

import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
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
		@AssertTrue(message = "14세 이상 확인이 필요합니다.") boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		boolean behaviorPersonalizationEnabled) {
}
