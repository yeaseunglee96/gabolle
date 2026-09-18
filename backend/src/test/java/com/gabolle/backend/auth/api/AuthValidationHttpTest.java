package com.gabolle.backend.auth.api;

import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthValidationHttpTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		AuthController controller = new AuthController(mock(LocalAuthService.class), mock(PasswordResetService.class),
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
	void emptySignupRequestReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(APPLICATION_JSON)
				.header("X-Request-Id", "validation-test")
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.meta.requestId").value("validation-test"));
	}
}
