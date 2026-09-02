package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OAuthChallengeRequest(
		@NotBlank @Size(max = 500) String redirectUri,
		@NotBlank @Size(min = 43, max = 128) @Pattern(regexp = "[A-Za-z0-9._~-]+") String codeChallenge,
		@NotBlank @Pattern(regexp = "S256") String codeChallengeMethod,
		@Size(max = 255) String deviceId) {
}
