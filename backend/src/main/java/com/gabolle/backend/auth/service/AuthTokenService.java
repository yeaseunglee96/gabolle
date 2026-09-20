package com.gabolle.backend.auth.service;

import com.gabolle.backend.common.security.SecurityEventLogger;
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
import java.time.Duration;
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

	/** {@code null} 일 수 있다 — 이 서비스를 직접 만드는 테스트가 목을 하나 더 두지 않게 한다. */
	private final SecurityEventLogger securityEventLogger;
	private final Clock clock;

	@Autowired
	public AuthTokenService(AccessTokenIssuer accessTokenIssuer, AuthProperties properties,
			AuthSessionRepository sessionRepository, AuthRefreshTokenRepository refreshTokenRepository,
			LocalCredentialRepository credentialRepository, AuthIdentityRepository identityRepository,
			SessionTokenGenerator tokenGenerator, SecurityEventLogger securityEventLogger) {
		this(accessTokenIssuer, properties, sessionRepository, refreshTokenRepository, credentialRepository,
				identityRepository, tokenGenerator, securityEventLogger, Clock.systemUTC());
	}

	AuthTokenService(AccessTokenIssuer accessTokenIssuer, AuthProperties properties,
			AuthSessionRepository sessionRepository, AuthRefreshTokenRepository refreshTokenRepository,
			LocalCredentialRepository credentialRepository, AuthIdentityRepository identityRepository,
			SessionTokenGenerator tokenGenerator, SecurityEventLogger securityEventLogger, Clock clock) {
		this.accessTokenIssuer = accessTokenIssuer;
		this.properties = properties;
		this.sessionRepository = sessionRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.credentialRepository = credentialRepository;
		this.identityRepository = identityRepository;
		this.tokenGenerator = tokenGenerator;
		this.securityEventLogger = securityEventLogger;
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
		// 이미 쓴 토큰이 왔을 때 얼마나 전에 썼는지로 갈린다. 유예 시간 안이면 정상 경쟁이고
		// (두 곳이 거의 동시에 갱신하면 하나만 성공한다), 지났으면 도난으로 보고 계열을 폐기한다.
		// 경쟁이어도 같은 토큰을 다시 돌려줄 수는 없다 — 해시로만 저장하므로 서버에 원문이 없다.
		if (presentedToken.wasUsed()) {
			Duration sinceUsed = Duration.between(presentedToken.getUsedAt(), now);
			boolean withinGrace = !sinceUsed.isNegative()
					&& sinceUsed.compareTo(properties.getRefreshReuseGrace()) <= 0;
			if (!withinGrace) {
				revokeFamily(session.getTokenFamilyId(), now);
				throw new AuthException("REFRESH_TOKEN_REUSED", "이미 사용된 refresh token이 재사용되었습니다.",
						org.springframework.http.HttpStatus.UNAUTHORIZED);
			}
			// 세션이 그 사이 폐기됐거나 사람이 비활성이면 유예와 무관하게 거절한다 —
			// 유예는 "경쟁을 허용" 하는 것이고 "죽은 세션을 살리는" 것이 아니다
			if (!session.isUsableAt(now)) {
				throw new AuthException("INVALID_REFRESH_TOKEN", "만료되었거나 폐기된 refresh token입니다.",
						org.springframework.http.HttpStatus.UNAUTHORIZED);
			}
			ensureActive(session.getUser());
			if (this.securityEventLogger != null) {
				this.securityEventLogger.refreshRace(sinceUsed.toMillis());
			}
			return rotate(session, now);
		}
		if (!presentedToken.isUsableAt(now) || !session.getRefreshTokenHash().equals(presentedHash)) {
			throw new AuthException("INVALID_REFRESH_TOKEN", "만료되었거나 폐기된 refresh token입니다.",
					org.springframework.http.HttpStatus.UNAUTHORIZED);
		}
		ensureActive(session.getUser());
		presentedToken.consume(now);
		return rotate(session, now);
	}

	/**
	 * 새 갱신 표와 접속 표 한 쌍을 발급하고 세션을 그 표로 돌린다.
	 *
	 * <p>정상 갱신과 유예로 허용한 경쟁이 같은 코드를 지나야 한다 — 둘이 서로 다른 발급 경로를
	 * 쓰면 한쪽만 고쳐지는 날이 온다.
	 */
	private IssuedTokens rotate(AuthSession session, Instant now) {
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
