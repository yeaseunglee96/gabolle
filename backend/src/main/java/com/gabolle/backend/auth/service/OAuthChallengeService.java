package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.OAuthChallenge;
import com.gabolle.backend.auth.repository.OAuthChallengeRepository;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"db", "dev"})
public class OAuthChallengeService {

	private final OAuthChallengeRepository repository;
	private final SessionTokenGenerator tokenGenerator;
	private final AuthProperties properties;
	private final Clock clock;

	@Autowired
	public OAuthChallengeService(OAuthChallengeRepository repository, SessionTokenGenerator tokenGenerator,
			AuthProperties properties) {
		this(repository, tokenGenerator, properties, Clock.systemUTC());
	}

	OAuthChallengeService(OAuthChallengeRepository repository, SessionTokenGenerator tokenGenerator,
			AuthProperties properties, Clock clock) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public IssuedChallenge issue(AuthProvider provider, String redirectUri, String codeChallenge,
			String codeChallengeMethod, String deviceId) {
		if (!properties.isAllowedOauthRedirectUri(redirectUri)) {
			throw new AuthException("INVALID_OAUTH_REDIRECT_URI", "허용되지 않은 OAuth redirect URI입니다.",
					HttpStatus.BAD_REQUEST);
		}
		if (!"S256".equals(codeChallengeMethod) || !isValidCodeChallenge(codeChallenge)) {
			throw new AuthException("INVALID_OAUTH_PKCE", "PKCE code challenge가 올바르지 않습니다.",
					HttpStatus.BAD_REQUEST);
		}
		Instant now = clock.instant();
		String state = tokenGenerator.issue();
		String nonce = tokenGenerator.issue();
		Instant expiresAt = now.plus(properties.getOauthChallengeTtl());
		repository.save(OAuthChallenge.issue(provider, tokenGenerator.hash(state), tokenGenerator.hash(nonce),
				tokenGenerator.hash(codeChallenge), codeChallengeMethod, redirectUri, deviceId, expiresAt));
		return new IssuedChallenge(state, nonce, expiresAt);
	}

	@Transactional
	public void consume(AuthProvider provider, String state, String nonce, String codeVerifier, String redirectUri,
			String deviceId) {
		OAuthChallenge challenge = repository.findByProviderAndStateHash(provider, tokenGenerator.hash(state))
				.orElseThrow(this::invalidChallenge);
		Instant now = clock.instant();
		String codeChallenge = toCodeChallenge(codeVerifier);
		if (!challenge.isUsableAt(now)
				|| !challenge.matches(tokenGenerator.hash(nonce), tokenGenerator.hash(codeChallenge), "S256", redirectUri,
						deviceId)) {
			throw invalidChallenge();
		}
		challenge.consume(now);
	}

	private AuthException invalidChallenge() {
		return new AuthException("INVALID_OAUTH_CHALLENGE", "유효하지 않거나 만료된 OAuth 요청입니다.",
				HttpStatus.BAD_REQUEST);
	}

	private boolean isValidCodeChallenge(String codeChallenge) {
		return codeChallenge != null && codeChallenge.length() >= 43 && codeChallenge.length() <= 128
				&& codeChallenge.matches("[A-Za-z0-9._~-]+");
	}

	private String toCodeChallenge(String codeVerifier) {
		if (codeVerifier == null || codeVerifier.length() < 43 || codeVerifier.length() > 128
				|| !codeVerifier.matches("[A-Za-z0-9._~-]+")) {
			throw invalidChallenge();
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the runtime", exception);
		}
	}

	public record IssuedChallenge(String state, String nonce, Instant expiresAt) {
	}
}
