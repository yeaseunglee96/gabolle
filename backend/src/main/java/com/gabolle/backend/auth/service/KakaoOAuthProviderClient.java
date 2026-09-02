package com.gabolle.backend.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabolle.backend.auth.domain.AuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile({"db", "dev"})
public class KakaoOAuthProviderClient extends AbstractRestClientOAuthProvider {

	private final String clientId;
	private final String clientSecret;

	public KakaoOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			@Value("${gabolle.oauth.kakao.client-id:}") String clientId,
			@Value("${gabolle.oauth.kakao.client-secret:}") String clientSecret) {
		super(restClientBuilder, objectMapper);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.KAKAO;
	}

	@Override
	public OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri,
			String codeVerifier, String state, String nonce) {
		String accessToken = exchangeAccessToken("https://kauth.kakao.com/oauth/token", clientId, clientSecret,
				authorizationCode, redirectUri, codeVerifier, true);
		JsonNode user = fetchUser("https://kapi.kakao.com/v2/user/me", accessToken);
		String subject = user.path("id").asText(null);
		if (subject == null || subject.isBlank()) {
			throw invalidResponse();
		}
		JsonNode account = user.path("kakao_account");
		JsonNode profile = account.path("profile");
		return new OAuthUserProfile(subject, account.path("email").asText(null), profile.path("nickname").asText(null), "KO");
	}
}
