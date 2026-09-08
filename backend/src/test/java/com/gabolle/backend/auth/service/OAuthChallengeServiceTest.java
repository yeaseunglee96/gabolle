package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.OAuthChallenge;
import com.gabolle.backend.auth.repository.OAuthChallengeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthChallengeServiceTest {

	@Mock private OAuthChallengeRepository repository;

	private OAuthChallengeService service;
	private SessionTokenGenerator tokenGenerator;
	private AuthProperties properties;
	private Instant now;
	private static final String REDIRECT_URI = "https://j15e201.p.ssafy.io/oauth/google/callback";
	private static final String KAKAO_REDIRECT_URI = "https://j15e201.p.ssafy.io/oauth/kakao/callback";

	@BeforeEach
	void setUp() {
		now = Instant.parse("2026-01-01T00:00:00Z");
		tokenGenerator = new SessionTokenGenerator();
		properties = new AuthProperties();
		properties.getOauthAllowedRedirectUris().add(REDIRECT_URI);
		properties.getOauthAllowedRedirectUris().add(KAKAO_REDIRECT_URI);
		service = new OAuthChallengeService(repository, tokenGenerator, properties,
				Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void challengeCanBeConsumedOnlyWithBoundRequestValues() {
		when(repository.save(any(OAuthChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		String challenge = codeChallenge(verifier);
		OAuthChallengeService.IssuedChallenge issued = service.issue(AuthProvider.GOOGLE,
				REDIRECT_URI, challenge, "S256", "device-1");
		OAuthChallenge stored = OAuthChallenge.issue(AuthProvider.GOOGLE, tokenGenerator.hash(issued.state()),
				tokenGenerator.hash(issued.nonce()), tokenGenerator.hash(challenge), "S256", REDIRECT_URI,
				"device-1", issued.expiresAt());
		when(repository.findByProviderAndStateHash(AuthProvider.GOOGLE, tokenGenerator.hash(issued.state())))
				.thenReturn(Optional.of(stored));

		service.consume(AuthProvider.GOOGLE, issued.state(), issued.nonce(), verifier, REDIRECT_URI,
				"device-1");

		assertThatThrownBy(() -> service.consume(AuthProvider.GOOGLE, issued.state(), issued.nonce(), verifier,
				REDIRECT_URI, "device-1"))
				.isInstanceOf(AuthException.class).hasMessageContaining("유효하지 않거나 만료된");
	}

	@Test
	void rejectsMismatchedRedirectUri() {
		String state = "state";
		String nonce = "nonce";
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		String challenge = codeChallenge(verifier);
		OAuthChallenge stored = OAuthChallenge.issue(AuthProvider.KAKAO, tokenGenerator.hash(state),
				tokenGenerator.hash(nonce), tokenGenerator.hash(challenge), "S256", KAKAO_REDIRECT_URI, "device-1",
				now.plusSeconds(60));
		when(repository.findByProviderAndStateHash(AuthProvider.KAKAO, tokenGenerator.hash(state)))
				.thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.consume(AuthProvider.KAKAO, state, nonce, verifier,
				"https://attacker.example/callback", "device-1"))
				.isInstanceOf(AuthException.class);
	}

	@Test
	void rejectsRedirectUriOutsideConfiguredAllowList() {
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		assertThatThrownBy(() -> service.issue(AuthProvider.GOOGLE, "https://evil.example/callback",
				codeChallenge(verifier), "S256", "device-1"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("redirect URI");
	}

	@Test
	void rejectsPlainPkceMethod() {
		String verifier = "verifier-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef";
		assertThatThrownBy(() -> service.issue(AuthProvider.GOOGLE, REDIRECT_URI, codeChallenge(verifier), "plain",
				"device-1"))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("PKCE");
	}

	private String codeChallenge(String verifier) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
