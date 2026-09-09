package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class GoogleIdTokenVerifierTest {

	private static final String ISSUER = "https://accounts.google.com";
	private static final String CLIENT_ID = "google-client-id";
	private static final String NONCE = "nonce-from-challenge";

	@Test
	void springCreatesVerifierUsingConfiguredConstructor() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("dev");
			TestPropertyValues.of(
					"gabolle.oauth.google.jwk-set-uri=https://example.com/oauth2/certs",
					"gabolle.oauth.google.issuer=" + ISSUER,
					"gabolle.oauth.google.client-id=" + CLIENT_ID)
				.applyTo(context);
			context.register(GoogleIdTokenVerifier.class);

			context.refresh();

			assertThat(context.getBean(GoogleIdTokenVerifier.class)).isNotNull();
		}
	}

	@Test
	void acceptsVerifiedGoogleClaimsBoundToAudienceAndNonce() {
		JwtDecoder decoder = mock(JwtDecoder.class);
		Jwt claims = jwt(NONCE, List.of(CLIENT_ID));
		assertThat(claims.getClaims()).as("claims=%s", claims.getClaims()).containsEntry("iss", ISSUER);
		assertThat(claims.getClaimAsString("iss")).isEqualTo(ISSUER);
		assertThat(claims.getAudience()).contains(CLIENT_ID);
		assertThat(claims.getClaimAsString("nonce")).isEqualTo(NONCE);
		when(decoder.decode("signed-id-token")).thenReturn(claims);
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "");

		GoogleIdTokenVerifier.VerifiedIdentity identity = verifier.verify("signed-id-token", NONCE);

		assertThat(identity.subject()).isEqualTo("google-subject");
		assertThat(identity.email()).isEqualTo("traveler@example.com");
	}

	@Test
	void rejectsIdTokenWithDifferentNonce() {
		JwtDecoder decoder = mock(JwtDecoder.class);
		when(decoder.decode("signed-id-token")).thenReturn(jwt("different-nonce", List.of(CLIENT_ID)));
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "");

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("ID Token");
	}

	@Test
	void rejectsIdTokenForUnknownAudience() {
		JwtDecoder decoder = mock(JwtDecoder.class);
		when(decoder.decode("signed-id-token")).thenReturn(jwt(NONCE, List.of("another-client-id")));
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "");

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE))
				.isInstanceOf(AuthException.class);
	}

	@Test
	void rejectsUnverifiedEmail() {
		JwtDecoder decoder = mock(JwtDecoder.class);
		when(decoder.decode("signed-id-token")).thenReturn(jwt(NONCE, List.of(CLIENT_ID), false));
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(decoder, ISSUER, CLIENT_ID, "");

		assertThatThrownBy(() -> verifier.verify("signed-id-token", NONCE))
				.isInstanceOf(AuthException.class);
	}

	private Jwt jwt(String nonce, List<String> audience) {
		return jwt(nonce, audience, true);
	}

	private Jwt jwt(String nonce, List<String> audience, boolean emailVerified) {
		return Jwt.withTokenValue("signed-id-token")
				.header("alg", "RS256")
				.claim("iss", ISSUER)
				.claim("sub", "google-subject")
				.claim("aud", audience)
				.issuedAt(Instant.now().minusSeconds(30))
				.expiresAt(Instant.now().plusSeconds(300))
				.claim("nonce", nonce)
				.claim("email", "traveler@example.com")
				.claim("email_verified", emailVerified)
				.claim("name", "GABOLLE Tester")
				.build();
	}
}
