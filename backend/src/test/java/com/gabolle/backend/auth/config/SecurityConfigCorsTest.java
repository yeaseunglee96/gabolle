package com.gabolle.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * {@link SecurityConfig#corsConfigurationSource()} 의 순수 단위 시험 — DB 도 전체 Spring 컨텍스트도
 * 필요 없다. {@link com.gabolle.backend.functional.AppleFormPostJourneyFunctionalTest} 의 동급 시험은
 * Postgres 가 있어야 돌아서(로컬 개발 환경엔 흔히 없다), 그 시험이 스킵되는 환경에서도 이 회귀만은
 * 잡히게 여기 따로 둔다.
 *
 * <p>S15P21E201-1556 — 2026-09-23 App Store 심사에서 Apple 로그인이 Touch ID 이후 항상 403
 * (Invalid CORS request)이었다. 원인은 {@code /**} 에 걸린 고정 allowed-origins 목록에
 * {@code appleid.apple.com} 이 없었던 것 — Apple 이 폼을 그대로 제출하는 자리인데도 최신 Safari 가
 * 붙이는 {@code Origin} 헤더를 CORS 필터가 걸러 냈다.
 */
class SecurityConfigCorsTest {

	@SuppressWarnings("unchecked")
	private CorsConfigurationSource buildSource() {
		AuthProperties properties = new AuthProperties();
		SecurityConfig config = new SecurityConfig(
				mock(ObjectProvider.class), mock(ObjectProvider.class),
				mock(ObjectProvider.class), properties,
				mock(ApiAuthenticationEntryPoint.class));
		return config.corsConfigurationSource();
	}

	@Test
	void appleFormPostAllowsAppleOrigin() {
		CorsConfigurationSource source = buildSource();
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/oauth/apple/form-post");

		CorsConfiguration configuration = source.getCorsConfiguration(request);

		assertThat(configuration).as("이 경로에 CORS 설정 자체가 없다 — /** 로 떨어져 우리 프론트 주소만 허용된다").isNotNull();
		assertThat(configuration.checkOrigin("https://appleid.apple.com"))
				.as("애플의 실제 Origin 이 여전히 거부된다 — S15P21E201-1556 재발").isNotNull();
	}

	@Test
	void otherProvidersFormPostPathAlsoAllowsAnyOrigin() {
		// 지금은 apple 만 response_mode=form_post 를 쓰지만, 패턴이 "apple" 을 박지 않고
		// "/api/v1/auth/oauth/*/form-post" 로 넓혀 둔 이유 — 나중에 다른 제공자가 같은 방식을
		// 쓰게 되면 그때도 이 문제를 다시 겪지 않아야 한다.
		CorsConfigurationSource source = buildSource();
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/oauth/naver/form-post");

		CorsConfiguration configuration = source.getCorsConfiguration(request);

		assertThat(configuration.checkOrigin("https://nid.naver.com")).isNotNull();
	}

	@Test
	void ordinaryApiPathStillOnlyAllowsConfiguredOrigins() {
		// 이 fix 가 전체 CORS 를 느슨하게 만들지 않았는지 — 일반 API 경로는 여전히 우리 프론트
		// 주소만 허용하고, 임의의 출처(예: 공격자가 만든 페이지)는 여전히 거부돼야 한다.
		CorsConfigurationSource source = buildSource();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trips");

		CorsConfiguration configuration = source.getCorsConfiguration(request);

		assertThat(configuration.checkOrigin("https://appleid.apple.com"))
				.as("일반 API 경로까지 아무 출처나 허용하게 되면 이 fix 가 CORS 를 깬 것이다").isNull();
	}

	@Test
	void appleFormPostConfigurationDoesNotAllowCredentials() {
		// allowCredentials(true) + addAllowedOriginPattern("*") 조합은 CORS 스펙 위반이라
		// Spring 이 런타임에 예외를 던진다 — 이 경로는 자격 증명이 필요 없다는 걸 여기서도 못박는다.
		CorsConfigurationSource source = buildSource();
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/oauth/apple/form-post");

		CorsConfiguration configuration = source.getCorsConfiguration(request);

		assertThat(configuration.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
	}
}
