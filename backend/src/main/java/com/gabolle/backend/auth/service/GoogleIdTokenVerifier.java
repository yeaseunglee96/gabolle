package com.gabolle.backend.auth.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"db", "dev"})
public class GoogleIdTokenVerifier {

	private static final String DEFAULT_ISSUER = "https://accounts.google.com";

	private final JwtDecoder decoder;
	private final String issuer;
	private final Set<String> allowedAudiences;

	@Autowired
	public GoogleIdTokenVerifier(
			@Value("${gabolle.oauth.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri,
			@Value("${gabolle.oauth.google.issuer:" + DEFAULT_ISSUER + "}") String issuer,
			@Value("${gabolle.oauth.google.client-id:}") String clientId,
			@Value("${gabolle.oauth.google.allowed-client-ids:}") String configuredAudiences) {
		this(NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build(), issuer, clientId, configuredAudiences);
	}

	GoogleIdTokenVerifier(JwtDecoder decoder, String issuer, String clientId, String configuredAudiences) {
		this.issuer = issuer;
		this.allowedAudiences = new HashSet<>();
		if (clientId != null && !clientId.isBlank()) {
			this.allowedAudiences.add(clientId.trim());
		}
		if (configuredAudiences != null && !configuredAudiences.isBlank()) {
			this.allowedAudiences.addAll(Arrays.stream(configuredAudiences.split(","))
					.map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet()));
		}

		OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);
		if (decoder instanceof NimbusJwtDecoder nimbusDecoder) {
			nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults));
		}
		this.decoder = decoder;
	}

	public VerifiedIdentity verify(String rawIdToken, String expectedNonce) {
		if (rawIdToken == null || rawIdToken.isBlank() || expectedNonce == null || expectedNonce.isBlank()) {
			throw invalidToken();
		}
		if (allowedAudiences.isEmpty()) {
			throw new AuthException("OAUTH_PROVIDER_NOT_CONFIGURED", "Google client ID 설정이 없습니다.",
					HttpStatus.NOT_IMPLEMENTED);
		}
		try {
			Jwt jwt = decoder.decode(rawIdToken);
			if (!issuer.equals(jwt.getClaimAsString("iss"))
					|| jwt.getAudience().stream().noneMatch(allowedAudiences::contains)
					|| !expectedNonce.equals(jwt.getClaimAsString("nonce"))
					|| !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
				throw invalidToken();
			}
			String subject = jwt.getClaimAsString("sub");
			if (subject == null || subject.isBlank()) {
				throw invalidToken();
			}
			String language = jwt.getClaimAsString("locale");
			return new VerifiedIdentity(subject, jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
					LanguageNormalizer.normalize(language));
		} catch (JwtException | IllegalArgumentException exception) {
			throw invalidToken();
		}
	}

	private AuthException invalidToken() {
		return new AuthException("OAUTH_ID_TOKEN_INVALID", "Google ID Token 검증에 실패했습니다.",
				HttpStatus.BAD_GATEWAY);
	}

	public record VerifiedIdentity(String subject, String email, String displayName, String language) {
	}
}
