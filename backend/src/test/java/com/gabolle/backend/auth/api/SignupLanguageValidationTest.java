package com.gabolle.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.AuthCommands;
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
import com.gabolle.backend.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 가입 요청 두 개({@code POST /api/v1/auth/signup} · {@code POST /api/v1/auth/oauth/signup})가 앱이 지원하는
 * 여섯 언어를 받는지, 그 밖의 값은 400 으로 막는지 본다(S15P21E201-2053).
 *
 * <p>전에는 두 요청이 {@code "KO|EN"} 만 받아서, 앱 가입 화면이 일본어·중국어·베트남어를 고르게 해도 서버가 400 으로
 * 돌려보낼 상황이었다. 그래서 가입 화면이 한국어·영어 둘뿐이었다.
 *
 * <p>검증이 통과했는지는 서비스 mock 이 불렸는지로 가른다. 소셜 쪽은 서비스를 일부러 티켓 오류로 던지게 해 두어
 * ({@code OAUTH_TICKET_INVALID}) 검증 오류({@code INVALID_REQUEST})와 코드로 구별한다 —
 * {@code OAuthRequestBindingTest} 와 같은 방법이다.
 */
class SignupLanguageValidationTest {

	private LocalAuthService localAuthService;
	private OAuthAccountService oAuthAccountService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.localAuthService = mock(LocalAuthService.class);
		this.oAuthAccountService = mock(OAuthAccountService.class);
		AuthController controller = new AuthController(this.localAuthService, mock(PasswordResetService.class),
				mock(AuthTokenService.class), mock(OAuthLoginService.class), mock(OAuthChallengeService.class),
				mock(WebAuthCookieService.class), mock(CurrentUserService.class), mock(ProfileUpdateService.class),
				mock(AccountDeletionService.class), this.oAuthAccountService,
				mock(ConsentUpdateService.class), mock(LinkedIdentityService.class));
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setValidator(validator)
				.setControllerAdvice(new AuthExceptionHandler(),
						new GlobalAuthExceptionHandler(mock(SecurityEventLogger.class)))
				.build();
	}

	// ---------------------------------------------------------------- 이메일 가입

	@ParameterizedTest(name = "이메일 가입 — {0}")
	@ValueSource(strings = { "KO", "EN", "JA", "ZH_HANS", "ZH_HANT", "VI", "ZH-HANS", "ZH-HANT" })
	@DisplayName("이메일 가입은 여섯 언어를 받고, 받은 값을 서비스에 그대로 넘긴다")
	void localSignupAcceptsEverySupportedLanguage(String language) throws Exception {
		when(this.localAuthService.register(any(AuthCommands.Register.class)))
				.thenReturn(new LocalAuthService.Registration(UUID.randomUUID(), "traveler@example.com",
						UserStatus.PENDING_EMAIL_VERIFICATION));

		this.mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(APPLICATION_JSON)
				.content(localBody(language)))
				.andExpect(status().isCreated());

		ArgumentCaptor<AuthCommands.Register> command = ArgumentCaptor.forClass(AuthCommands.Register.class);
		verify(this.localAuthService).register(command.capture());
		// 정규화(ZH_HANS → ZH-HANS)는 서비스의 일이다 — 컨트롤러는 받은 값을 바꾸지 않는다.
		assertThat(command.getValue().language()).isEqualTo(language);
	}

	@ParameterizedTest(name = "이메일 가입 거부 — 「{0}」")
	@ValueSource(strings = { "FR", "ZH", "ZH_HK", "EN-US", "ko", "en", "zh_hans", "zh-Hans", "KO|EN", "", " " })
	@DisplayName("이메일 가입은 목록에 없는 언어를 400 으로 막고, 응답에 language 칸이 문제라고 남는다")
	void localSignupRejectsUnknownLanguage(String language) throws Exception {
		this.mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(APPLICATION_JSON)
				.content(localBody(language)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				// 앱이 「서버가 언어 칸을 못 받는다」를 다른 오류와 가르는 단서다(authApi 의 영어 대체 재시도).
				.andExpect(jsonPath("$.error.fields", hasItem(startsWith("language: "))));

		verifyNoInteractions(this.localAuthService);
	}

	@Test
	@DisplayName("이메일 가입은 언어 칸을 빼면 여전히 400 이다 — 필수 항목이다")
	void localSignupStillRequiresLanguage() throws Exception {
		this.mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(APPLICATION_JSON)
				.content(localBody(null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields", hasItem(startsWith("language: "))));

		verifyNoInteractions(this.localAuthService);
	}

	// ---------------------------------------------------------------- 소셜 가입 마무리

	@ParameterizedTest(name = "소셜 가입 — {0}")
	@ValueSource(strings = { "KO", "EN", "JA", "ZH_HANS", "ZH_HANT", "VI", "ZH-HANS", "ZH-HANT" })
	@DisplayName("소셜 가입 마무리도 여섯 언어를 받고, 검증을 지나 서비스까지 간다")
	void oauthSignupAcceptsEverySupportedLanguage(String language) throws Exception {
		when(this.oAuthAccountService.completeSignup(anyString(), any(), any(), any(), anyBoolean(), any()))
				.thenThrow(new AuthException("OAUTH_TICKET_INVALID", "소셜 로그인 절차가 만료됐어요.", HttpStatus.BAD_REQUEST));

		this.mockMvc.perform(post("/api/v1/auth/oauth/signup")
				.contentType(APPLICATION_JSON)
				.content(oauthBody(language)))
				.andExpect(status().isBadRequest())
				// INVALID_REQUEST 가 아니라 이것이어야 검증을 지나 서비스까지 갔다는 뜻이다.
				.andExpect(jsonPath("$.error.code").value("OAUTH_TICKET_INVALID"));

		verify(this.oAuthAccountService).completeSignup(eq("ticket-1"), any(), eq(language), any(), anyBoolean(), any());
	}

	@ParameterizedTest(name = "소셜 가입 거부 — 「{0}」")
	@ValueSource(strings = { "FR", "ZH", "ZH_HK", "EN-US", "ko", "en", "zh_hans", "zh-Hans", "KO|EN" })
	@DisplayName("소셜 가입 마무리는 목록에 없는 언어를 400 으로 막는다")
	void oauthSignupRejectsUnknownLanguage(String language) throws Exception {
		this.mockMvc.perform(post("/api/v1/auth/oauth/signup")
				.contentType(APPLICATION_JSON)
				.content(oauthBody(language)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields", hasItem(startsWith("language: "))));

		verifyNoInteractions(this.oAuthAccountService);
	}

	@Test
	@DisplayName("소셜 가입 마무리는 언어 칸을 비워도 된다 — 비우면 티켓에 담긴 provider 언어를 쓴다")
	void oauthSignupMayOmitLanguage() throws Exception {
		when(this.oAuthAccountService.completeSignup(anyString(), any(), any(), any(), anyBoolean(), any()))
				.thenThrow(new AuthException("OAUTH_TICKET_INVALID", "소셜 로그인 절차가 만료됐어요.", HttpStatus.BAD_REQUEST));

		this.mockMvc.perform(post("/api/v1/auth/oauth/signup")
				.contentType(APPLICATION_JSON)
				.content(oauthBody(null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("OAUTH_TICKET_INVALID"));

		verify(this.oAuthAccountService).completeSignup(eq("ticket-1"), any(), isNull(), any(), anyBoolean(), any());
	}

	// ---------------------------------------------------------------- 본문

	/** 언어 칸만 바꾼 이메일 가입 본문. {@code language} 가 {@code null} 이면 그 키를 뺀다. */
	private static String localBody(String language) {
		return """
				{
				  "email": "traveler@example.com",
				  "password": "correct-horse-battery-staple",
				  "displayName": "부산여행자",%s
				  "ageGateAccepted": true,
				  "deviceId": "test-device",
				  "consents": {},
				  "behaviorPersonalizationEnabled": false
				}
				""".formatted(languageKey(language));
	}

	/** 언어 칸만 바꾼 소셜 가입 마무리 본문. {@code language} 가 {@code null} 이면 그 키를 뺀다. */
	private static String oauthBody(String language) {
		return """
				{
				  "signupTicket": "ticket-1",
				  "displayName": "부산여행자",%s
				  "ageGateAccepted": true,
				  "consents": {"TERMS_OF_SERVICE": true, "PRIVACY_POLICY": true}
				}
				""".formatted(languageKey(language));
	}

	private static String languageKey(String language) {
		return language == null ? "" : "\n  \"language\": \"" + language + "\",";
	}
}
