package com.gabolle.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.ConsentUpdateService;
import com.gabolle.backend.auth.service.CurrentUserService;
import com.gabolle.backend.auth.service.ProfileUpdateService;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.service.OAuthChallengeService;
import com.gabolle.backend.auth.service.OAuthLoginService;
import com.gabolle.backend.auth.service.PasswordResetService;
import com.gabolle.backend.auth.service.WebAuthCookieService;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class AuthControllerTest {

	private final CurrentUserService currentUserService = mock(CurrentUserService.class);
	private AuthController controller;

	@BeforeEach
	void setUp() {
		controller = new AuthController(mock(LocalAuthService.class), mock(PasswordResetService.class),
				mock(AuthTokenService.class), mock(OAuthLoginService.class), mock(OAuthChallengeService.class),
				mock(WebAuthCookieService.class), currentUserService,
				mock(ProfileUpdateService.class),
				mock(AccountDeletionService.class), mock(OAuthAccountService.class),
				mock(ConsentUpdateService.class));
	}

	@Test
	void returnsAuthenticatedUser() {
		UUID userId = UUID.randomUUID();
		Authentication authentication = mock(Authentication.class);
		AppUser user = mock(AppUser.class);
		when(authentication.getName()).thenReturn(userId.toString());
		when(user.getUserId()).thenReturn(userId);
		when(user.getDisplayName()).thenReturn("부산여행자");
		when(user.getLanguage()).thenReturn("ko");
		when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(currentUserService.get(userId))
				.thenReturn(new CurrentUserService.CurrentUser(user, "traveler@example.com"));

		ApiResponse<AuthUserResponse> response = controller.me(authentication, "request-123");

		assertThat(response.meta().requestId()).isEqualTo("request-123");
		assertThat(response.data()).isEqualTo(new AuthUserResponse(userId, "traveler@example.com", "부산여행자",
				"ko", UserStatus.ACTIVE, null));
	}

	@Test
	void rejectsMalformedAuthenticationPrincipal() {
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("not-a-uuid");

		assertThatThrownBy(() -> controller.me(authentication, null))
				.isInstanceOfSatisfying(AuthException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("INVALID_AUTHENTICATION");
					assertThat(exception.getStatus().value()).isEqualTo(401);
				});
	}
}
