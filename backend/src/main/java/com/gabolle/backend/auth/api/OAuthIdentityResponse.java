package com.gabolle.backend.auth.api;

import java.time.Instant;

import com.gabolle.backend.auth.domain.AuthIdentity;

/** 로그인한 계정에 붙은 소셜 신원 하나 — {@code POST /api/v1/auth/oauth/{provider}/link} 응답. */
public record OAuthIdentityResponse(String provider, String providerEmail, Instant linkedAt, boolean alreadyLinked) {

	public static OAuthIdentityResponse of(AuthIdentity identity, boolean alreadyLinked) {
		return new OAuthIdentityResponse(identity.getProvider().name(), identity.getProviderEmail(), identity.getLinkedAt(),
				alreadyLinked);
	}
}
