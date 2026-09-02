package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthProvider;

public interface OAuthProviderClient {

	AuthProvider provider();

	OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri, String codeVerifier,
			String state, String nonce);

	record OAuthUserProfile(String subject, String email, String displayName, String language) {
	}
}
