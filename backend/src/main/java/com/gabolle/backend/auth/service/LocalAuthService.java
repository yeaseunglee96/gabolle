package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.common.security.SecurityEventLogger;
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
	private final LoginAttemptGuard loginAttemptGuard;
	private final SecurityEventLogger securityEventLogger;
	private final Clock clock;

	@Autowired
	public LocalAuthService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			LocalCredentialRepository credentialRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, PasswordEncoder passwordEncoder,
			SessionTokenGenerator tokenGenerator, AuthTokenService authTokenService, EmailSender emailSender,
			AuthProperties properties, ConsentPolicy consentPolicy, LoginAttemptGuard loginAttemptGuard,
			SecurityEventLogger securityEventLogger) {
		this(userRepository, consentRepository, credentialRepository, oneTimeTokenRepository, passwordEncoder, tokenGenerator,
				authTokenService, emailSender, properties, consentPolicy, loginAttemptGuard, securityEventLogger,
				Clock.systemUTC());
	}

	LocalAuthService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			LocalCredentialRepository credentialRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, PasswordEncoder passwordEncoder,
			SessionTokenGenerator tokenGenerator, AuthTokenService authTokenService, EmailSender emailSender,
			AuthProperties properties, ConsentPolicy consentPolicy, LoginAttemptGuard loginAttemptGuard,
			SecurityEventLogger securityEventLogger, Clock clock) {
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
		this.loginAttemptGuard = loginAttemptGuard;
		this.securityEventLogger = securityEventLogger;
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
		String normalizedEmail = normalizeEmail(command.email());
		// 🔴 S15P21E201-722 — 가입되지 않은 이메일도 기록한다. 여기 로그가 없으면 유출 목록으로
		//    넓게 뿌리는 공격이 통째로 안 보인다 — 시도 대부분이 이 경로로 들어온다.
		//    응답은 아래 비밀번호 불일치와 <b>똑같은</b> 401 INVALID_CREDENTIALS 다. 그 이메일로
		//    가입했는지를 응답으로 알려주지 않는 것이 의도이므로, 로그만 갈라지고 응답은 같다.
		LocalCredential credential = credentialRepository.findByEmail(normalizedEmail)
				.orElseThrow(() -> {
					this.securityEventLogger.loginFailureForUnknownAccount(normalizedEmail);
					return invalidCredentials();
				});
		Instant now = clock.instant();

		// 🔴 비밀번호를 보기 전에 잠금부터 본다. 잠긴 동안에는 맞는 비밀번호도 거부한다 —
		//    비밀번호가 맞는지 알려 주는 것 자체가 공격자에게 정보이기 때문이다.
		if (credential.isLoginLocked(now)) {
			// 🔴 S15P21E201-682 후속 — 잠긴 뒤의 시도는 지금까지 어디에도 안 남았다.
			//    accountLocked 는 임계치에 닿는 순간 한 번만 남으므로, 이 줄이 없으면
			//    "잠갔더니 멈췄다" 와 "잠긴 채로 계속 맞고 있다" 를 구분할 수 없다.
			securityEventLogger.lockedAccountAttempt(credential.getEmail(),
					java.time.Duration.between(now, credential.getLoginLockedUntil()).toSeconds());
			throw loginLocked(credential.getLoginLockedUntil(), now);
		}

		if (!passwordEncoder.matches(command.password(), credential.getPasswordHash())) {
			// 🔴 세는 일은 별도 트랜잭션에서 한다. 바로 아래에서 예외를 던지면 이 메서드의
			//    트랜잭션이 되돌려지는데, 그 안에서 올렸으면 올린 것도 같이 사라진다.
			//    자세한 이유는 LoginAttemptGuard 의 주석에 있다.
			int attempts = loginAttemptGuard.recordFailure(credential.getLocalCredentialId(), now);
			// S15P21E201-682 — 이메일 원문이 아니라 credential.getEmail() 을 넘긴다(이미
			// 정규화된 값이라 같은 사람의 반복 실패가 같은 해시로 잡힌다). SecurityEventLogger 가
			// 해시로 바꿔 남기므로 여기서 원문이 로그로 새는 자리는 없다.
			securityEventLogger.loginFailure(credential.getEmail(), attempts);
			if (attempts >= properties.getLoginFailureThreshold()) {
				// 방금 이 실패로 잠겼다. LocalCredential.recordFailedLogin 이 잠글 때 쓰는 것과
				// 같은 임계치 비교라 판정이 어긋나지 않는다.
				securityEventLogger.accountLocked(credential.getEmail(), attempts);
			}
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
		// 여기까지 왔으면 비밀번호가 맞았다. 세던 것을 지운다. 바깥 트랜잭션이 그대로 커밋되므로
		// 별도 트랜잭션이 필요 없다.
		credential.recordSuccessfulLogin();
		return authTokenService.issue(credential.getUser(), command.deviceId());
	}

	/**
	 * 잠겨 있다는 응답.
	 *
	 * <p>🔴 이 코드가 비밀번호 틀림({@code INVALID_CREDENTIALS})과 다르다는 것은 <b>그 이메일로 가입한
	 * 계정이 있다</b>는 사실을 알려 준다. 티켓 완료 기준이 둘을 구분하라고 요구하고(화면이 "잠시 후
	 * 다시" 를 띄워야 한다), 회원가입이 이미 {@code EMAIL_ALREADY_EXISTS} 로 같은 사실을 알려 주고
	 * 있어서 여기서 새로 열리는 구멍은 아니다. 알면서 받아들인 것이라 적어 둔다.
	 */
	private AuthException loginLocked(Instant lockedUntil, Instant now) {
		long seconds = Math.max(1, java.time.Duration.between(now, lockedUntil).toSeconds());
		return new AuthException("TOO_MANY_LOGIN_ATTEMPTS",
				"로그인 시도가 너무 많습니다. " + ((seconds + 59) / 60) + "분 뒤에 다시 시도해 주세요.",
				org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
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

	/**
	 * 정규화 규칙 자체는 {@link EmailNormalizer} 에 있다 — 넣을 때와 찾을 때가 갈라지면 대문자로
	 * 적은 사람의 계정을 못 찾는다. 이 메서드는 부르는 자리를 짧게 두려고 남긴 껍데기다.
	 */
	private String normalizeEmail(String email) {
		return EmailNormalizer.normalize(email);
	}

	private String normalizeLanguage(String language) {
		return LanguageNormalizer.normalize(language);
	}

	public record Registration(java.util.UUID userId, String email, UserStatus status) {
	}
}
