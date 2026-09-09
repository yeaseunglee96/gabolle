package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Profile({"db", "dev"})
public class WebAuthCookieService {

	private static final String COOKIE_PATH = "/api/v1/auth/web";

	private final AuthProperties properties;

	public WebAuthCookieService(AuthProperties properties) {
		this.properties = properties;
	}

	public ResponseCookie issue(String refreshToken) {
		return ResponseCookie.from(properties.getWebRefreshCookieName(), refreshToken)
				.httpOnly(true)
				.secure(properties.isWebRefreshCookieSecure())
				.sameSite(properties.getWebRefreshCookieSameSite())
				.path(COOKIE_PATH)
				.maxAge(properties.getRefreshTokenTtl())
				.build();
	}

	public ResponseCookie clear() {
		return ResponseCookie.from(properties.getWebRefreshCookieName(), "")
				.httpOnly(true)
				.secure(properties.isWebRefreshCookieSecure())
				.sameSite(properties.getWebRefreshCookieSameSite())
				.path(COOKIE_PATH)
				.maxAge(Duration.ZERO)
				.build();
	}

	public String cookieName() {
		return properties.getWebRefreshCookieName();
	}
}
