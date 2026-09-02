package com.gabolle.backend.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gabolle.auth")
public class AuthProperties {

	private String jwtSecret = "local-development-secret-change-me";
	private Duration accessTokenTtl = Duration.ofMinutes(30);
	private Duration refreshTokenTtl = Duration.ofDays(14);
	private Duration emailVerificationTtl = Duration.ofMinutes(30);
	private Duration passwordResetTtl = Duration.ofMinutes(15);
	private Duration oneTimeTokenRequestCooldown = Duration.ofSeconds(60);
	private Duration oauthChallengeTtl = Duration.ofMinutes(5);
	private String ageGatePolicyVersion = "2026-01";
	private String consentPolicyVersion = "2026-01";
	private String emailVerificationBaseUrl = "http://localhost:3000/verify-email";
	private String passwordResetBaseUrl = "http://localhost:3000/reset-password";
	private List<String> oauthAllowedRedirectUris = new ArrayList<>();
	private List<String> corsAllowedOrigins = new ArrayList<>();
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

	public String getPasswordResetBaseUrl() {
		return passwordResetBaseUrl;
	}

	public void setPasswordResetBaseUrl(String passwordResetBaseUrl) {
		this.passwordResetBaseUrl = passwordResetBaseUrl;
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
