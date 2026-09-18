package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.ConsentUpdateService;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.CurrentUserService;
import com.gabolle.backend.auth.service.LinkedIdentityService;
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

	private final ConsentUpdateService consentUpdateService;

	private final LinkedIdentityService linkedIdentityService;

	public AuthController(LocalAuthService localAuthService, PasswordResetService passwordResetService,
			AuthTokenService tokenService, OAuthLoginService oAuthLoginService,
			OAuthChallengeService oAuthChallengeService, WebAuthCookieService webAuthCookieService,
			CurrentUserService currentUserService, ProfileUpdateService profileUpdateService,
			AccountDeletionService accountDeletionService, OAuthAccountService oAuthAccountService,
			ConsentUpdateService consentUpdateService, LinkedIdentityService linkedIdentityService) {
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
		this.consentUpdateService = consentUpdateService;
		this.linkedIdentityService = linkedIdentityService;
	}

	/**
	 * 내 동의 상태 — S15P21E201-735.
	 *
	 * <p>🔴 {@code GET /me} 에 얹지 않고 따로 뒀다. 동의는 프로필 값이 아니라 <b>정책 판이
	 * 붙은 결정 기록</b>이라 항목 수도 모양도 프로필과 다르게 늘어난다. 한 응답에 섞으면
	 * 로그인 직후 매번 동의 기록 전부를 실어 나르게 되고, 그 응답은 앱의 거의 모든 화면이 쓴다.
	 */
	@GetMapping("/me/consents")
	public ApiResponse<UserConsentsResponse> myConsents(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID userId = authenticatedUserId(authentication);
		return ApiResponse.success(consentUpdateService.get(userId), resolveRequestId(requestId));
	}

	/**
	 * 동의를 바꾼다 — S15P21E201-735.
	 *
	 * <p>이 경로가 없어서 앱의 "행동으로 추천 다듬기" 토글이 <b>기기 안에만</b> 남았다.
	 * 가입 요청 말고는 동의를 서버에 남길 방법이 없었다 — {@code PATCH /me} 는 이름·언어만 받는다.
	 *
	 * <p>🔴 {@code PATCH} 다. 보낸 항목만 바꾸고 안 보낸 항목은 그대로 둔다 — 통째로 덮으면
	 * 앱 화면에 없는 항목(정밀 위치·건강 제약)이 요청마다 조용히 철회된다.
	 */
	@PatchMapping("/me/consents")
	public ApiResponse<UserConsentsResponse> updateMyConsents(Authentication authentication,
			@Valid @RequestBody UpdateConsentsRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID userId = authenticatedUserId(authentication);
		return ApiResponse.success(consentUpdateService.update(userId, request.consents()),
				resolveRequestId(requestId));
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
	 * 계정을 지우기 전 안내 화면이 보여줄 실제 영향 수 — S15P21E201-188/195(진미리 님 요청).
	 *
	 * <p>🔴 {@code reviewCount}는 응답에 없다. 이 백엔드에 리뷰 도메인이 아직 없어서다 — 자세한
	 * 내용은 {@link com.gabolle.backend.auth.service.AccountDeletionService#preview} 참고.
	 */
	@GetMapping("/me/deletion-preview")
	public ApiResponse<AccountDeletionPreviewResponse> deletionPreview(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID userId = authenticatedUserId(authentication);
		return ApiResponse.success(accountDeletionService.preview(userId), resolveRequestId(requestId));
	}

	/**
	 * 지금 붙어 있는 소셜 계정 — S15P21E201-1317, 설정 화면의 「연결된 소셜 계정」.
	 *
	 * <p>이 자리가 없어서 화면은 <b>무엇이 붙어 있는지 못 보여주고</b> 있었다 — 눌러 봐야
	 * 「이미 연결되어 있어요」로 알 수 있었다.
	 *
	 * <p>줄마다 {@code canUnlink} 가 온다. 마지막 로그인 수단은 뗄 수 없다는 판정을 서버가 해서
	 * 보내므로, 화면은 그 규칙을 다시 적을 필요가 없다 — 적으면 두 벌이 되고 한쪽만 낡는다.
	 */
	@GetMapping("/me/identities")
	public ApiResponse<LinkedIdentityService.LinkedIdentities> myIdentities(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(linkedIdentityService.list(authenticatedUserId(authentication)),
				resolveRequestId(requestId));
	}

	/**
	 * 소셜 계정 연결을 뗀다 — S15P21E201-1317.
	 *
	 * <p>🔴 <b>마지막 하나는 안 떼진다</b> — 409 {@code LAST_SIGN_IN_METHOD}. 소셜로만 가입한
	 * 사람이 마지막 연결을 떼면 다시 로그인할 수 없다. 화면이 아니라 여기서 막는다.
	 *
	 * <p>이미 안 붙어 있으면 204 다. 두 번 눌렀을 때 실패로 답하면 <b>실제로 끝난 일을 실패로</b>
	 * 보게 된다.
	 */
	@DeleteMapping("/me/identities/{provider}")
	public ResponseEntity<Void> unlinkIdentity(@PathVariable String provider, Authentication authentication) {
		linkedIdentityService.unlink(authenticatedUserId(authentication),
				AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT)));
		return ResponseEntity.noContent().build();
	}

	/**
	 * 계정과 그 사람의 데이터를 지운다 (S15P21E201-425, 확인 방식은 -837).
	 *
	 * <p>🔴 되돌릴 수 없다. 그래서 사용자가 직접 친 확인 값을 받고, 다르면 아무것도 지우지 않는다.
	 * 비밀번호는 가진 계정만 함께 보내면 되는데, 보냈으면 맞아야 한다 — 소셜로만 가입한 계정에는
	 * 비밀번호가 없어서 그것을 필수로 두면 그 사람들이 탈퇴를 못 한다.
	 * 성공하면 본문 없이 204 다 — 지운 뒤에 돌려줄 것이 없다.
	 */
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request,
			Authentication authentication) {
		accountDeletionService.delete(authenticatedUserId(authentication), request.confirmation(),
				request.password());
		return ResponseEntity.noContent().build();
	}

	/**
	 * S15P21E201-317 — {@code X-Session-Token} 이 실려 오면, 가입 직전까지 그 익명 세션으로
	 * 만든 여행을 이 계정으로 승계한다.
	 *
	 * <p>🔴 {@code Authentication}/{@code SecurityContext} 를 거치지 않고 헤더를 직접 읽는다.
	 * {@code /signup} 은 {@code SecurityConfig} 의 {@code permitAll()} 이라 익명 인증 필터가
	 * 돌긴 하지만, 그 필터가 채우는 principal({@code "anon:" + sessionId})은 "로그인이
	 * 필요한 기존 경로를 열지 않는다" 는 것이 원래 목적이다(그 필터 문서 참고) — 승계 여부를
	 * 그 우회 경로에 얹기보다, 이 자리에서만 쓰는 목적을 헤더로 명시하는 편이 더 분명하다.
	 */
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<LocalAuthService.Registration>> signup(@Valid @RequestBody LocalSignupRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		LocalAuthService.Registration registration = localAuthService.register(new AuthCommands.Register(request.email(),
				request.password(), request.displayName(), request.language(), request.ageGateAcceptedOrFalse(),
				request.deviceId(), request.consents(), request.behaviorPersonalizationEnabledOrFalse(), sessionToken));
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
		tokenService.logout(request.refreshToken(), request.allDevicesOrFalse());
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
				request.displayName(), request.language(), request.consents(), request.behaviorPersonalizationEnabledOrFalse(),
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

	/**
	 * 🔴 아래 셋은 S15P21E201-689 가 실수로 지웠던 것을 되살린 것이다 (2026-09-07).
	 *
	 * <p>지워진 뒤 앱에서 구글·네이버·카카오 버튼을 누르면 첫 요청인 챌린지 발급이 실패해 "이메일 또는
	 * 비밀번호가 올바르지 않아요" 만 떴다. 매핑이 없으면 Spring 이 /error 로 넘기는데 그 경로는 공개
	 * 목록에 없어서 404 가 아니라 <b>401 로 나간다</b> — 앱은 401 을 전부 비밀번호 오류로 바꿔
	 * 보여주므로 원인이 화면에 드러나지 않았다. 웹 세션 갱신·로그아웃도 같이 사라져 있었다.
	 *
	 * <p>{@link AuthControllerRoutesPresentTest} 가 이 셋의 존재를 못으로 박는다.
	 */
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
