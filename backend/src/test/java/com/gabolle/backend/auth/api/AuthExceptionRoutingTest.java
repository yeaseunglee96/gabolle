package com.gabolle.backend.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.ConsentUpdateService;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.CurrentUserService;
import com.gabolle.backend.auth.service.LinkedIdentityService;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.service.OAuthChallengeService;
import com.gabolle.backend.auth.service.OAuthLoginService;
import com.gabolle.backend.auth.service.PasswordResetService;
import com.gabolle.backend.auth.service.ProfileUpdateService;
import com.gabolle.backend.auth.service.WebAuthCookieService;
import com.gabolle.backend.common.security.GlobalAuthExceptionHandler;
import com.gabolle.backend.common.security.SecurityEventLogger;

/**
 * {@link AuthException} 이 {@code AuthController} 요청에서도 제 상태 코드로 나가는지 본다.
 *
 * <p>{@code AuthExceptionHandler} 는 {@code @ExceptionHandler(Exception.class)} 캐치올을 갖고
 * 있고 {@link AuthException} 핸들러는 {@link GlobalAuthExceptionHandler} 에 있다. Spring 은
 * advice 단위로 순서대로 훑어 처리할 메서드가 있는 첫 advice 에서 멈추므로 — advice 들
 * 사이에서 가장 구체적인 타입을 고르는 것이 아니다 — 순서가 어긋나면 {@link AuthException}
 * 이 캐치올에 먼저 잡혀 500 이 된다.
 *
 * <p>두 advice 를 함께 등록해 그 순서를 고정한다. MockMvc standaloneSetup 은
 * {@code setControllerAdvice} 에 준 순서가 아니라 {@code @Order} 를 따르므로,
 * {@code GlobalAuthExceptionHandler} 의 {@code @Order(HIGHEST_PRECEDENCE)} 를 지우면 빨개진다.
 */
class AuthExceptionRoutingTest {

	private LocalAuthService localAuthService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.localAuthService = mock(LocalAuthService.class);
		AuthController controller = new AuthController(this.localAuthService, mock(PasswordResetService.class),
				mock(AuthTokenService.class), mock(OAuthLoginService.class), mock(OAuthChallengeService.class),
				mock(WebAuthCookieService.class), mock(CurrentUserService.class), mock(ProfileUpdateService.class),
				mock(AccountDeletionService.class), mock(OAuthAccountService.class),
				mock(ConsentUpdateService.class), mock(LinkedIdentityService.class));
		// 캐치올을 가진 advice 를 일부러 먼저 준다. @Order 가 없으면 그것이 이긴다.
		// 여기서 보는 것은 라우팅 순서라 SecurityEventLogger 는 mock 으로 채운다.
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new AuthExceptionHandler(),
						new GlobalAuthExceptionHandler(mock(SecurityEventLogger.class)))
				.build();
	}

	@Test
	@DisplayName("🔴 로그인 실패의 AuthException 은 401 INVALID_CREDENTIALS 로 나간다 — 500 이 아니다")
	void authExceptionKeepsItsStatusOnLogin() throws Exception {
		when(this.localAuthService.login(any(AuthCommands.Login.class)))
				.thenThrow(new AuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.",
						HttpStatus.UNAUTHORIZED));

		this.mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\",\"password\":\"whatever12\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	@DisplayName("잠금(429)·이메일 미인증(403)도 각자의 상태 코드를 지킨다")
	void otherAuthExceptionStatusesSurvive() throws Exception {
		when(this.localAuthService.login(any(AuthCommands.Login.class)))
				.thenThrow(new AuthException("TOO_MANY_LOGIN_ATTEMPTS", "로그인 시도가 너무 많습니다.",
						HttpStatus.TOO_MANY_REQUESTS));
		this.mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"locked@example.com\",\"password\":\"whatever12\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

		when(this.localAuthService.login(any(AuthCommands.Login.class)))
				.thenThrow(new AuthException("EMAIL_NOT_VERIFIED", "이메일 인증 후 로그인할 수 있습니다.",
						HttpStatus.FORBIDDEN));
		this.mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"unverified@example.com\",\"password\":\"whatever12\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
	}

	@Test
	@DisplayName("AuthException 이 아닌 예기치 않은 예외는 그대로 500 INTERNAL_ERROR 다 — 캐치올을 없애지 않았다")
	void unexpectedExceptionsStillBecomeInternalError() throws Exception {
		when(this.localAuthService.login(any(AuthCommands.Login.class)))
				.thenThrow(new IllegalStateException("무언가 잘못됐다"));

		this.mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"boom@example.com\",\"password\":\"whatever12\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
	}
}
