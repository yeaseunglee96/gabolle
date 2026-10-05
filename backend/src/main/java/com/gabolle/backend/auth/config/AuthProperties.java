package com.gabolle.backend.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gabolle.auth")
public class AuthProperties {

	private String jwtSecret = "local-development-secret-change-me";
	private Duration accessTokenTtl = Duration.ofMinutes(30);

	/**
	 * 갱신 토큰의 유효 시간 — 실질적으로 "손 놓고 이만큼 지나면 로그아웃" 이다. 사용자가 체감하는
	 * 로그인 유지 기간은 {@link #accessTokenTtl} 이 아니라 이 값이다.
	 *
	 * <p>{@code AuthTokenService.rotate} 가 갱신할 때마다 만료 시각을 그 시점 기준으로 다시
	 * 계산하므로, 이 값은 "마지막으로 쓴 뒤 얼마나 버티는가" 이지 "로그인한 뒤 얼마 만에
	 * 끊기는가" 가 아니다. 그래서 절대 상한이 없다 — 2일마다 한 번씩 열면 세션은 계속 산다.
	 *
	 * <p>2시간이었다가 2일로 늘렸다(S15P21E201-1978) — 전시 시연 기기가 2시간마다 로그인 화면으로 돌아갔다.
	 *
	 * <p>웹도 이 값을 따른다 — {@code WebAuthCookieService} 가 쿠키 수명을 여기서 가져온다.
	 */
	private Duration refreshTokenTtl = Duration.ofDays(2);

	/**
	 * 이미 쓴 갱신 토큰이 이 시간 안에 다시 오면 도난이 아니라 정상 경쟁으로 본다. 두 곳이 거의
	 * 동시에 갱신하면 하나만 성공하고 나머지는 같은 토큰을 내는데, 그것을 도난으로 보고 세션
	 * 계열을 폐기하면 아무도 잘못하지 않았는데 로그아웃된다.
	 *
	 * <p>대가로, 토큰을 훔친 사람이 정당한 사용 직후 이 시간 안에 같은 토큰을 쓰면 세션이
	 * 폐기되지 않는다. 그래서 짧게 둔다. {@code 0} 이면 재사용을 즉시 도난으로 본다.
	 */
	private Duration refreshReuseGrace = Duration.ofSeconds(30);
	private Duration emailVerificationTtl = Duration.ofMinutes(30);
	/**
	 * 이메일 인증 TTL 과 같은 값이어야 한다 — 사용자가 두 메일에서 다른 시간을 겪지 않게 한다.
	 * 더 짧게 두면 메일이 스팸함을 거치는 동안 만료돼 재요청하게 되고, 재요청마다 이전 토큰이
	 * 소비된다.
	 */
	private Duration passwordResetTtl = Duration.ofMinutes(30);
	private Duration oneTimeTokenRequestCooldown = Duration.ofSeconds(60);
	private Duration oauthChallengeTtl = Duration.ofMinutes(5);
	/** 소셜 인증 → 회원가입(또는 연결) 사이의 티켓 수명. 화면 하나를 채울 시간이면 된다. */
	private Duration oauthSignupTicketTtl = Duration.ofMinutes(10);
	private int loginFailureThreshold = 5;
	/**
	 * 잠금이 유지되는 시간. 5분이면 자동 시도가 시간당 60번으로 묶여 사실상 뚫을 수 없고, 남의
	 * 계정을 일부러 잠가 괴롭히는 것도 5분짜리 성가심에 그친다 — 길게 잡을수록 그 공격이 강해진다.
	 */
	private Duration loginLockoutDuration = Duration.ofMinutes(5);
	private String ageGatePolicyVersion = "2026-01";
	private String consentPolicyVersion = "2026-01";
	private String emailVerificationBaseUrl = "http://localhost:3000/verify-email";
	private String emailVerificationSuccessRedirectUrl = "http://localhost:3000/sign-in?verified=1";
	private String emailVerificationFailureRedirectUrl = "http://localhost:3000/sign-in?verified=0";
	/**
	 * 재설정 메일의 링크가 착지하는 곳. 여기에 {@code "?token=" + 원문토큰} 이 붙으므로 프런트의
	 * 실제 화면 경로와 같아야 한다.
	 */
	private String passwordResetBaseUrl = "https://j15e201.p.ssafy.io/auth/password/reset";
	/**
	 * 애플이 {@code response_mode=form_post} 로 보낸 결과를 넘겨 줄 화면 주소.
	 *
	 * <p>요청에서 받은 값을 쓰지 않고 설정에 박힌 이 값만 쓴다. 애플의 POST 는 누구나 흉내낼 수
	 * 있는 폼 전송이라, 거기 실린 주소로 되돌려 보내면 이 서버가 open redirect 도구가 된다.
	 */
	private String appleFormPostRedirectUrl = "https://j15e201.p.ssafy.io/oauth/apple/callback";

	private List<String> oauthAllowedRedirectUris = new ArrayList<>();
	private List<String> corsAllowedOrigins = new ArrayList<>();
	/**
	 * 기동할 때 운영자(ADMIN)로 올릴 계정의 이메일.
	 *
	 * <p>기본값을 두지 않는다 — 비어 있는 것이 정상 상태이고, 그때는 아무도 운영자가 아니다.
	 * 이 목록이 유일한 사실이라 여기 없는 계정이 이미 ADMIN 이면 다음 기동에서 USER 로 내려가고,
	 * 적은 이메일의 계정이 없으면 기동이 실패한다. 동작은 {@link AdminRoleStartupSynchronizer}.
	 */
	private List<String> adminEmails = new ArrayList<>();
	private String webRefreshCookieName = "gabolle_refresh_token";
	private boolean webRefreshCookieSecure = true;
	private String webRefreshCookieSameSite = "Strict";

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	public void setAccessTokenTtl(Duration accessTokenTtl) {
		this.accessTokenTtl = accessTokenTtl;
	}

	public Duration getRefreshTokenTtl() {
		return refreshTokenTtl;
	}

	public Duration getRefreshReuseGrace() {
		return refreshReuseGrace;
	}

	public void setRefreshReuseGrace(Duration refreshReuseGrace) {
		this.refreshReuseGrace = refreshReuseGrace;
	}

	public void setRefreshTokenTtl(Duration refreshTokenTtl) {
		this.refreshTokenTtl = refreshTokenTtl;
	}

	public Duration getEmailVerificationTtl() {
		return emailVerificationTtl;
	}

	public void setEmailVerificationTtl(Duration emailVerificationTtl) {
		this.emailVerificationTtl = emailVerificationTtl;
	}

	public Duration getPasswordResetTtl() {
		return passwordResetTtl;
	}

	public void setPasswordResetTtl(Duration passwordResetTtl) {
		this.passwordResetTtl = passwordResetTtl;
	}

	public Duration getOneTimeTokenRequestCooldown() {
		return oneTimeTokenRequestCooldown;
	}

	public void setOneTimeTokenRequestCooldown(Duration oneTimeTokenRequestCooldown) {
		this.oneTimeTokenRequestCooldown = oneTimeTokenRequestCooldown;
	}

	public Duration getOauthChallengeTtl() {
		return oauthChallengeTtl;
	}

	public void setOauthChallengeTtl(Duration oauthChallengeTtl) {
		this.oauthChallengeTtl = oauthChallengeTtl;
	}

	public Duration getOauthSignupTicketTtl() {
		return oauthSignupTicketTtl;
	}

	public void setOauthSignupTicketTtl(Duration oauthSignupTicketTtl) {
		this.oauthSignupTicketTtl = oauthSignupTicketTtl;
	}

	public int getLoginFailureThreshold() {
		return loginFailureThreshold;
	}

	public void setLoginFailureThreshold(int loginFailureThreshold) {
		this.loginFailureThreshold = loginFailureThreshold;
	}

	public Duration getLoginLockoutDuration() {
		return loginLockoutDuration;
	}

	public void setLoginLockoutDuration(Duration loginLockoutDuration) {
		this.loginLockoutDuration = loginLockoutDuration;
	}

	public String getAgeGatePolicyVersion() {
		return ageGatePolicyVersion;
	}

	public void setAgeGatePolicyVersion(String ageGatePolicyVersion) {
		this.ageGatePolicyVersion = ageGatePolicyVersion;
	}

	public String getConsentPolicyVersion() {
		return consentPolicyVersion;
	}

	public void setConsentPolicyVersion(String consentPolicyVersion) {
		this.consentPolicyVersion = consentPolicyVersion;
	}

	public String getEmailVerificationBaseUrl() {
		return emailVerificationBaseUrl;
	}

	public void setEmailVerificationBaseUrl(String emailVerificationBaseUrl) {
		this.emailVerificationBaseUrl = emailVerificationBaseUrl;
	}

	public String getEmailVerificationSuccessRedirectUrl() {
		return emailVerificationSuccessRedirectUrl;
	}

	public void setEmailVerificationSuccessRedirectUrl(String emailVerificationSuccessRedirectUrl) {
		this.emailVerificationSuccessRedirectUrl = emailVerificationSuccessRedirectUrl;
	}

	public String getEmailVerificationFailureRedirectUrl() {
		return emailVerificationFailureRedirectUrl;
	}

	public void setEmailVerificationFailureRedirectUrl(String emailVerificationFailureRedirectUrl) {
		this.emailVerificationFailureRedirectUrl = emailVerificationFailureRedirectUrl;
	}

	public String getPasswordResetBaseUrl() {
		return passwordResetBaseUrl;
	}

	public void setPasswordResetBaseUrl(String passwordResetBaseUrl) {
		this.passwordResetBaseUrl = passwordResetBaseUrl;
	}

	public String getAppleFormPostRedirectUrl() {
		return appleFormPostRedirectUrl;
	}

	public void setAppleFormPostRedirectUrl(String appleFormPostRedirectUrl) {
		this.appleFormPostRedirectUrl = appleFormPostRedirectUrl;
	}

	public List<String> getOauthAllowedRedirectUris() {
		return oauthAllowedRedirectUris;
	}

	public void setOauthAllowedRedirectUris(List<String> oauthAllowedRedirectUris) {
		this.oauthAllowedRedirectUris = oauthAllowedRedirectUris == null ? new ArrayList<>() : oauthAllowedRedirectUris;
	}

	public boolean isAllowedOauthRedirectUri(String redirectUri) {
		return redirectUri != null && oauthAllowedRedirectUris.stream().anyMatch(redirectUri::equals);
	}

	public List<String> getAdminEmails() {
		return adminEmails;
	}

	public void setAdminEmails(List<String> adminEmails) {
		this.adminEmails = adminEmails == null ? new ArrayList<>() : adminEmails;
	}

	public List<String> getCorsAllowedOrigins() {
		return corsAllowedOrigins;
	}

	public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
		this.corsAllowedOrigins = corsAllowedOrigins == null ? new ArrayList<>() : corsAllowedOrigins;
	}

	public String getWebRefreshCookieName() {
		return webRefreshCookieName;
	}

	public void setWebRefreshCookieName(String webRefreshCookieName) {
		this.webRefreshCookieName = webRefreshCookieName;
	}

	public boolean isWebRefreshCookieSecure() {
		return webRefreshCookieSecure;
	}

	public void setWebRefreshCookieSecure(boolean webRefreshCookieSecure) {
		this.webRefreshCookieSecure = webRefreshCookieSecure;
	}

	public String getWebRefreshCookieSameSite() {
		return webRefreshCookieSameSite;
	}

	public void setWebRefreshCookieSameSite(String webRefreshCookieSameSite) {
		this.webRefreshCookieSameSite = webRefreshCookieSameSite;
	}
}
