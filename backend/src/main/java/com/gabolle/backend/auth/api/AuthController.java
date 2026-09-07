package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.CurrentUserService;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.service.OAuthAccountService;
import com.gabolle.backend.auth.service.OAuthChallengeService;
import com.gabolle.backend.auth.service.OAuthLoginService;
import com.gabolle.backend.auth.service.PasswordResetService;
import com.gabolle.backend.auth.service.ProfileUpdateService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
	private final OAuthAccountService oAuthAccountService;
	private final OAuthChallengeService oAuthChallengeService;
	private final WebAuthCookieService webAuthCookieService;
	private final CurrentUserService currentUserService;
	private final AccountDeletionService accountDeletionService;

	private final ProfileUpdateService profileUpdateService;

	public AuthController(LocalAuthService localAuthService, PasswordResetService passwordResetService,
			AuthTokenService tokenService, OAuthLoginService oAuthLoginService,
			OAuthChallengeService oAuthChallengeService, WebAuthCookieService webAuthCookieService,
			CurrentUserService currentUserService, ProfileUpdateService profileUpdateService,
			AccountDeletionService accountDeletionService, OAuthAccountService oAuthAccountService) {
		this.localAuthService = localAuthService;
		this.passwordResetService = passwordResetService;
		this.tokenService = tokenService;
		this.oAuthLoginService = oAuthLoginService;
		this.oAuthChallengeService = oAuthChallengeService;
		this.webAuthCookieService = webAuthCookieService;
		this.currentUserService = currentUserService;
		this.accountDeletionService = accountDeletionService;
		this.profileUpdateService = profileUpdateService;
		this.oAuthAccountService = oAuthAccountService;
	}

	@GetMapping("/me")
	public ApiResponse<AuthUserResponse> me(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID userId = authenticatedUserId(authentication);
		CurrentUserService.CurrentUser currentUser = currentUserService.get(userId);
		return ApiResponse.success(AuthUserResponse.from(currentUser.user(), currentUser.email()),
				resolveRequestId(requestId));
	}

	@PatchMapping("/me")
	public ApiResponse<AuthUserResponse> updateMe(Authentication authentication,
			@Valid @RequestBody UpdateProfileRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID userId = authenticatedUserId(authentication);
		AuthUserResponse response = profileUpdateService.update(userId, request);
		return ApiResponse.success(response, resolveRequestId(requestId));
	}

	/**
	 * 계정과 그 사람의 데이터를 지운다 (S15P21E201-425).
	 *
	 * <p>🔴 되돌릴 수 없다. 그래서 비밀번호를 다시 받아 확인하고, 틀리면 아무것도 지우지 않는다.
	 * 성공하면 본문 없이 204 다 — 지운 뒤에 돌려줄 것이 없다.
	 */
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request,
			Authentication authentication) {
		accountDeletionService.delete(authenticatedUserId(authentication), request.password());
		return ResponseEntity.noContent().build();
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

	/**
	 * 소셜 인증 — S15P21E201-689 (2026-09-07 재설계).
	 *
	 * <p>세 갈래로 답한다. 이미 가입한 소셜 계정이면 {@code LOGGED_IN} + 토큰, 처음 보는 계정이면 계정을 만들지 않고
	 * {@code SIGNUP_REQUIRED} + 가입 티켓과 미리 채울 값, 같은 이메일의 로컬 계정이 있으면 409
	 * {@code OAUTH_ACCOUNT_LINK_REQUIRED} + 연결 티켓({@code AuthExceptionHandler} 가 싣는다).
	 *
	 * <p>🔴 응답 record 가 {@link AuthTokenResponse} 에서 {@link OAuthLoginResponse} 로 바뀌었지만
	 * {@code accessToken}·{@code refreshToken}·{@code expiresIn}·{@code sessionId}·{@code user} 는 같은 이름·같은 자리다 —
	 * 지금 배포된 앱은 {@code data.accessToken} 을 읽으므로 그대로 동작한다. 늘어난 칸(status·signupTicket·prefill)은
	 * 모르는 키라 무시된다.
	 */
	@PostMapping("/oauth/{provider}")
	public ResponseEntity<ApiResponse<OAuthLoginResponse>> oauth(@PathVariable String provider,
			@Valid @RequestBody OAuthLoginRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Platform", defaultValue = "MOBILE") String clientPlatform,
			HttpServletResponse response) {
		AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
		OAuthAccountService.Outcome outcome = oAuthLoginService.login(authProvider, request);
		return oauthResponse(outcome, clientPlatform, response, requestId, HttpStatus.OK);
	}

	/**
	 * 소셜 회원가입 완료 — S15P21E201-689. 가입 티켓 + 닉네임·언어·14세 확인·동의로 계정을 만든다.
	 *
	 * <p>이메일·비밀번호가 없다는 것만 빼면 {@code POST /auth/signup} 과 받는 것이 같다. 로컬 가입은 이메일 인증을
	 * 기다리지만 소셜은 provider 가 이미 신원을 확인했으므로 그 자리에서 토큰이 나간다.
	 */
	@PostMapping("/oauth/signup")
	public ResponseEntity<ApiResponse<OAuthLoginResponse>> oauthSignup(@Valid @RequestBody OAuthSignupRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Platform", defaultValue = "MOBILE") String clientPlatform,
			HttpServletResponse response) {
		OAuthAccountService.Outcome outcome = oAuthAccountService.completeSignup(request.signupTicket(),
				request.displayName(), request.language(), request.consents(), request.behaviorPersonalizationEnabled(),
				request.deviceId());
		// 티켓을 받은 뒤 같은 이메일의 계정이 생겼으면 여기서도 연결 필요(409)가 나올 수 있다.
		return oauthResponse(outcome, clientPlatform, response, requestId, HttpStatus.CREATED);
	}

	/**
	 * 기존 계정에 소셜 계정 연결 — S15P21E201-690. 연결 티켓 + 그 계정의 비밀번호.
	 *
	 * <p>이메일이 같다고 자동으로 붙이지 않는 이유는 {@code DEC-AUTH-010} 이 소유한다 — 남의 이메일로 소셜 계정을
	 * 만든 사람이 기존 계정에 들어갈 수 있기 때문이고, 그래서 비밀번호를 한 번 확인한다.
	 */
	@PostMapping("/oauth/link")
	public ApiResponse<OAuthLoginResponse> oauthLink(@Valid @RequestBody OAuthLinkRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Platform", defaultValue = "MOBILE") String clientPlatform,
			HttpServletResponse response) {
		OAuthAccountService.LoggedIn result = oAuthAccountService.linkWithPassword(request.linkTicket(),
				request.password(), request.deviceId());
		return ApiResponse.success(OAuthLoginResponse.loggedIn(
				writeWebCookieIfNeeded(result.tokens(), clientPlatform, response, result.user(), result.email())),
				resolveRequestId(requestId));
	}

	/**
	 * {@link OAuthAccountService.Outcome} 을 HTTP 로 번역한다.
	 *
	 * <p>🔴 연결 필요는 <b>409 인데 {@code data} 와 {@code error} 를 함께</b> 싣는다. 코드와 상태를 예전과 같게 둔 이유는
	 * 지금 배포된 앱이 {@code error.code == "OAUTH_ACCOUNT_LINK_REQUIRED"} 를 보고 안내를 띄우고 있어서다. 새 앱만
	 * {@code data.linkTicket} 을 읽는다.
	 */
	private ResponseEntity<ApiResponse<OAuthLoginResponse>> oauthResponse(OAuthAccountService.Outcome outcome,
			String clientPlatform, HttpServletResponse response, String requestId, HttpStatus loggedInStatus) {
		String resolvedRequestId = resolveRequestId(requestId);
		if (outcome instanceof OAuthAccountService.LoggedIn loggedIn) {
			OAuthLoginResponse body = OAuthLoginResponse.loggedIn(writeWebCookieIfNeeded(loggedIn.tokens(),
					clientPlatform, response, loggedIn.user(), loggedIn.email()));
			return ResponseEntity.status(loggedInStatus).body(ApiResponse.success(body, resolvedRequestId));
		}
		if (outcome instanceof OAuthAccountService.SignupRequired signup) {
			OAuthLoginResponse body = OAuthLoginResponse.signupRequired(signup.signupTicket(), signup.ticketExpiresAt(),
					new OAuthLoginResponse.Prefill(signup.email(), signup.displayName(), signup.language(),
							signup.emailProvided()));
			return ResponseEntity.ok(ApiResponse.success(body, resolvedRequestId));
		}
		OAuthAccountService.LinkRequired link = (OAuthAccountService.LinkRequired) outcome;
		OAuthLoginResponse body = OAuthLoginResponse.linkRequired(link.linkTicket(), link.ticketExpiresAt(),
				link.maskedEmail(), link.provider().name());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(body,
				new com.gabolle.backend.common.api.ApiError("OAUTH_ACCOUNT_LINK_REQUIRED",
						"이미 가입된 이메일입니다. 비밀번호를 확인하면 이 소셜 계정을 기존 계정에 연결해 드려요."),
				new com.gabolle.backend.common.api.ApiMeta(resolvedRequestId)));
	}

	/**
	 * 로그인한 계정에 소셜 계정 연결 — S15P21E201-690, 설정 화면. 이미 붙어 있으면 {@code alreadyLinked=true} 로 200 이고
	 * 다른 계정에 붙어 있으면 409 {@code OAUTH_IDENTITY_TAKEN} 이다.
	 */
	@PostMapping("/oauth/{provider}/link")
	public ApiResponse<OAuthIdentityResponse> oauthLinkAuthenticated(@PathVariable String provider,
			@Valid @RequestBody OAuthLoginRequest request, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
		OAuthAccountService.LinkedIdentity linked = oAuthLoginService.linkForUser(authenticatedUserId(authentication),
				authProvider, request);
		return ApiResponse.success(OAuthIdentityResponse.of(linked.identity(), linked.alreadyLinked()),
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
