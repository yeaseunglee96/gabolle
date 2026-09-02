package com.gabolle.backend.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabolle.backend.auth.domain.AuthProvider;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

abstract class AbstractRestClientOAuthProvider implements OAuthProviderClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	protected AbstractRestClientOAuthProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = restClientBuilder.requestFactory(requestFactory).build();
		this.objectMapper = objectMapper;
	}

	protected String exchangeAccessToken(String tokenUri, String clientId, String clientSecret, String code,
			String redirectUri, String codeVerifier, boolean includeCodeVerifier) {
		return exchangeTokens(tokenUri, clientId, clientSecret, code, redirectUri, codeVerifier, includeCodeVerifier, true)
				.accessToken();
	}

	protected TokenResponse exchangeTokens(String tokenUri, String clientId, String clientSecret, String code,
			String redirectUri, String codeVerifier, boolean includeCodeVerifier, boolean clientSecretRequired) {
		if (clientId == null || clientId.isBlank() || (clientSecretRequired && (clientSecret == null || clientSecret.isBlank()))) {
			throw new AuthException("OAUTH_PROVIDER_NOT_CONFIGURED", provider() + " client 설정이 없습니다.",
					org.springframework.http.HttpStatus.NOT_IMPLEMENTED);
		}
		LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", clientId);
		if (clientSecret != null && !clientSecret.isBlank()) {
			form.add("client_secret", clientSecret);
		}
		form.add("code", code);
		form.add("redirect_uri", redirectUri);
		if (includeCodeVerifier && codeVerifier != null && !codeVerifier.isBlank()) {
			form.add("code_verifier", codeVerifier);
		}
		try {
			String body = restClient.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
					.retrieve().body(String.class);
			JsonNode json = objectMapper.readTree(body);
			String accessToken = json.path("access_token").asText(null);
			String idToken = json.path("id_token").asText(null);
			if (accessToken == null || accessToken.isBlank()) {
				throw new AuthException("OAUTH_TOKEN_EXCHANGE_FAILED", "소셜 access token을 받지 못했습니다.",
						org.springframework.http.HttpStatus.BAD_GATEWAY);
			}
			return new TokenResponse(accessToken, idToken);
		} catch (RestClientException | java.io.IOException exception) {
			throw new AuthException("OAUTH_TOKEN_EXCHANGE_FAILED", "소셜 인증 코드 교환에 실패했습니다.",
					org.springframework.http.HttpStatus.BAD_GATEWAY);
		}
	}

	protected record TokenResponse(String accessToken, String idToken) {
	}

	protected JsonNode fetchUser(String userInfoUri, String accessToken) {
		try {
			String body = restClient.get().uri(userInfoUri).header("Authorization", "Bearer " + accessToken)
					.retrieve().body(String.class);
			return objectMapper.readTree(body);
		} catch (RestClientException | java.io.IOException exception) {
			throw new AuthException("OAUTH_USERINFO_FAILED", "소셜 사용자 정보를 받지 못했습니다.",
					org.springframework.http.HttpStatus.BAD_GATEWAY);
		}
	}

	protected AuthException invalidResponse() {
		return new AuthException("OAUTH_USERINFO_INVALID", "소셜 사용자 정보 형식이 올바르지 않습니다.",
				org.springframework.http.HttpStatus.BAD_GATEWAY);
	}
}
