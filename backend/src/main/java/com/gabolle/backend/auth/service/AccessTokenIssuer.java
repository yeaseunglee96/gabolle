package com.gabolle.backend.auth.service;

import com.gabolle.backend.user.domain.AppUser;
import java.time.Instant;
import java.util.UUID;

public interface AccessTokenIssuer {

	IssuedAccessToken issue(AppUser user, Instant now);

	default IssuedAccessToken issue(AppUser user, Instant now, UUID sessionId) {
		return issue(user, now);
	}

	record IssuedAccessToken(String value, Instant expiresAt) {
	}
}
