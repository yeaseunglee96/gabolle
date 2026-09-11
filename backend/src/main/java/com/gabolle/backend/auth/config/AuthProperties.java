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
	 * 갱신 표의 유효 시간 — 실질적으로 <b>"손 놓고 이만큼 지나면 로그아웃"</b> 이다
	 * (S15P21E201-739).
	 *
	 * <p>사용자가 체감하는 로그인 유지 기간은 {@link #accessTokenTtl}(30분)이 아니라 이 값이다.
	 * 앱은 접속 표가 만료되면 갱신 표로 조용히 새로 받아 오기 때문이다
	 * ({@code frontend/src/api/client.ts} 가 401 을 받으면 갱신하고 재시도한다).
	 *
	 * <p>🔴 2026-09-08 에 <b>14일에서 2시간으로 줄였다.</b> 사용자가 "반나절을 안 썼는데도
	 * 로그인이 그대로다" 라고 제보했고, 값을 보니 그게 정상 동작이었다. 여행 일정·기록·위치가
	 * 붙어 있는 계정이 공용 PC 나 잃어버린 폰에서 2주 동안 열려 있는 것은 너무 길다.
	 *
	 * <p>🔴 <b>쓰는 동안에는 끊기지 않는다.</b> {@code AuthTokenService.rotate} 가 갱신할 때마다
	 * 만료 시각을 그 시점 기준으로 다시 계산해 넣으므로, 이 값은 "마지막으로 쓴 뒤 얼마나
	 * 버티는가" 이지 "로그인한 뒤 얼마 만에 끊기는가" 가 아니다. 그래서 2시간으로 줄여도
	 * 쓰는 중에 튕기지 않는다.
	 *
	 * <p>같은 이유로 <b>절대 상한이 없다</b> — 2시간마다 한 번씩 열면 그 세션은 계속 산다.
	 * 상한을 두려면 최초 발급 시각을 함께 보는 자리가 하나 더 필요하고, 그것은 이 티켓에
	 * 넣지 않았다({@code S15P21E201-739} 의 "확인 필요").
	 *
	 * <p>웹(브라우저)도 이 값을 따른다 — {@code WebAuthCookieService} 가 쿠키 수명을 여기서
	 * 가져오므로 한 곳만 고치면 앱과 웹이 같이 움직인다.
	 */
	private Duration refreshTokenTtl = Duration.ofHours(2);

	/**
	 * 이미 쓴 갱신 표가 이 시간 안에 다시 오면 <b>도난이 아니라 정상 경쟁</b>으로 본다
	 * (S15P21E201-723).
	 *
	 * <p>🔴 브라우저 탭 두 개만으로 재현되는 문제를 막는 값이다. 접속 표가 만료된 상태에서
	 * 두 곳이 거의 동시에 갱신을 시도하면 하나만 성공하고 나머지는 같은 표를 낸다. 그것을
	 * 도난으로 보고 세션 계열을 폐기하면 <b>아무도 잘못하지 않았는데 로그아웃된다.</b>
	 *
	 * <p>대가가 있다. 표를 훔친 사람이 정당한 사용 직후 이 시간 안에 같은 표를 쓰면 세션이
	 * 폐기되지 않는다. 그래서 짧게 둔다. <b>{@code 0} 으로 두면 예전 동작(즉시 폐기)과
	 * 같아진다</b> — 도난 사고가 실제로 생기면 그렇게 되돌린다.
	 */
	private Duration refreshReuseGrace = Duration.ofSeconds(30);
	private Duration emailVerificationTtl = Duration.ofMinutes(30);
	/**
	 * 🔴 15분이 아니라 30분이다. S15P21E201-433 의 제목과 완료 기준이 "30분 1회용 재설정 링크" 라
	 * 티켓을 정본으로 삼았다. 이메일 인증 TTL 과도 같아져서 사용자가 두 메일에서 다른 시간을 겪지
	 * 않는다. 짧게 두는 것이 안전에 유리하지만, 메일이 스팸함에 들어갔다 나오는 시간을 감안하면
	 * 15분은 실제로 다시 요청하게 만든다 — 재요청마다 이전 토큰이 소비되므로 사용자만 더 헤맨다.
	 */
	private Duration passwordResetTtl = Duration.ofMinutes(30);
	private Duration oneTimeTokenRequestCooldown = Duration.ofSeconds(60);
	private Duration oauthChallengeTtl = Duration.ofMinutes(5);
	/** 소셜 인증 → 회원가입(또는 연결) 사이의 티켓 수명 — S15P21E201-689. 화면 하나를 채울 시간이면 된다. */
	private Duration oauthSignupTicketTtl = Duration.ofMinutes(10);
	/**
	 * 연속으로 이만큼 틀리면 잠근다 (S15P21E201-421). 티켓이 5회로 정했다.
	 */
	private int loginFailureThreshold = 5;
	/**
	 * 잠금이 유지되는 시간.
	 *
	 * <p>🔴 티켓에 값이 정해져 있지 않아 여기서 정했다. 5분이면 자동 시도가 시간당 60번으로 묶여
	 * 사실상 뚫을 수 없고, 진짜 주인은 잠깐 기다렸다 다시 하면 된다. 그리고 남의 계정을 일부러
	 * 잠가 괴롭히는 것도 5분짜리 성가심에 그친다 — 길게 잡을수록 그 공격이 강해진다.
	 */
	private Duration loginLockoutDuration = Duration.ofMinutes(5);
	private String ageGatePolicyVersion = "2026-01";
	private String consentPolicyVersion = "2026-01";
	private String emailVerificationBaseUrl = "http://localhost:3000/verify-email";
	private String emailVerificationSuccessRedirectUrl = "http://localhost:3000/sign-in?verified=1";
	private String emailVerificationFailureRedirectUrl = "http://localhost:3000/sign-in?verified=0";
	/**
	 * 🔴 재설정 메일의 링크가 실제로 착지하는 곳. 여기에 {@code "?token=" + 원문토큰} 이 붙는다.
	 *
	 * <p>기본값이 {@code localhost:3000/reset-password} 였는데 그런 화면은 없다. 프런트의 실제
	 * 화면은 {@code app/auth/password/reset.tsx} 이고 경로가 {@code /auth/password/reset} 이며
	 * 쿼리 {@code token} 을 읽는다. 즉 지금까지 나간 재설정 메일의 링크는 아무 데도 닿지 않았다.
	 *
	 * <p>배포 도메인을 기본값으로 둔다. OAuth redirect URI 허용 목록이 이미 같은 도메인을 기본값
	 * 으로 갖고 있어(같은 파일의 {@code oauthAllowedRedirectUris}) 관례가 맞는다.
	 */
	private String passwordResetBaseUrl = "https://j15e201.p.ssafy.io/auth/password/reset";
	/**
	 * 애플이 {@code response_mode=form_post} 로 보낸 결과를 넘겨 줄 화면 주소 — S15P21E201-833.
	 *
	 * <p>🔴 <b>요청에서 받은 값을 쓰지 않고 설정에 박힌 이 값만 쓴다.</b> 애플의 POST 는 누구나
	 * 흉내낼 수 있는 평범한 폼 전송이고, 거기 실린 주소로 되돌려 보내면 이 서버가 임의의 주소로
	 * 사람을 보내 주는 도구가 된다(open redirect). 보낼 곳이 하나뿐이면 그 공격이 성립하지 않는다.
	 *
	 * <p>기본값은 지금 앱이 쓰는 애플 착지 화면과 같다. 그 화면은 이미 코드와 state 를 쿼리에서
	 * 읽으므로, 이 리다이렉트가 붙으면 화면 쪽은 POST 가 있었다는 것을 모른 채 그대로 돈다.
	 */
	private String appleFormPostRedirectUrl = "https://j15e201.p.ssafy.io/oauth/apple/callback";

	private List<String> oauthAllowedRedirectUris = new ArrayList<>();
	private List<String> corsAllowedOrigins = new ArrayList<>();
	/**
	 * 기동할 때 운영자(ADMIN)로 올릴 계정의 이메일 — S15P21E201-225.
	 *
	 * <p>🔴 <b>기본값을 두지 않는다.</b> 비어 있는 것이 정상 상태고, 그때는 아무도 운영자가
	 * 아니다. 여기에 계정을 하나라도 미리 적어 두면 그 배포에서는 아무 확인 없이 그 사람이
	 * 신고 검토 큐를 열 수 있다 — "설정이 없으면 편의상 기본값을 쓴다" 가 보안 구멍이 되는
	 * 자리가 정확히 여기다. 그래서 값을 주는 일은 배포하는 사람의 명시적인 행동이어야 한다.
	 *
	 * <p>이 목록이 <b>유일한 사실</b>이다. 여기에 없는 계정이 이미 ADMIN 이면 다음 기동에서
	 * USER 로 내려간다. 값을 지우는 것만으로 권한이 회수돼야 하기 때문이다 — 목록에서 뺐는데도
	 * 그 사람이 계속 운영자로 남으면, 권한을 회수하는 방법이 다시 "DB 를 손으로 고치기" 가 된다.
	 *
	 * <p>적은 이메일의 계정이 없으면 기동이 <b>실패</b>한다. 오타 하나로 "운영자를 지정했다고
	 * 생각했는데 아무도 없는" 상태가 조용히 유지되는 것을 막는다. 자세한 동작은
	 * {@link AdminRoleStartupSynchronizer} 에 있다.
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
