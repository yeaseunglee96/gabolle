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

/**
 * 애플이 준 {@code id_token} 을 검증한다. 공개키로 서명을 확인하고 발급자·대상·nonce 를 대조한다.
 * {@link GoogleIdTokenVerifier} 와 골격은 같고 다음 넷이 다르다.
 *
 * <p>애플에는 access token 으로 프로필을 받아오는 주소가 없어 이 토큰의 검증이 곧 로그인 판정이다.
 *
 * <p>이름은 최초 인증 응답에만 실려 오고 {@code id_token} 에는 없다 — 표시 이름은 없는 것으로 두고
 * 가입 화면에서 받는다.
 *
 * <p>{@code email_verified}·{@code is_private_email} 이 참/거짓으로 올 때도 문자열로 올 때도 있다.
 * 한쪽만 읽으면 "애플이 확인해 준 주소" 가 조용히 "모름" 으로 떨어진다.
 *
 * <p>이메일이 없어도 통과시킨다. 사용자가 제공을 건너뛸 수 있고, 가려서 주면
 * {@code ...@privaterelay.appleid.com} 주소가 온다.
 */
@Component
@Profile({"db", "dev"})
public class AppleIdTokenVerifier {

	private static final String DEFAULT_ISSUER = "https://appleid.apple.com";

	private final JwtDecoder decoder;
	private final String issuer;
	private final Set<String> allowedAudiences;

	@Autowired
	public AppleIdTokenVerifier(
			@Value("${gabolle.oauth.apple.jwk-set-uri:https://appleid.apple.com/auth/keys}") String jwkSetUri,
			@Value("${gabolle.oauth.apple.issuer:" + DEFAULT_ISSUER + "}") String issuer,
			@Value("${gabolle.oauth.apple.client-id:}") String clientId,
			@Value("${gabolle.oauth.apple.allowed-client-ids:}") String configuredAudiences) {
		this(NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build(), issuer, clientId, configuredAudiences);
	}

	AppleIdTokenVerifier(JwtDecoder decoder, String issuer, String clientId, String configuredAudiences) {
		this.issuer = issuer;
		this.allowedAudiences = new HashSet<>();
		if (clientId != null && !clientId.isBlank()) {
			this.allowedAudiences.add(clientId.trim());
		}
		// 웹과 앱의 대상(aud)이 다르다 — 웹은 Service ID, iOS 네이티브는 앱 번들 id 가 찍힌다.
		// 번들 id 를 안 더하면 "웹은 되는데 앱만 안 되는" 상태가 된다.
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
			throw new AuthException("OAUTH_PROVIDER_NOT_CONFIGURED", "Apple client ID 설정이 없습니다.",
					HttpStatus.NOT_IMPLEMENTED);
		}
		try {
			Jwt jwt = decoder.decode(rawIdToken);
			if (!issuer.equals(jwt.getClaimAsString("iss"))
					|| jwt.getAudience().stream().noneMatch(allowedAudiences::contains)
					|| !expectedNonce.equals(jwt.getClaimAsString("nonce"))) {
				throw invalidToken();
			}
			String subject = jwt.getClaimAsString("sub");
			if (subject == null || subject.isBlank()) {
				throw invalidToken();
			}
			String email = jwt.getClaimAsString("email");
			return new VerifiedIdentity(subject, blankToNull(email), flag(jwt, "email_verified"),
					flag(jwt, "is_private_email"));
		} catch (JwtException | IllegalArgumentException exception) {
			throw invalidToken();
		}
	}

	/**
	 * 참/거짓 클레임을 읽는다 — 없으면 {@code null}(모름)이다. {@code false} 로 채우면 안 된다.
	 * "아니라고 답했다" 와 "안 알려줬다" 는 다른 사실이고, 이 구분이 {@code auth_identity} 에
	 * 그대로 저장된다.
	 */
	private Boolean flag(Jwt jwt, String claimName) {
		Object raw = jwt.getClaim(claimName);
		if (raw instanceof Boolean value) {
			return value;
		}
		if (raw instanceof String text && !text.isBlank()) {
			return Boolean.valueOf(text.trim());
		}
		return null;
	}

	private String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private AuthException invalidToken() {
		return new AuthException("OAUTH_ID_TOKEN_INVALID", "Apple ID Token 검증에 실패했습니다.",
				HttpStatus.BAD_GATEWAY);
	}

	/**
	 * @param privateEmail 애플이 주소를 가려서 준 경우 참({@code ...@privaterelay.appleid.com}).
	 *                     그 주소로도 메일은 가지만, 사용자가 나중에 전달을 끌 수 있다.
	 */
	public record VerifiedIdentity(String subject, String email, Boolean emailVerified, Boolean privateEmail) {
	}
}
