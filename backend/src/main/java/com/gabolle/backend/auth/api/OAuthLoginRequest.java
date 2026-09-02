package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.AssertTrue;
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
		@AssertTrue(message = "14세 이상 확인이 필요합니다.") boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		boolean behaviorPersonalizationEnabled) {
}
