package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.domain.AuthRefreshToken;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthRefreshTokenRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

@Service
@Profile({"db", "dev"})
public class AuthTokenService {

	private final AccessTokenIssuer accessTokenIssuer;
	private final AuthProperties properties;
	private final AuthSessionRepository sessionRepository;
	private final AuthRefreshTokenRepository refreshTokenRepository;
	private final LocalCredentialRepository credentialRepository;
	private final AuthIdentityRepository identityRepository;
	private final SessionTokenGenerator tokenGenerator;
	private final Clock clock;

	@Autowired
	public AuthTokenService(AccessTokenIssuer accessTokenIssuer, AuthProperties properties,
			AuthSessionRepository sessionRepository, AuthRefreshTokenRepository refreshTokenRepository,
			LocalCredentialRepository credentialRepository, AuthIdentityRepository identityRepository,
			SessionTokenGenerator tokenGenerator) {
		this(accessTokenIssuer, properties, sessionRepository, refreshTokenRepository, credentialRepository,
				identityRepository, tokenGenerator, Clock.systemUTC());
	}

	AuthTokenService(AccessTokenIssuer accessTokenIssuer, AuthProperties properties,
			AuthSessionRepository sessionRepository, AuthRefreshTokenRepository refreshTokenRepository,
			LocalCredentialRepository credentialRepository, AuthIdentityRepository identityRepository,
			SessionTokenGenerator tokenGenerator, Clock clock) {
		this.accessTokenIssuer = accessTokenIssuer;
		this.properties = properties;
		this.sessionRepository = sessionRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.credentialRepository = credentialRepository;
		this.identityRepository = identityRepository;
		this.tokenGenerator = tokenGenerator;
		this.clock = clock;
	}

	@Transactional
	public IssuedTokens issue(AppUser user, String deviceId) {
		return issue(user, deviceId, resolveEmail(user));
	}

	@Transactional
	public IssuedTokens issue(AppUser user, String deviceId, String email) {
		ensureActive(user);
		String resolvedEmail = email == null || email.isBlank() ? resolveEmail(user) : email;
		Instant now = clock.instant();
		String refreshToken = tokenGenerator.issue();
		AuthSession session = AuthSession.issue(user, UUID.randomUUID(), tokenGenerator.hash(refreshToken), deviceId,
				now.plus(properties.getRefreshTokenTtl()));
		session = sessionRepository.save(session);
		refreshTokenRepository.save(AuthRefreshToken.issue(session, tokenGenerator.hash(refreshToken), session.getExpiresAt()));
		AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(user, now, session.getSessionId());
		return new IssuedTokens(accessToken.value(), refreshToken, accessToken.expiresAt(), session.getSessionId(), user,
				resolvedEmail);
	}

	@Transactional(noRollbackFor = AuthException.class)
	public IssuedTokens refresh(String rawRefreshToken, String deviceId) {
		Instant now = clock.instant();
		String presentedHash = tokenGenerator.hash(rawRefreshToken);
		AuthRefreshToken presentedToken = refreshTokenRepository.findByTokenHash(presentedHash)
				.orElseThrow(() -> new AuthException("INVALID_REFRESH_TOKEN", "유효하지 않은 refresh token입니다.",
						org.springframework.http.HttpStatus.UNAUTHORIZED));
		AuthSession session = presentedToken.getSession();
		if (session.getDeviceId() != null && !Objects.equals(session.getDeviceId(), deviceId)) {
			throw new AuthException("DEVICE_MISMATCH", "refresh token이 발급된 기기와 일치하지 않습니다.",
					org.springframework.http.HttpStatus.UNAUTHORIZED);
		}
		if (presentedToken.wasUsed()) {
			revokeFamily(session.getTokenFamilyId(), now);
			throw new AuthException("REFRESH_TOKEN_REUSED", "이미 사용된 refresh token이 재사용되었습니다.",
					org.springframework.http.HttpStatus.UNAUTHORIZED);
		}
		if (!presentedToken.isUsableAt(now) || !session.getRefreshTokenHash().equals(presentedHash)) {
			throw new AuthException("INVALID_REFRESH_TOKEN", "만료되었거나 폐기된 refresh token입니다.",
					org.springframework.http.HttpStatus.UNAUTHORIZED);
		}
		ensureActive(session.getUser());
		presentedToken.consume(now);
		String nextRefreshToken = tokenGenerator.issue();
		String nextHash = tokenGenerator.hash(nextRefreshToken);
		session.rotate(nextHash, now.plus(properties.getRefreshTokenTtl()));
		sessionRepository.save(session);
		refreshTokenRepository.save(AuthRefreshToken.issue(session, nextHash, session.getExpiresAt()));
		AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(session.getUser(), now,
				session.getSessionId());
		String email = resolveEmail(session.getUser());
		return new IssuedTokens(accessToken.value(), nextRefreshToken, accessToken.expiresAt(), session.getSessionId(),
				session.getUser(), email);
	}

	@Transactional
	public void logout(String rawRefreshToken, boolean allDevices) {
		AuthRefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenGenerator.hash(rawRefreshToken))
				.orElseThrow(() -> new AuthException("INVALID_REFRESH_TOKEN", "유효하지 않은 refresh token입니다.",
					org.springframework.http.HttpStatus.UNAUTHORIZED));
		AuthSession session = refreshToken.getSession();
		Instant now = clock.instant();
		if (allDevices) {
			refreshTokenRepository.findAllBySessionUserUserIdAndRevokedAtIsNull(session.getUser().getUserId())
					.forEach(activeToken -> activeToken.revoke(now));
			sessionRepository.findAllByUserUserIdAndRevokedAtIsNull(session.getUser().getUserId())
					.forEach(activeSession -> activeSession.revoke(now));
		} else {
			revokeFamily(session.getTokenFamilyId(), now);
		}
	}

	private void revokeFamily(UUID tokenFamilyId, Instant now) {
		refreshTokenRepository.findAllBySessionTokenFamilyIdAndRevokedAtIsNull(tokenFamilyId)
				.forEach(token -> token.revoke(now));
		sessionRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(tokenFamilyId)
				.forEach(session -> session.revoke(now));
	}

	private void ensureActive(AppUser user) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
					org.springframework.http.HttpStatus.FORBIDDEN);
		}
	}

	private String resolveEmail(AppUser user) {
		String localEmail = credentialRepository.findByUserUserId(user.getUserId())
				.map(credential -> credential.getEmail()).orElse(null);
		if (localEmail != null && !localEmail.isBlank()) {
			return localEmail;
		}
		return identityRepository.findAllByUserUserId(user.getUserId()).stream()
				.filter(identity -> identity.isActive() && identity.getProviderEmail() != null
						&& !identity.getProviderEmail().isBlank())
				.findFirst()
				.map(identity -> identity.getProviderEmail())
				.orElse(null);
	}

	public record IssuedTokens(String accessToken, String refreshToken, Instant accessTokenExpiresAt, UUID sessionId,
			AppUser user, String email) {
	}
}
