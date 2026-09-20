package com.gabolle.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.ConsentUpdateService;
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
 * 앱이 보내는 최소 본문이 실제로 읽히는지 본다.
 *
 * <p>record 로 요청 본문을 받으면 JSON 에 없는 키는 생성자에 {@code null} 로 들어가고, 그
 * 자리가 원시형 {@code boolean} 이면 Jackson 이 거기서 실패한다. 그러면 응답이
 * {@code OAUTH_TICKET_INVALID} 가 아니라 {@code INVALID_REQUEST} 로 나가 화면이 무엇이
 * 문제인지 알 수 없다.
 *
 * <p>2단계 흐름에서 앱은 인증 요청에 동의·14세 확인을 싣지 않으므로
 * {@code behaviorPersonalizationEnabled} 도 함께 빠진다. 그 칸을 다시 원시형으로 되돌리면
 * 이 검사가 빨개진다.
 */
class OAuthRequestBindingTest {

	private OAuthAccountService accountService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.accountService = mock(OAuthAccountService.class);
		AuthController controller = new AuthController(mock(LocalAuthService.class), mock(PasswordResetService.class),
				mock(AuthTokenService.class), mock(OAuthLoginService.class), mock(OAuthChallengeService.class),
				mock(WebAuthCookieService.class), mock(CurrentUserService.class), mock(ProfileUpdateService.class),
				mock(AccountDeletionService.class), this.accountService,
				mock(ConsentUpdateService.class), mock(LinkedIdentityService.class));
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		// 여기서 보는 것은 요청 바인딩이라 SecurityEventLogger 는 mock 으로 채운다.
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setValidator(validator)
				.setControllerAdvice(new AuthExceptionHandler(), new GlobalAuthExceptionHandler(mock(SecurityEventLogger.class)))
				.build();
	}

	@Test
	@DisplayName("🔴 가입 본문에 behaviorPersonalizationEnabled 가 없어도 읽힌다 — 티켓 오류가 그대로 나온다")
	void signupBodyWithoutOptionalBooleanStillBinds() throws Exception {
		when(this.accountService.completeSignup(anyString(), any(), any(), any(), anyBoolean(), any()))
				.thenThrow(new AuthException("OAUTH_TICKET_INVALID", "소셜 로그인 절차가 만료됐어요.",
						HttpStatus.BAD_REQUEST));

		this.mockMvc.perform(post("/api/v1/auth/oauth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"signupTicket":"bogus","ageGateAccepted":true,
						 "consents":{"TERMS_OF_SERVICE":true,"PRIVACY_POLICY":true}}
						"""))
				.andExpect(status().isBadRequest())
				// INVALID_REQUEST 가 아니라 이것이어야 본문이 읽혔다는 뜻이다.
				.andExpect(jsonPath("$.error.code").value("OAUTH_TICKET_INVALID"));
	}

	@Test
	@DisplayName("가입 본문에서 14세 확인이 빠지면 그건 검증 오류로 걸린다 — 어느 항목인지 응답에 남는다")
	void signupWithoutAgeGateIsAValidationError() throws Exception {
		this.mockMvc.perform(post("/api/v1/auth/oauth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"signupTicket\":\"bogus\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields").isNotEmpty());
	}

	@Test
	@DisplayName("🔴 인증 본문에 동의·14세·개인화가 전부 없어도 읽힌다 — 2단계 흐름이 그렇게 보낸다")
	void oauthLoginBodyWithoutConsentFieldsStillBinds() throws Exception {
		OAuthLoginRequest request = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
				{"authorizationCode":"code","redirectUri":"https://example.com/cb",
				 "codeVerifier":"0123456789012345678901234567890123456789012","state":"s","nonce":"n"}
				""", OAuthLoginRequest.class);

		assertThat(request.ageGateAccepted()).isNull();
		assertThat(request.behaviorPersonalizationEnabled()).isNull();
		// 서비스가 쓰는 것은 이 접근자다 — null 을 false 로 본다.
		assertThat(request.behaviorPersonalizationEnabledOrFalse()).isFalse();
	}

	@Test
	@DisplayName("연결 본문도 최소 형태로 읽힌다")
	void linkBodyBinds() throws Exception {
		when(this.accountService.linkWithPassword(anyString(), anyString(), any()))
				.thenThrow(new AuthException("OAUTH_TICKET_INVALID", "소셜 로그인 절차가 만료됐어요.",
						HttpStatus.BAD_REQUEST));

		this.mockMvc.perform(post("/api/v1/auth/oauth/link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"linkTicket\":\"bogus\",\"password\":\"whatever12\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("OAUTH_TICKET_INVALID"));
	}
}
