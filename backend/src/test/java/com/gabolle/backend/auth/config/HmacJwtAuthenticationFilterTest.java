package com.gabolle.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.service.AccessTokenIssuer.IssuedAccessToken;
import com.gabolle.backend.auth.service.HmacJwtAccessTokenIssuer;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserRole;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

/**
 * role 이 요청마다 새로 읽혀 SecurityContext 의 authority 로 매핑되는지 본다.
 * JWT 자체에는 role 클레임이 없다.
 */
class HmacJwtAuthenticationFilterTest {

	private final AuthProperties properties = new AuthProperties();
	private final AppUserRepository userRepository = mock(AppUserRepository.class);
	private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
	private final HmacJwtAccessTokenIssuer issuer = new HmacJwtAccessTokenIssuer(properties);
	private HmacJwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		filter = new HmacJwtAuthenticationFilter(properties, new ObjectMapper(), sessionRepository, userRepository);
		SecurityContextHolder.clearContext();
	}

	@Test
	void grantsAdminAuthorityForAdminUser() throws Exception {
		Authentication authentication = authenticateAs(promote(activeUser(), UserRole.ADMIN));

		assertThat(authentication).isNotNull();
		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
	}

	@Test
	void grantsUserAuthorityForOrdinaryUser() throws Exception {
		Authentication authentication = authenticateAs(activeUser());

		assertThat(authentication).isNotNull();
		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
	}

	@Test
	void rejectsTokenWhenSessionIsRevoked() throws Exception {
		AppUser user = activeUser();
		UUID sessionId = UUID.randomUUID();
		when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
		AuthSession revoked = AuthSession.issue(user, UUID.randomUUID(), "hash", "device", Instant.now().plusSeconds(60));
		revoked.revoke(Instant.now());
		when(sessionRepository.findBySessionIdAndUserUserId(any(), any())).thenReturn(Optional.of(revoked));

		Authentication authentication = runFilter(issueToken(user, sessionId));

		assertThat(authentication).isNull();
	}

	private Authentication authenticateAs(AppUser user) throws Exception {
		UUID sessionId = UUID.randomUUID();
		when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
		AuthSession session = AuthSession.issue(user, UUID.randomUUID(), "hash", "device", Instant.now().plusSeconds(60));
		when(sessionRepository.findBySessionIdAndUserUserId(any(), any())).thenReturn(Optional.of(session));

		return runFilter(issueToken(user, sessionId));
	}

	private Authentication runFilter(String token) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		return SecurityContextHolder.getContext().getAuthentication();
	}

	private String issueToken(AppUser user, UUID sessionId) {
		IssuedAccessToken token = issuer.issue(user, Instant.now(), sessionId);
		return token.value();
	}

	private AppUser activeUser() throws Exception {
		AppUser user = AppUser.register("여행자", "ko", Instant.now(), "2026-01",
				PersonalizationMode.BEHAVIOR_ENABLED, UserStatus.PENDING_EMAIL_VERIFICATION);
		user.activate();
		// userId 는 @GeneratedValue라 실제 저장 없이는 null이다 — 서명된 토큰의 sub 클레임에
		// 실제 값이 실려야 하므로 여기서만 리플렉션으로 채운다.
		setField(user, "userId", UUID.randomUUID());
		return user;
	}

	/** role 은 가입으로 얻을 수 없는 값이라 검사에서만 리플렉션으로 올린다. */
	private AppUser promote(AppUser user, UserRole role) throws Exception {
		setField(user, "role", role);
		return user;
	}

	private void setField(AppUser user, String name, Object value) throws Exception {
		Field field = AppUser.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(user, value);
	}
}
