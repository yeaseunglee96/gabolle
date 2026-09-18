package com.gabolle.backend.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.ConsentUpdateService;
import com.gabolle.backend.auth.service.CurrentUserService;
import com.gabolle.backend.auth.service.ProfileUpdateService;
import com.gabolle.backend.auth.service.LinkedIdentityService;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.service.OAuthChallengeService;
import com.gabolle.backend.auth.service.OAuthLoginService;
import com.gabolle.backend.auth.service.PasswordResetService;
import com.gabolle.backend.auth.service.WebAuthCookieService;
import com.gabolle.backend.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 외부 API 경로 계약을 검증한다.
 *
 * <p>Nginx는 외부 {@code /api} prefix를 보존하는 방식으로 운영한다. 이 테스트는
 * 컨트롤러를 직접 호출하는 단위 테스트와 달리 실제 Spring MVC 매핑을 통과하므로,
 * {@code /api/v1/auth/signup} 경로가 실수로 {@code /v1/...}로 바뀌는 회귀를 잡는다.
 */
class AuthControllerHttpContractTest {

	private LocalAuthService localAuthService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		localAuthService = mock(LocalAuthService.class);
		AuthController controller = new AuthController(localAuthService, mock(PasswordResetService.class),
				mock(AuthTokenService.class), mock(OAuthLoginService.class), mock(OAuthChallengeService.class),
				mock(WebAuthCookieService.class), mock(CurrentUserService.class),
				mock(ProfileUpdateService.class),
				mock(AccountDeletionService.class), mock(OAuthAccountService.class),
				mock(ConsentUpdateService.class), mock(LinkedIdentityService.class));
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new AuthExceptionHandler())
				.build();
	}

	@Test
	void signupIsExposedUnderApiV1Path() throws Exception {
		UUID userId = UUID.randomUUID();
		when(localAuthService.register(any(AuthCommands.Register.class)))
				.thenReturn(new LocalAuthService.Registration(userId, "traveler@example.com",
						UserStatus.PENDING_EMAIL_VERIFICATION));

		mockMvc.perform(post("/api/v1/auth/signup")
					.contentType(APPLICATION_JSON)
					.content(validSignupJson()))
				.andExpect(status().isCreated());

		verify(localAuthService).register(any(AuthCommands.Register.class));
	}

	private String validSignupJson() {
		return """
				{
				  "email": "traveler@example.com",
				  "password": "correct-horse-battery-staple",
				  "displayName": "부산여행자",
				  "language": "KO",
				  "ageGateAccepted": true,
				  "deviceId": "test-device",
				  "consents": {},
				  "behaviorPersonalizationEnabled": false
				}
				""";
	}
}
