package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.config.AuthProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebAuthCookieServiceTest {

	private WebAuthCookieService service;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties();
		properties.setRefreshTokenTtl(Duration.ofDays(14));
		properties.setWebRefreshCookieSecure(true);
		properties.setWebRefreshCookieSameSite("Strict");
		service = new WebAuthCookieService(properties);
	}

	@Test
	void issuesHttpOnlyScopedSecureRefreshCookie() {
		var cookie = service.issue("refresh-value");

		assertThat(cookie.getName()).isEqualTo("gabolle_refresh_token");
		assertThat(cookie.getValue()).isEqualTo("refresh-value");
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.isSecure()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Strict");
		assertThat(cookie.getPath()).isEqualTo("/api/v1/auth/web");
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
	}

	@Test
	void clearCookieExpiresImmediately() {
		assertThat(service.clear().getMaxAge()).isEqualTo(Duration.ZERO);
	}
}
