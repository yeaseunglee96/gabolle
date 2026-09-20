package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile({"db", "dev"})
public class GoogleOAuthProviderClient extends AbstractRestClientOAuthProvider {

	private final String clientId;
	private final String clientSecret;
	private final GoogleIdTokenVerifier idTokenVerifier;

	public GoogleOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			@Value("${gabolle.oauth.google.client-id:}") String clientId,
			@Value("${gabolle.oauth.google.client-secret:}") String clientSecret,
			GoogleIdTokenVerifier idTokenVerifier) {
		super(restClientBuilder, objectMapper);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.idTokenVerifier = idTokenVerifier;
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.GOOGLE;
	}

	@Override
	public OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri,
			String codeVerifier, String state, String nonce) {
		TokenResponse tokens = exchangeTokens("https://oauth2.googleapis.com/token", clientId, clientSecret,
				authorizationCode, redirectUri, codeVerifier, true, false);
		GoogleIdTokenVerifier.VerifiedIdentity identity = idTokenVerifier.verify(tokens.idToken(), nonce);
		// verify() 가 email_verified 클레임이 true 가 아니면 예외를 던지므로 여기 도달한
		// 이메일은 항상 검증된 것이다. "유효한 주소인가" 는 구글 ID 토큰에 신호가 없어 null 이다.
		return new OAuthUserProfile(identity.subject(), identity.email(), identity.displayName(), identity.language(),
				Boolean.TRUE, null);
	}
}
