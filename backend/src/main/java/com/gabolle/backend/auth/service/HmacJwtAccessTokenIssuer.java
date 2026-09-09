package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.user.domain.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class HmacJwtAccessTokenIssuer implements AccessTokenIssuer {

	private static final String HMAC_SHA256 = "HmacSHA256";
	private final AuthProperties properties;

	public HmacJwtAccessTokenIssuer(AuthProperties properties) {
		this.properties = properties;
	}

	@Override
	public IssuedAccessToken issue(AppUser user, Instant now) {
		return issue(user, now, null);
	}

	@Override
	public IssuedAccessToken issue(AppUser user, Instant now, UUID sessionId) {
		Instant expiresAt = now.plus(properties.getAccessTokenTtl());
		String header = encodeJson("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
		String sessionClaim = sessionId == null ? "" : ",\"sid\":\"" + sessionId + "\"";
		String payload = encodeJson("{\"sub\":\"" + user.getUserId() + "\",\"iat\":"
				+ now.getEpochSecond() + ",\"exp\":" + expiresAt.getEpochSecond() + ",\"jti\":\""
				+ UUID.randomUUID() + "\"" + sessionClaim + "}");
		String unsigned = header + "." + payload;
		return new IssuedAccessToken(unsigned + "." + sign(unsigned), expiresAt);
	}

	private String encodeJson(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("JWT signing is unavailable", exception);
		}
	}
}
