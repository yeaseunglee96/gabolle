package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.AuthRefreshTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@Profile({"db", "dev"})
public class PasswordResetService {

	private final LocalCredentialRepository credentialRepository;
	private final AuthOneTimeTokenRepository oneTimeTokenRepository;
	private final AuthSessionRepository sessionRepository;
	private final AuthRefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final SessionTokenGenerator tokenGenerator;
	private final EmailSender emailSender;
	private final AuthProperties properties;
	private final Clock clock;

	@Autowired
	public PasswordResetService(LocalCredentialRepository credentialRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, AuthSessionRepository sessionRepository,
			AuthRefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder, SessionTokenGenerator tokenGenerator, EmailSender emailSender,
			AuthProperties properties) {
		this(credentialRepository, oneTimeTokenRepository, sessionRepository, refreshTokenRepository,
				passwordEncoder, tokenGenerator,
				emailSender, properties, Clock.systemUTC());
	}

	PasswordResetService(LocalCredentialRepository credentialRepository, AuthOneTimeTokenRepository oneTimeTokenRepository,
			AuthSessionRepository sessionRepository, AuthRefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder, SessionTokenGenerator tokenGenerator,
			EmailSender emailSender, AuthProperties properties, Clock clock) {
		this.credentialRepository = credentialRepository;
		this.oneTimeTokenRepository = oneTimeTokenRepository;
		this.sessionRepository = sessionRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenGenerator = tokenGenerator;
		this.emailSender = emailSender;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public void request(String email) {
		credentialRepository.findByEmailForUpdate(normalizeEmail(email)).ifPresent(credential -> {
			Instant now = clock.instant();
			if (isWithinCooldown(credential, now)) {
				return;
			}
			oneTimeTokenRepository.findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(
					credential, AuthTokenPurpose.PASSWORD_RESET).forEach(token -> token.consume(now));
			String rawToken = tokenGenerator.issue();
			oneTimeTokenRepository.save(AuthOneTimeToken.issue(credential, AuthTokenPurpose.PASSWORD_RESET,
					tokenGenerator.hash(rawToken), now.plus(properties.getPasswordResetTtl())));
			EmailDispatch.afterCommit(() -> emailSender.sendPasswordReset(credential.getEmail(),
					properties.getPasswordResetBaseUrl() + "?token=" + rawToken));
		});
	}

	@Transactional
	public void confirm(String rawToken, String newPassword) {
		AuthOneTimeToken token = oneTimeTokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash(rawToken),
				AuthTokenPurpose.PASSWORD_RESET).orElseThrow(this::invalidToken);
		Instant now = clock.instant();
		if (!token.isUsableAt(now)) {
			throw invalidToken();
		}
		LocalCredential credential = token.getLocalCredential();
		credential.changePassword(passwordEncoder.encode(newPassword), now);
		token.consume(now);
		refreshTokenRepository.findAllBySessionUserUserIdAndRevokedAtIsNull(credential.getUser().getUserId())
				.forEach(refreshToken -> refreshToken.revoke(now));
		sessionRepository.findAllByUserUserIdAndRevokedAtIsNull(credential.getUser().getUserId())
				.forEach(session -> session.revoke(now));
	}

	private AuthException invalidToken() {
		return new AuthException("INVALID_OR_EXPIRED_TOKEN", "유효하지 않거나 만료된 인증 토큰입니다.",
				org.springframework.http.HttpStatus.BAD_REQUEST);
	}

	private boolean isWithinCooldown(LocalCredential credential, Instant now) {
		return oneTimeTokenRepository.findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(
				credential, AuthTokenPurpose.PASSWORD_RESET)
				.map(AuthOneTimeToken::getCreatedAt)
				.filter(createdAt -> createdAt != null)
				.map(createdAt -> createdAt.plus(properties.getOneTimeTokenRequestCooldown()).isAfter(now))
				.orElse(false);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
