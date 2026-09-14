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
 * Sign in with Apple — S15P21E201-825.
 *
 * <p>구글·카카오·네이버와 같은 흐름(챌린지 발급 → 코드 교환 → 계정 판정)을 그대로 타고, 두 자리만
 * 다르다. {@code client_secret} 은 설정값이 아니라 {@link AppleClientSecretFactory} 가 그때그때 서명해
 * 만든 JWT 이고, 사용자 정보는 조회 주소가 없어 코드 교환 응답의 {@code id_token} 에서만 나온다.
 *
 * <p>🔴 표시 이름은 언제나 비어 있다. 애플은 이름을 최초 인증 응답에만 한 번 실어 주고 그다음부터는
 * 주지 않는다. 그 한 번을 붙잡아 두려면 앱이 받은 값을 서버로 따로 올려야 하는데, 그러면 <b>앱이 보낸
 * 이름을 서버가 그대로 믿는</b> 자리가 생긴다. 여기서는 만들지 않는다 — 가입 화면에서 사용자가 직접
 * 적는 이름이 이미 있고, 그쪽이 출처가 분명하다.
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
	 * 그 주소가 지금도 살아 있는 주소인가 (S15P21E201-741 의 {@code email_valid}).
	 *
	 * <p>애플이 실제 주소를 준 경우에는 그것이 애플 계정 주소 자체라 유효하다고 본다. 가려서 준
	 * {@code ...@privaterelay.appleid.com} 주소는 <b>모름</b>으로 남긴다 — 지금은 전달되지만 사용자가
	 * 설정에서 전달을 끄면 그날부터 죽는 주소이고, 우리는 그 시점을 알 수 없다. {@code false} 로
	 * 적지 않는 이유는 지금 죽어 있다는 뜻이 되기 때문이다.
	 */
	private Boolean emailValid(AppleIdTokenVerifier.VerifiedIdentity identity) {
		if (identity.email() == null || Boolean.TRUE.equals(identity.privateEmail())) {
			return null;
		}
		return Boolean.TRUE.equals(identity.emailVerified()) ? Boolean.TRUE : null;
	}
}
