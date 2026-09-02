package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@Profile({"db", "dev"})
public class LocalAuthService {

	private final AppUserRepository userRepository;
	private final UserConsentRepository consentRepository;
	private final LocalCredentialRepository credentialRepository;
	private final AuthOneTimeTokenRepository oneTimeTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final SessionTokenGenerator tokenGenerator;
	private final AuthTokenService authTokenService;
	private final EmailSender emailSender;
	private final AuthProperties properties;
	private final ConsentPolicy consentPolicy;
	private final Clock clock;

	@Autowired
	public LocalAuthService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			LocalCredentialRepository credentialRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, PasswordEncoder passwordEncoder,
			SessionTokenGenerator tokenGenerator, AuthTokenService authTokenService, EmailSender emailSender,
			AuthProperties properties, ConsentPolicy consentPolicy) {
		this(userRepository, consentRepository, credentialRepository, oneTimeTokenRepository, passwordEncoder, tokenGenerator,
				authTokenService, emailSender, properties, consentPolicy, Clock.systemUTC());
	}

	LocalAuthService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			LocalCredentialRepository credentialRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, PasswordEncoder passwordEncoder,
			SessionTokenGenerator tokenGenerator, AuthTokenService authTokenService, EmailSender emailSender,
			AuthProperties properties, ConsentPolicy consentPolicy, Clock clock) {
		this.userRepository = userRepository;
		this.consentRepository = consentRepository;
		this.credentialRepository = credentialRepository;
		this.oneTimeTokenRepository = oneTimeTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenGenerator = tokenGenerator;
		this.authTokenService = authTokenService;
		this.emailSender = emailSender;
		this.properties = properties;
		this.consentPolicy = consentPolicy;
		this.clock = clock;
	}

	@Transactional
	public Registration register(AuthCommands.Register command) {
		String email = normalizeEmail(command.email());
		if (!command.ageGateAccepted()) {
			throw new AuthException("AGE_GATE_REQUIRED", "14세 이상 확인이 필요합니다.",
					org.springframework.http.HttpStatus.BAD_REQUEST);
		}
		if (credentialRepository.findByEmail(email).isPresent()) {
			throw new AuthException("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.",
					org.springframework.http.HttpStatus.CONFLICT);
		}
		Map<ConsentType, Boolean> consents = consentPolicy.validate(command.consents(),
				command.behaviorPersonalizationEnabled());
		Instant now = clock.instant();
		PersonalizationMode mode = command.behaviorPersonalizationEnabled()
				? PersonalizationMode.BEHAVIOR_ENABLED : PersonalizationMode.EXPLICIT_ONLY;
		AppUser user = userRepository.save(AppUser.register(command.displayName().trim(), normalizeLanguage(command.language()),
				now, properties.getAgeGatePolicyVersion(), mode,
				UserStatus.PENDING_EMAIL_VERIFICATION));
		consents.forEach((type, granted) -> consentRepository.save(UserConsent.decide(user, type,
				Boolean.TRUE.equals(granted) ? ConsentStatus.GRANTED : ConsentStatus.REVOKED,
				properties.getConsentPolicyVersion())));
		LocalCredential credential = credentialRepository.save(LocalCredential.create(user, email,
				passwordEncoder.encode(command.password())));
		issueEmailVerification(credential, now);
		return new Registration(user.getUserId(), email, user.getStatus());
	}

	@Transactional
	public void resendEmailVerification(String email) {
		credentialRepository.findByEmailForUpdate(normalizeEmail(email)).ifPresent(credential -> {
			if (credential.getEmailVerifiedAt() != null) {
				return;
			}
			Instant now = clock.instant();
			if (isWithinCooldown(credential, AuthTokenPurpose.EMAIL_VERIFICATION, now)) {
				return;
			}
			consumeActiveTokens(credential, AuthTokenPurpose.EMAIL_VERIFICATION, now);
			issueEmailVerification(credential, now);
		});
	}

	@Transactional
	public void verifyEmail(String rawToken) {
		AuthOneTimeToken token = oneTimeTokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash(rawToken),
				AuthTokenPurpose.EMAIL_VERIFICATION).orElseThrow(this::invalidToken);
		Instant now = clock.instant();
		if (!token.isUsableAt(now)) {
			throw invalidToken();
		}
		token.consume(now);
		LocalCredential credential = token.getLocalCredential();
		credential.markEmailVerified(now);
		credential.getUser().activate();
	}

	@Transactional
	public AuthTokenService.IssuedTokens login(AuthCommands.Login command) {
		LocalCredential credential = credentialRepository.findByEmail(normalizeEmail(command.email()))
				.orElseThrow(this::invalidCredentials);
		if (!passwordEncoder.matches(command.password(), credential.getPasswordHash())) {
			throw invalidCredentials();
		}
		if (credential.getEmailVerifiedAt() == null || credential.getUser().getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
			throw new AuthException("EMAIL_NOT_VERIFIED", "이메일 인증 후 로그인할 수 있습니다.",
					org.springframework.http.HttpStatus.FORBIDDEN);
		}
		if (credential.getUser().getStatus() != UserStatus.ACTIVE) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.",
					org.springframework.http.HttpStatus.FORBIDDEN);
		}
		return authTokenService.issue(credential.getUser(), command.deviceId());
	}

	private AuthException invalidCredentials() {
		return new AuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.",
				org.springframework.http.HttpStatus.UNAUTHORIZED);
	}

	private AuthException invalidToken() {
		return new AuthException("INVALID_OR_EXPIRED_TOKEN", "유효하지 않거나 만료된 인증 토큰입니다.",
				org.springframework.http.HttpStatus.BAD_REQUEST);
	}

	private void issueEmailVerification(LocalCredential credential, Instant now) {
		String rawToken = tokenGenerator.issue();
		oneTimeTokenRepository.save(AuthOneTimeToken.issue(credential, AuthTokenPurpose.EMAIL_VERIFICATION,
				tokenGenerator.hash(rawToken), now.plus(properties.getEmailVerificationTtl())));
		EmailDispatch.afterCommit(() -> emailSender.sendEmailVerification(credential.getEmail(),
				properties.getEmailVerificationBaseUrl() + "?token=" + rawToken));
	}

	private boolean isWithinCooldown(LocalCredential credential, AuthTokenPurpose purpose, Instant now) {
		return oneTimeTokenRepository.findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(credential, purpose)
				.map(AuthOneTimeToken::getCreatedAt)
				.filter(createdAt -> createdAt != null)
				.map(createdAt -> createdAt.plus(properties.getOneTimeTokenRequestCooldown()).isAfter(now))
				.orElse(false);
	}

	private void consumeActiveTokens(LocalCredential credential, AuthTokenPurpose purpose, Instant now) {
		oneTimeTokenRepository.findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(credential, purpose)
				.forEach(token -> token.consume(now));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeLanguage(String language) {
		return LanguageNormalizer.normalize(language);
	}

	public record Registration(java.util.UUID userId, String email, UserStatus status) {
	}
}
