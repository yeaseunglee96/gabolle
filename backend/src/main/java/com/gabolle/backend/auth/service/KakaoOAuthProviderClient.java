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
public class KakaoOAuthProviderClient extends AbstractRestClientOAuthProvider {

	private final String clientId;
	private final String clientSecret;

	@Autowired
	public KakaoOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			@Value("${gabolle.oauth.kakao.client-id:}") String clientId,
			@Value("${gabolle.oauth.kakao.client-secret:}") String clientSecret) {
		super(restClientBuilder, objectMapper);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	KakaoOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, String clientId,
			String clientSecret, ClientHttpRequestFactory requestFactory) {
		super(restClientBuilder, objectMapper, requestFactory);
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
				authorizationCode, redirectUri, codeVerifier, true, state);
		JsonNode user = fetchUser("https://kapi.kakao.com/v2/user/me", accessToken);
		String subject = user.path("id").asText(null);
		if (subject == null || subject.isBlank()) {
			throw invalidResponse();
		}
		JsonNode account = user.path("kakao_account");
		JsonNode profile = account.path("profile");
		return new OAuthUserProfile(subject, account.path("email").asText(null), profile.path("nickname").asText(null),
				"KO", nullableBoolean(account, "is_email_verified"), nullableBoolean(account, "is_email_valid"));
	}

	// JsonNode.path(...).asBoolean() 은 필드가 없을 때 조용히 false 를 준다.
	// has() 로 먼저 확인하지 않으면 "안 줬다(모름)" 가 "false 라고 답했다" 로 둔갑한다.
	private Boolean nullableBoolean(JsonNode node, String fieldName) {
		return node.has(fieldName) ? node.path(fieldName).asBoolean() : null;
	}
}
