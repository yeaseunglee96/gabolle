package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 애플 {@code id_token} 검증 — S15P21E201-825.
 *
 * <p>애플에는 사용자 정보 조회 주소가 없어 이 토큰이 신원의 유일한 출처다. 그래서 통과 조건과 거절
 * 조건을 여기서 못 박는다.
 */
class AppleIdTokenVerifierTest {

	private static final String ISSUER = "https://appleid.apple.com";
	private static final String CLIENT_ID = "io.ssafy.gabolle.web";
	private static final String NONCE = "nonce-from-challenge";

	@Test
	void springCreatesVerifierUsingConfiguredConstructor() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("dev");
			TestPropertyValues.of(
					"gabolle.oauth.apple.jwk-set-uri=https://example.com/auth/keys",
					"gabolle.oauth.apple.issuer=" + ISSUER,
					"gabolle.oauth.apple.client-id=" + CLIENT_ID)
				.applyTo(context);
			context.register(AppleIdTokenVerifier.class);

			context.refresh();

			assertThat(context.getBean(AppleIdTokenVerifier.class)).isNotNull();
		}
	}

	@Test
	void acceptsTokenBoundToAudienceAndNonce() {
		AppleIdTokenVerifier verifier = verifier(jwt(Map.of("aud", List.of(CLIENT_ID), "nonce", NONCE,
				"email", "traveler@example.com", "email_verified", true, "is_private_email", false)));

		AppleIdTokenVerifier.VerifiedIdentity identity = verifier.verify("signed-id-token", NONCE);

		assertThat(identity.subject()).isEqualTo("apple-subject");
		assertThat(identity.email()).isEqualTo("traveler@example.com");
		assertThat(identity.emailVerified()).isTrue();
		assertThat(identity.privateEmail()).isFalse();
	}

	@Test
	void readsFlagsThatArriveAsStrings() {
		// 🔴 애플은 같은 클레임을 참/거짓으로도, 문자열로도 보낸다. 한쪽만 읽으면
		//    "애플이 확인해 준 주소" 가 조용히 "모름" 이 된다.
		AppleIdTokenVerifier verifier = verifier(jwt(Map.of("aud", List.of(CLIENT_ID), "nonce", NONCE,
				"email", "hidden@privaterelay.appleid.com", "email_verified", "true", "is_private_email", "true")));

		AppleIdTokenVerifier.VerifiedIdentity identity = verifier.verify("signed-id-token", NONCE);

		assertThat(identity.emailVerified()).isTrue();
		assertThat(identity.privateEmail()).isTrue();
	}

	@Test
	void leavesMissingFlagsUnknownInsteadOfFalse() {
		AppleIdTokenVerifier verifier = verifier(
				jwt(Map.of("aud", List.of(CLIENT_ID), "nonce", NONCE, "email", "traveler@example.com")));

		AppleIdTokenVerifier.VerifiedIdentity identity = verifier.verify("signed-id-token", NONCE);

		assertThat(identity.emailVerified()).isNull();
		assertThat(identity.privateEmail()).isNull();
	}

	@Test
	void acceptsTokenWithoutEmail() {
		// 애플은 이메일 없이도 로그인시킬 수 있다. 카카오와 같은 자리다 — 계정은 이메일 없이 만들어진다.
		AppleIdTokenVerifier verifier = verifier(jwt(Map.of("aud", List.of(CLIENT_ID), "nonce", NONCE)));

		assertThat(verifier.verify("signed-id-token", NONCE).email()).isNull();
	}

	@Test
	void rejectsTokenWithDifferentNonce() {
		AppleIdTokenVerifier verifier = verifier(
				jwt(Map.of("aud", List.of(CLIENT_ID), "nonce", "another-nonce")));

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("Apple ID Token");
	}

	@Test
	void rejectsTokenForUnknownAudience() {
		AppleIdTokenVerifier verifier = verifier(
				jwt(Map.of("aud", List.of("io.ssafy.someone-else"), "nonce", NONCE)));

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE)).isInstanceOf(AuthException.class);
	}

	@Test
	void acceptsAudienceFromAdditionalAllowList() {
		// 앱의 네이티브 로그인은 번들 id 로 발급된다 — 목록에 없으면 웹만 되고 앱은 안 된다.
		JwtDecoder decoder = mock(JwtDecoder.class);
		when(decoder.decode("signed-id-token"))
				.thenReturn(jwt(Map.of("aud", List.of("io.ssafy.gabolle.app"), "nonce", NONCE)));
		AppleIdTokenVerifier verifier = new AppleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "io.ssafy.gabolle.app");

		assertThat(verifier.verify("signed-id-token", NONCE).subject()).isEqualTo("apple-subject");
	}

	@Test
	void rejectsTokenFromAnotherIssuer() {
		JwtDecoder decoder = mock(JwtDecoder.class);
		Jwt claims = Jwt.withTokenValue("signed-id-token").header("alg", "RS256")
				.claim("iss", "https://appleid.example.com").claim("sub", "apple-subject")
				.claim("aud", List.of(CLIENT_ID)).claim("nonce", NONCE)
				.issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(300)).build();
		when(decoder.decode("signed-id-token")).thenReturn(claims);
		AppleIdTokenVerifier verifier = new AppleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "");

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE)).isInstanceOf(AuthException.class);
	}

	@Test
	void answersNotImplementedWhenClientIdIsMissing() {
		AppleIdTokenVerifier verifier = new AppleIdTokenVerifier(mock(JwtDecoder.class), ISSUER, "", "");

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("OAUTH_PROVIDER_NOT_CONFIGURED");
	}

	private AppleIdTokenVerifier verifier(Jwt claims) {
		JwtDecoder decoder = mock(JwtDecoder.class);
		when(decoder.decode("signed-id-token")).thenReturn(claims);
		return new AppleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "");
	}

	private Jwt jwt(Map<String, Object> claims) {
		Jwt.Builder builder = Jwt.withTokenValue("signed-id-token")
				.header("alg", "RS256")
				.claim("iss", ISSUER)
				.claim("sub", "apple-subject")
				.issuedAt(Instant.now().minusSeconds(30))
				.expiresAt(Instant.now().plusSeconds(300));
		claims.forEach(builder::claim);
		return builder.build();
	}
}
