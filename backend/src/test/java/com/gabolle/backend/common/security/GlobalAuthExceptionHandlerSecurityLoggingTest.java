package com.gabolle.backend.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.common.api.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 어느 상태·코드가 어느 이벤트로 가는지와 무엇을 건너뛰는지만 mock 으로 본다. 실제 로그 줄의 형식은
 * {@link SecurityEventLoggerTest} 가 검증한다.
 */
class GlobalAuthExceptionHandlerSecurityLoggingTest {

	private SecurityEventLogger securityEventLogger;
	private GlobalAuthExceptionHandler handler;
	private MockHttpServletRequest request;

	@BeforeEach
	void setUp() {
		this.securityEventLogger = mock(SecurityEventLogger.class);
		this.handler = new GlobalAuthExceptionHandler(this.securityEventLogger);
		this.request = new MockHttpServletRequest();
	}

	@Test
	@DisplayName("토큰 없음/무효(401, 로그인이 아닌 것)는 AUTH_TOKEN_REJECTED 로 남는다")
	void logsTokenRejectedForGenuine401() {
		this.handler.handleAuth(
				new AuthException("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED), this.request);

		verify(this.securityEventLogger).tokenRejected("AUTHENTICATION_REQUIRED");
	}

	@Test
	@DisplayName("🔴 완료 기준 — 403 이 나가면 AUTHZ_DENIED 가 남는다")
	void logsAuthzDeniedFor403() {
		this.handler.handleAuth(
				new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.FORBIDDEN), this.request);

		verify(this.securityEventLogger).authzDenied("ACCOUNT_UNAVAILABLE");
	}

	@Test
	@DisplayName("로그인 실패(INVALID_CREDENTIALS, 401)는 여기서 다시 로깅하지 않는다 — LocalAuthService 가 이미 남긴다")
	void doesNotDoubleLogInvalidCredentials() {
		this.handler.handleAuth(
				new AuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
				this.request);

		verifyNoInteractions(this.securityEventLogger);
	}

	@Test
	@DisplayName("계정 잠금(TOO_MANY_LOGIN_ATTEMPTS, 429)도 여기서 로깅하지 않는다 — 401/403 도 아니고 이미 남겨졌다")
	void doesNotLogLockoutResponse() {
		this.handler.handleAuth(
				new AuthException("TOO_MANY_LOGIN_ATTEMPTS", "로그인 시도가 너무 많습니다.", HttpStatus.TOO_MANY_REQUESTS),
				this.request);

		verifyNoInteractions(this.securityEventLogger);
	}

	@Test
	@DisplayName("로깅이 추가돼도 응답 상태·본문은 그대로다 — 관측만 더했지 동작을 바꾸지 않았다")
	void responseStatusAndBodyAreUnchanged() {
		ResponseEntity<ApiResponse<Void>> response = this.handler.handleAuth(
				new AuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
				this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().error().code()).isEqualTo("INVALID_CREDENTIALS");
	}
}
