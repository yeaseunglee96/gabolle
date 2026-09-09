package com.gabolle.backend.auth.service;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.common.security.SecurityEventLogger;
import com.gabolle.backend.trip.application.AnonymousTripClaimService;
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

	/** S15P21E201-742 — 이메일 중복을 볼 때 소셜 계정도 함께 본다. {@code emailAlreadyTaken} 참고. */
	private final AuthIdentityRepository identityRepository;
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

	/** S15P21E201-317 — {@code X-Session-Token} 이 가리키는 세션을 찾는다. */
	private final AnonymousSessionService anonymousSessionService;
	/** S15P21E201-317 — 그 세션이 만든 여행을 회원 소유로 옮긴다. */
	private final AnonymousTripClaimService anonymousTripClaimService;

	@Autowired
	public LocalAuthService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			LocalCredentialRepository credentialRepository, AuthIdentityRepository identityRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, PasswordEncoder passwordEncoder,
			SessionTokenGenerator tokenGenerator, AuthTokenService authTokenService, EmailSender emailSender,
			AuthProperties properties, ConsentPolicy consentPolicy, LoginAttemptGuard loginAttemptGuard,
			SecurityEventLogger securityEventLogger, AnonymousSessionService anonymousSessionService,
			AnonymousTripClaimService anonymousTripClaimService) {
		this(userRepository, consentRepository, credentialRepository, identityRepository, oneTimeTokenRepository,
				passwordEncoder, tokenGenerator,
				authTokenService, emailSender, properties, consentPolicy, loginAttemptGuard, securityEventLogger,
				anonymousSessionService, anonymousTripClaimService, Clock.systemUTC());
	}

	LocalAuthService(AppUserRepository userRepository, UserConsentRepository consentRepository,
			LocalCredentialRepository credentialRepository, AuthIdentityRepository identityRepository,
			AuthOneTimeTokenRepository oneTimeTokenRepository, PasswordEncoder passwordEncoder,
			SessionTokenGenerator tokenGenerator, AuthTokenService authTokenService, EmailSender emailSender,
			AuthProperties properties, ConsentPolicy consentPolicy, LoginAttemptGuard loginAttemptGuard,
			SecurityEventLogger securityEventLogger, AnonymousSessionService anonymousSessionService,
			AnonymousTripClaimService anonymousTripClaimService, Clock clock) {
		this.userRepository = userRepository;
		this.consentRepository = consentRepository;
		this.credentialRepository = credentialRepository;
		this.identityRepository = identityRepository;
		this.oneTimeTokenRepository = oneTimeTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenGenerator = tokenGenerator;
		this.authTokenService = authTokenService;
		this.emailSender = emailSender;
		this.properties = properties;
		this.consentPolicy = consentPolicy;
		this.loginAttemptGuard = loginAttemptGuard;
		this.securityEventLogger = securityEventLogger;
		this.anonymousSessionService = anonymousSessionService;
		this.anonymousTripClaimService = anonymousTripClaimService;
		this.clock = clock;
	}

	@Transactional
	public Registration register(AuthCommands.Register command) {
		String email = normalizeEmail(command.email());
		if (!command.ageGateAccepted()) {
			throw new AuthException("AGE_GATE_REQUIRED", "14세 이상 확인이 필요합니다.",
					org.springframework.http.HttpStatus.BAD_REQUEST);
		}
		if (emailAlreadyTaken(email)) {
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
		claimAnonymousTrips(command.sessionToken(), user.getUserId(), now);
		return new Registration(user.getUserId(), email, user.getStatus());
	}

	/**
	 * S15P21E201-317 — 가입 직전까지 익명으로 만든 여행을 새 계정 소유로 옮긴다.
	 *
	 * <p>🔴 이 메서드가 던지는 예외는 {@link #register} 의 {@code @Transactional} 을 그대로
	 * 타고 올라간다 — 승계 도중 실패하면 방금 만든 계정({@code user}·consents·credential)도
	 * 함께 롤백된다(완료 기준 2번). 트랜잭션을 여기서 새로 열지 않는 것이 핵심이다.
	 *
	 * <p>{@code sessionToken} 이 없거나, 있어도 가리키는 세션이 없거나(만료·오타), 그 세션이
	 * 만든 여행이 하나도 없으면 전부 조용히 넘어간다 — 익명 여행 없이 가입하는 것은 실패가
	 * 아니라 <b>정상 흐름</b>이다(완료 기준 3번).
	 */
	private void claimAnonymousTrips(String sessionToken, java.util.UUID newUserId, Instant now) {
		if (sessionToken == null || sessionToken.isBlank()) {
			return;
		}
		anonymousSessionService.resolve(sessionToken)
				.ifPresent(session -> anonymousTripClaimService.claimForNewUser(
						session.getSessionId().toString(), newUserId.toString(), now));
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
	/**
	 * 이 이메일이 이미 쓰이고 있는가 — S15P21E201-742.
	 *
	 * <p>🔴 예전에는 {@code local_credential} 만 봤다. 그래서 이 순서가 그대로 통과했다 —
	 * 구글로 가입(비밀번호 없는 계정이 생긴다) → 같은 주소로 비밀번호 회원가입 → <b>계정이
	 * 하나 더 생긴다.</b> 사용자 눈에는 "가입했는데 내 여행이 없다" 로 보인다. 2026-09-08 에
	 * 사용자가 구글·카카오·네이버로 각각 로그인해 계정이 셋 생기는 것을 제보하면서, 그 반대
	 * 방향으로 같은 구멍이 있다는 것을 코드 조사로 찾았다.
	 *
	 * <p>🔴 <b>어느 소셜로 가입돼 있는지는 응답에 담지 않는다.</b> "이 이메일은 구글로 가입돼
	 * 있습니다" 가 친절해 보이지만, 아무나 이메일을 넣어 보며 <b>그 사람이 어느 소셜을 쓰는지
	 * 알아낼 수 있게 된다.</b> 그래서 이 메서드는 참·거짓만 돌려주고, 부르는 쪽은 예전과 똑같은
	 * {@code EMAIL_ALREADY_EXISTS} 로 답한다 — 앱이 이미 아는 오류라 화면을 안 고쳐도 맞는
	 * 동작이 된다.
	 *
	 * <p>🔴 <b>두 저장소가 같은 정규화를 쓰는 것에 기대고 있다.</b> 소셜 쪽 이메일도
	 * {@code OAuthAccountService} 가 같은 {@link EmailNormalizer} 로 내려 저장한다. 한쪽만 규칙이
	 * 바뀌면 대소문자만 다른 주소가 조용히 새 계정이 된다 — 그때 이 검사는 실패하지 않고
	 * <b>그냥 아무것도 안 잡는다.</b>
	 *
	 * <p>연결이 끊긴 신원은 세지 않는다. 그 신원은 더 이상 로그인 경로가 아니므로, 그것 때문에
	 * 가입을 막으면 <b>아무도 못 쓰는 이메일</b>이 생긴다.
	 */
	private boolean emailAlreadyTaken(String normalizedEmail) {
		if (credentialRepository.findByEmail(normalizedEmail).isPresent()) {
			return true;
		}
		return !identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull(normalizedEmail).isEmpty();
	}

	private String normalizeEmail(String email) {
		return EmailNormalizer.normalize(email);
	}

	private String normalizeLanguage(String language) {
		return LanguageNormalizer.normalize(language);
	}

	public record Registration(java.util.UUID userId, String email, UserStatus status) {
	}
}
