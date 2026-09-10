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
 * 애플이 준 {@code id_token} 을 검증한다 — S15P21E201-825.
 *
 * <p>구글의 같은 자리({@link GoogleIdTokenVerifier})와 골격은 같다. 애플 공개키로 서명을 확인하고
 * 발급자·대상·nonce 를 대조한다. 다른 점 셋이 이 클래스의 존재 이유다.
 *
 * <p>1. <b>여기 말고 신원을 얻을 곳이 없다.</b> 카카오·네이버에는 access token 으로 프로필을 받아오는
 * 주소가 있지만 애플에는 없다. 그래서 이 토큰의 검증이 곧 로그인 판정이다.
 *
 * <p>2. <b>이름을 안 준다.</b> 애플은 최초 인증 응답에만 이름을 실어 주고 {@code id_token} 에는 넣지
 * 않는다. 두 번째 로그인부터는 아예 오지 않으므로 표시 이름은 없는 것으로 두고 가입 화면에서 받는다.
 *
 * <p>3. <b>참/거짓이 문자열로 온다.</b> {@code email_verified} 와 {@code is_private_email} 이
 * {@code true} 일 때도 있고 {@code "true"} 일 때도 있다. 한쪽만 읽으면 "애플이 확인해 준 주소" 가
 * 조용히 "모름" 으로 떨어진다.
 *
 * <p>🔴 구글과 달리 <b>이메일이 없어도 통과시킨다.</b> 애플은 사용자가 이메일 제공을 건너뛸 수 있게
 * 하고, 가려서 주면 {@code ...@privaterelay.appleid.com} 주소가 온다. 이메일 없이도 계정을 만들 수
 * 있는 것은 카카오에서 이미 겪은 자리라({@code auth_identity.provider_email} 이 NULL 허용) 새 규칙이
 * 아니다.
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
		// 🔴 웹과 앱의 대상(aud)이 서로 다르다. 웹 로그인은 Service ID 가, iOS 네이티브 로그인은
		//    앱 번들 id 가 대상으로 찍힌다. 앱을 붙이는 시점에 여기에 번들 id 를 더해야 한다 —
		//    안 더하면 "웹은 되는데 앱만 안 되는" 상태가 된다.
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
	 * 참/거짓 클레임을 읽는다 — 없으면 {@code null}(모름)이다.
	 *
	 * <p>🔴 {@code false} 로 채우지 않는다. "애플이 아니라고 답했다" 와 "애플이 안 알려줬다" 는 다른
	 * 사실이고, 이 구분은 {@code auth_identity} 에 그대로 저장된다(S15P21E201-741).
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
