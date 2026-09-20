package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record OAuthLoginRequest(
		@NotBlank String authorizationCode,
		@NotBlank String redirectUri,
		@NotBlank @Size(min = 43, max = 128) @Pattern(regexp = "[A-Za-z0-9._~-]+") String codeVerifier,
		@NotBlank String state,
		@NotBlank String nonce,
		/**
		 * 필수가 아니다. 14세 확인은 가입 단계({@code POST /auth/oauth/signup})가 받는다. 옛 앱이
		 * 여기 {@code true} 와 동의를 함께 보내면 한 번에 가입한다.
		 */
		Boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		/**
		 * {@code Boolean} 이어야 한다 — 2단계 흐름에서 이 키가 빠질 수 있고, 원시형이면 그때
		 * 본문 자체를 못 읽는다. 비우면 {@code false}.
		 */
		Boolean behaviorPersonalizationEnabled) {

	public boolean behaviorPersonalizationEnabledOrFalse() {
		return Boolean.TRUE.equals(behaviorPersonalizationEnabled);
	}
}
