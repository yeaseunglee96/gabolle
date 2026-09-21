package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile({"db", "dev"})
public class NaverOAuthProviderClient extends AbstractRestClientOAuthProvider {

	private final String clientId;
	private final String clientSecret;

	@Autowired
	public NaverOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			@Value("${gabolle.oauth.naver.client-id:}") String clientId,
			@Value("${gabolle.oauth.naver.client-secret:}") String clientSecret) {
		super(restClientBuilder, objectMapper);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	NaverOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, String clientId,
			String clientSecret, ClientHttpRequestFactory requestFactory) {
		super(restClientBuilder, objectMapper, requestFactory);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.NAVER;
	}

	@Override
	public OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri,
			String codeVerifier, String state, String nonce) {
		String accessToken = exchangeAccessToken("https://nid.naver.com/oauth2.0/token", clientId, clientSecret,
				authorizationCode, redirectUri, null, false, state);
		JsonNode response = fetchUser("https://openapi.naver.com/v1/nid/me", accessToken).path("response");
		String subject = response.path("id").asText(null);
		if (subject == null || subject.isBlank()) {
			throw invalidResponse();
		}
		// 네이버 응답에는 이메일 검증 여부 필드가 없다. false 로 채우면 "확인했는데 아니다" 가
		// 되므로 null(모름)로 남긴다.
		return new OAuthUserProfile(subject, response.path("email").asText(null), response.path("name").asText(null),
				LanguageNormalizer.normalize(response.path("locale").asText("KO")), null, null);
	}
}
