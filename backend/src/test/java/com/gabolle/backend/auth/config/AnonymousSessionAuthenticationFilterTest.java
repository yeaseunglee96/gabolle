package com.gabolle.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.domain.AnonymousSession;
import com.gabolle.backend.auth.service.AnonymousSessionService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** {@code X-Session-Token} 헤더로 익명 세션의 주인을 찾는 필터. */
class AnonymousSessionAuthenticationFilterTest {

	private final AnonymousSessionService anonymousSessionService = mock(AnonymousSessionService.class);
	private final AnonymousSessionAuthenticationFilter filter = new AnonymousSessionAuthenticationFilter(
			anonymousSessionService);

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void headerWithAKnownTokenAuthenticatesAsThatSession() throws Exception {
		AnonymousSession session = anonymousSession();
		when(anonymousSessionService.resolve("valid-token")).thenReturn(Optional.of(session));

		Authentication authentication = runFilter("valid-token");

		assertThat(authentication).isNotNull();
		assertThat(authentication.getName()).isEqualTo("anon:" + session.getSessionId());
		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ANONYMOUS");
	}

	@Test
	void unknownTokenDoesNotAuthenticate() throws Exception {
		when(anonymousSessionService.resolve("unknown-token")).thenReturn(Optional.empty());

		Authentication authentication = runFilter("unknown-token");

		assertThat(authentication).isNull();
	}

	@Test
	void missingHeaderDoesNotAuthenticateAndDoesNotQueryTheStore() throws Exception {
		Authentication authentication = runFilter(null);

		assertThat(authentication).isNull();
		verify(anonymousSessionService, never()).resolve(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void doesNotOverwriteAnAlreadyAuthenticatedRequest() throws Exception {
		// 실제 로그인 사용자의 요청에는 HmacJwtAuthenticationFilter 가 먼저 SecurityContext 를
		// 채운다. 이 필터가 그 뒤에서 익명 세션으로 덮어쓰면 안 된다.
		UsernamePasswordAuthenticationToken existing = new UsernamePasswordAuthenticationToken("real-user-id", null,
				java.util.List.of());
		SecurityContextHolder.getContext().setAuthentication(existing);
		AnonymousSession session = anonymousSession();
		when(anonymousSessionService.resolve("valid-token")).thenReturn(Optional.of(session));

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Session-Token", "valid-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
		verify(anonymousSessionService, never()).resolve(org.mockito.ArgumentMatchers.any());
	}

	private Authentication runFilter(String token) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (token != null) {
			request.addHeader("X-Session-Token", token);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		return SecurityContextHolder.getContext().getAuthentication();
	}

	private AnonymousSession anonymousSession() {
		return AnonymousSession.issue("hash-value", Instant.now());
	}
}
