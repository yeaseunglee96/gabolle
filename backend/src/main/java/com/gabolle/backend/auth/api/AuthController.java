package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.CurrentUserService;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.service.OAuthChallengeService;
import com.gabolle.backend.auth.service.OAuthLoginService;
import com.gabolle.backend.auth.service.PasswordResetService;
import com.gabolle.backend.auth.service.WebAuthCookieService;
import com.gabolle.backend.common.api.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile({"db", "dev"})
public class AuthController {

	private final LocalAuthService localAuthService;
	private final PasswordResetService passwordResetService;
	private final AuthTokenService tokenService;
	private final OAuthLoginService oAuthLoginService;
	private final OAuthChallengeService oAuthChallengeService;
	private final WebAuthCookieService webAuthCookieService;
	private final CurrentUserService currentUserService;

	public AuthController(LocalAuthService localAuthService, PasswordResetService passwordResetService,
			AuthTokenService tokenService, OAuthLoginService oAuthLoginService,
			OAuthChallengeService oAuthChallengeService, WebAuthCookieService webAuthCookieService,
			CurrentUserService currentUserService) {
		this.localAuthService = localAuthService;
		this.passwordResetService = passwordResetService;
		this.tokenService = tokenService;
		this.oAuthLoginService = oAuthLoginService;
		this.oAuthChallengeService = oAuthChallengeService;
		this.webAuthCookieService = webAuthCookieService;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/me")
	public ApiResponse<AuthUserResponse> me(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID userId = authenticatedUserId(authentication);
		CurrentUserService.CurrentUser currentUser = currentUserService.get(userId);
		return ApiResponse.success(AuthUserResponse.from(currentUser.user(), currentUser.email()),
				resolveRequestId(requestId));
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<LocalAuthService.Registration>> signup(@Valid @RequestBody LocalSignupRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		LocalAuthService.Registration registration = localAuthService.register(new AuthCommands.Register(request.email(),
				request.password(), request.displayName(), request.language(), request.ageGateAccepted(), request.deviceId(),
				request.consents(), request.behaviorPersonalizationEnabled()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(registration, resolveRequestId(requestId)));
	}

	@PostMapping("/email-verification/confirm")
	public ResponseEntity<Void> verifyEmail(@Valid @RequestBody EmailTokenRequest request) {
		localAuthService.verifyEmail(request.token());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/email-verification/resend")
	public ResponseEntity<Void> resendEmailVerification(
			@Valid @RequestBody EmailVerificationResendRequest request) {
		localAuthService.resendEmailVerification(request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/login")
	public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Platform", defaultValue = "MOBILE") String clientPlatform,
			HttpServletResponse response) {
		AuthTokenService.IssuedTokens tokens = localAuthService.login(new AuthCommands.Login(request.email(), request.password(),
				request.deviceId()));
		return ApiResponse.success(writeWebCookieIfNeeded(tokens, clientPlatform, response), resolveRequestId(requestId));
	}

	@PostMapping("/refresh")
	public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthTokenService.IssuedTokens tokens = tokenService.refresh(request.refreshToken(), request.deviceId());
		return ApiResponse.success(AuthTokenResponse.from(tokens), resolveRequestId(requestId));
	}

	@PostMapping("/password-reset/request")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		passwordResetService.request(request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password-reset/confirm")
	public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		passwordResetService.confirm(request.token(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
		tokenService.logout(request.refreshToken(), request.allDevices());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/oauth/{provider}")
	public ApiResponse<AuthTokenResponse> oauth(@PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Platform", defaultValue = "MOBILE") String clientPlatform,
			HttpServletResponse response) {
		AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
		OAuthAccountService.OAuthAccountResult result = oAuthLoginService.login(authProvider, request);
		return ApiResponse.success(writeWebCookieIfNeeded(result.tokens(), clientPlatform, response, result.user(), result.email()),
				resolveRequestId(requestId));
	}

	@PostMapping("/web/refresh")
	public ApiResponse<AuthTokenResponse> webRefresh(
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Device-Id", required = false) String deviceId,
			HttpServletRequest request, HttpServletResponse response) {
		String refreshToken = findCookie(request);
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new AuthException("INVALID_REFRESH_TOKEN", "웹 refresh cookie가 없습니다.", HttpStatus.UNAUTHORIZED);
		}
		AuthTokenService.IssuedTokens tokens = tokenService.refresh(refreshToken, deviceId);
		response.addHeader("Set-Cookie", webAuthCookieService.issue(tokens.refreshToken()).toString());
		return ApiResponse.success(webResponse(tokens), resolveRequestId(requestId));
	}

	@PostMapping("/web/logout")
	public ResponseEntity<Void> webLogout(
			@RequestParam(defaultValue = "false") boolean allDevices,
			HttpServletRequest request, HttpServletResponse response) {
		response.addHeader("Set-Cookie", webAuthCookieService.clear().toString());
		String refreshToken = findCookie(request);
		if (refreshToken != null && !refreshToken.isBlank()) {
			tokenService.logout(refreshToken, allDevices);
		}
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/oauth/{provider}/challenge")
	public ApiResponse<OAuthChallengeService.IssuedChallenge> oauthChallenge(@PathVariable String provider,
			@Valid @RequestBody OAuthChallengeRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
		return ApiResponse.success(
				oAuthChallengeService.issue(authProvider, request.redirectUri(), request.codeChallenge(),
						request.codeChallengeMethod(), request.deviceId()),
				resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}

	private UUID authenticatedUserId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			throw new AuthException("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
		}
		try {
			return UUID.fromString(authentication.getName());
		} catch (IllegalArgumentException exception) {
			throw new AuthException("INVALID_AUTHENTICATION", "인증 정보가 올바르지 않습니다.",
					HttpStatus.UNAUTHORIZED);
		}
	}

	private AuthTokenResponse writeWebCookieIfNeeded(AuthTokenService.IssuedTokens tokens, String clientPlatform,
			HttpServletResponse response) {
		return writeWebCookieIfNeeded(tokens, clientPlatform, response, tokens.user(), tokens.email());
	}

	private AuthTokenResponse writeWebCookieIfNeeded(AuthTokenService.IssuedTokens tokens, String clientPlatform,
			HttpServletResponse response, com.gabolle.backend.user.domain.AppUser user, String email) {
		if (isWeb(clientPlatform)) {
			response.addHeader("Set-Cookie", webAuthCookieService.issue(tokens.refreshToken()).toString());
			return webResponse(tokens, user, email);
		}
		return AuthTokenResponse.from(tokens, user, email);
	}

	private AuthTokenResponse webResponse(AuthTokenService.IssuedTokens tokens) {
		return webResponse(tokens, tokens.user(), tokens.email());
	}

	private AuthTokenResponse webResponse(AuthTokenService.IssuedTokens tokens,
			com.gabolle.backend.user.domain.AppUser user, String email) {
		AuthTokenResponse response = AuthTokenResponse.from(tokens, user, email);
		return new AuthTokenResponse(response.accessToken(), null, response.expiresIn(), response.sessionId(), response.user());
	}

	private boolean isWeb(String clientPlatform) {
		return "WEB".equalsIgnoreCase(clientPlatform);
	}

	private String findCookie(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (webAuthCookieService.cookieName().equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
