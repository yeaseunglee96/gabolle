package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.user.domain.AppUser;
import java.time.Instant;
import java.util.UUID;

public record AuthTokenResponse(String accessToken, String refreshToken, long expiresIn, UUID sessionId,
		AuthUserResponse user) {

	public static AuthTokenResponse from(AuthTokenService.IssuedTokens tokens, AppUser user, String email) {
		long expiresIn = Math.max(0, tokens.accessTokenExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
		return new AuthTokenResponse(tokens.accessToken(), tokens.refreshToken(), expiresIn, tokens.sessionId(),
				AuthUserResponse.from(user, email));
	}

	public static AuthTokenResponse from(AuthTokenService.IssuedTokens tokens) {
		return from(tokens, tokens.user(), tokens.email());
	}
}
