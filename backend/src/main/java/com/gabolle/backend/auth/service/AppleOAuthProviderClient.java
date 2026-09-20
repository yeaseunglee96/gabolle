package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.domain.AuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Sign in with Apple. 다른 제공자와 흐름은 같고 두 자리가 다르다 — {@code client_secret} 은
 * 설정값이 아니라 {@link AppleClientSecretFactory} 가 그때그때 서명해 만든 JWT 이고, 사용자 정보는
 * 조회 주소가 없어 코드 교환 응답의 {@code id_token} 에서만 나온다.
 *
 * <p>표시 이름은 언제나 비어 있다. 애플은 이름을 최초 인증 응답에만 주는데, 그 값을 앱이 서버로
 * 올려 보관하면 앱이 보낸 이름을 서버가 그대로 믿는 자리가 생긴다.
 */
@Component
@Profile({"db", "dev"})
public class AppleOAuthProviderClient extends AbstractRestClientOAuthProvider {

	private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";

	private final String clientId;
	private final AppleClientSecretFactory clientSecretFactory;
	private final AppleIdTokenVerifier idTokenVerifier;

	@Autowired
	public AppleOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
			@Value("${gabolle.oauth.apple.client-id:}") String clientId,
			AppleClientSecretFactory clientSecretFactory, AppleIdTokenVerifier idTokenVerifier) {
		super(restClientBuilder, objectMapper);
		this.clientId = clientId;
		this.clientSecretFactory = clientSecretFactory;
		this.idTokenVerifier = idTokenVerifier;
	}

	AppleOAuthProviderClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, String clientId,
			AppleClientSecretFactory clientSecretFactory, AppleIdTokenVerifier idTokenVerifier,
			ClientHttpRequestFactory requestFactory) {
		super(restClientBuilder, objectMapper, requestFactory);
		this.clientId = clientId;
		this.clientSecretFactory = clientSecretFactory;
		this.idTokenVerifier = idTokenVerifier;
	}

	@Override
	public AuthProvider provider() {
		return AuthProvider.APPLE;
	}

	@Override
	public OAuthUserProfile exchangeAuthorizationCode(String authorizationCode, String redirectUri,
			String codeVerifier, String state, String nonce) {
		String clientSecret = clientSecretFactory.currentClientSecret();
		TokenResponse tokens = exchangeTokens(TOKEN_URI, clientId, clientSecret, authorizationCode, redirectUri,
				codeVerifier, true, true);
		AppleIdTokenVerifier.VerifiedIdentity identity = idTokenVerifier.verify(tokens.idToken(), nonce);
		// 표시 이름은 null 이고 언어는 애플이 알려주지 않아 앱 기본값(KO)으로 둔다.
		return new OAuthUserProfile(identity.subject(), identity.email(), null, LanguageNormalizer.normalize(null),
				identity.emailVerified(), emailValid(identity));
	}

	/**
	 * 그 주소가 지금도 살아 있는가 ({@code email_valid}).
	 *
	 * <p>가려서 준 {@code ...@privaterelay.appleid.com} 주소는 모름으로 남긴다 — 사용자가 전달을
	 * 끄면 그날부터 죽는 주소이고 그 시점을 알 수 없다. {@code false} 는 지금 죽어 있다는 뜻이라
	 * 쓰지 않는다.
	 */
	private Boolean emailValid(AppleIdTokenVerifier.VerifiedIdentity identity) {
		if (identity.email() == null || Boolean.TRUE.equals(identity.privateEmail())) {
			return null;
		}
		return Boolean.TRUE.equals(identity.emailVerified()) ? Boolean.TRUE : null;
	}
}
