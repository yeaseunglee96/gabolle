package com.gabolle.backend.auth.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.LocalAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 메일 링크 클릭이 실제로 302 를 주는지, 그리고 실패할 때 토큰이 리다이렉트 주소로
 * 새지 않는지를 못 박는다.
 */
class EmailVerificationLinkControllerTest {

	private static final String TOKEN = "raw-verification-token";

	private LocalAuthService localAuthService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		localAuthService = mock(LocalAuthService.class);
		AuthProperties properties = new AuthProperties();
		properties.setEmailVerificationSuccessRedirectUrl("https://example.test/sign-in?verified=1");
		properties.setEmailVerificationFailureRedirectUrl("https://example.test/sign-in?verified=0");
		mockMvc = MockMvcBuilders
				.standaloneSetup(new EmailVerificationLinkController(localAuthService, properties))
				.build();
	}

	@Test
	void redirectsToSuccessUrlAndConsumesToken() throws Exception {
		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", TOKEN))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://example.test/sign-in?verified=1"));

		verify(localAuthService).verifyEmail(eq(TOKEN));
	}

	@Test
	void redirectsToFailureUrlWithErrorCodeWhenTokenIsRejected() throws Exception {
		doThrow(new AuthException("INVALID_OR_EXPIRED_TOKEN", "유효하지 않거나 만료된 인증 토큰입니다.",
				HttpStatus.BAD_REQUEST)).when(localAuthService).verifyEmail(TOKEN);

		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", TOKEN))
				.andExpect(status().isFound())
				.andExpect(header().string("Location",
						"https://example.test/sign-in?verified=0&error=INVALID_OR_EXPIRED_TOKEN"));
	}

	@Test
	void neverPutsTheTokenInTheRedirectLocation() throws Exception {
		doThrow(new AuthException("INVALID_OR_EXPIRED_TOKEN", "유효하지 않거나 만료된 인증 토큰입니다.",
				HttpStatus.BAD_REQUEST)).when(localAuthService).verifyEmail(TOKEN);

		// 실패 응답의 Location 에 토큰이 실리면 브라우저 이력·Referer 로 새어 나가고,
		// 아직 소비되지 않은 토큰이면 그것으로 인증을 끝낼 수 있다.
		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", TOKEN))
				.andExpect(status().isFound())
				.andExpect(result -> {
					String location = result.getResponse().getHeader("Location");
					if (location != null && location.contains(TOKEN)) {
						throw new AssertionError("리다이렉트 주소에 토큰이 실렸다: " + location);
					}
				});
	}

	@Test
	void missingTokenRedirectsToFailureInsteadOfErroring() throws Exception {
		// 처음엔 @RequestParam 을 필수로 걸고 이 테스트가 400 을 단정했다. 배포에서
		// 재 보니 500 이었다 — AuthExceptionHandler 의 Exception catch-all 이
		// MissingServletRequestParameterException 을 잡는다. 테스트가 advice 없이
		// 돌아서 실제와 다른 것을 통과시켰다. 잘린 링크도 실패 화면으로 보낸다.
		mockMvc.perform(get("/api/v1/auth/email-verification"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location",
						"https://example.test/sign-in?verified=0&error=MISSING_VERIFICATION_TOKEN"));
	}

	@Test
	void blankTokenRedirectsToFailureToo() throws Exception {
		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", "   "))
				.andExpect(status().isFound())
				.andExpect(header().string("Location",
						"https://example.test/sign-in?verified=0&error=MISSING_VERIFICATION_TOKEN"));
	}
}
