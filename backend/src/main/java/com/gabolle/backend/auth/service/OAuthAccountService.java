package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.domain.OAuthSignupTicket;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.common.security.SecurityEventLogger;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.ConsentStatus;
import com.gabolle.backend.user.domain.ConsentType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserConsent;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * 소셜 신원을 계정으로 잇는 규칙. {@link #authenticate} 는 셋 중 하나로 답한다.
 * <ul>
 *   <li>{@link LoggedIn} — 이미 붙어 있는 신원이거나 같은 이메일의 계정에 자동으로 붙인 경우.
 *       동의를 다시 묻지 않는다.</li>
 *   <li>{@link SignupRequired} — 처음 보는 신원. 계정을 만들지 않고 10분짜리 가입 티켓과 미리 채울 값을 준다.
 *       {@link #completeSignup} 이 그 티켓으로 계정을 만든다.</li>
 *   <li>{@link LinkRequired} — 메일 인증을 안 끝낸 로컬 계정이 같은 이메일을 쓰고 있다. 연결 티켓을
 *       싣고, {@link #linkWithPassword} 가 비밀번호를 확인한 뒤 붙인다.</li>
 * </ul>
 *
 * <p>같은 이메일이면 새 계정을 만들지 않고 기존 계정에 붙인다 — 고르는 기준과 붙이지 않는 경우는
 * {@link #autoLinkTarget} 이 소유한다.
 *
 * <p>옛 앱은 첫 요청에 14세 확인과 동의를 함께 보낸다. 그 요청이 오면 한 번에 계정을 만들어
 * {@link LoggedIn} 으로 답한다({@link #authenticate} 의 {@code oneStep}). 동의가 없으면 티켓이다.
 *
 * <p>이메일 없이도 가입할 수 있다 — 카카오 기본 동의는 이메일을 주지 않고
 * {@code auth_identity.provider_email} 은 NULL 을 허용한다. 그 계정이 비밀번호 재설정 같은 메일 기능을
 * 못 쓴다는 점은 {@code SignupRequired.emailProvided=false} 로 화면에 알린다.
 */
@Service
@Profile({"db", "dev"})
public class OAuthAccountService {

	private final AuthIdentityRepository identityRepository;
	private final LocalCredentialRepository credentialRepository;
	private final AppUserRepository userRepository;
	private final UserConsentRepository consentRepository;
	private final AuthTokenService tokenService;
	private final AuthProperties properties;
	private final ConsentPolicy consentPolicy;
	private final OAuthSignupTicketService ticketService;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptGuard loginAttemptGuard;

	/**
	 * 소셜 연결의 비밀번호 확인 실패를 여기서 직접 남긴다. {@code GlobalAuthExceptionHandler} 는
	 * 로그인 전용 코드({@code INVALID_CREDENTIALS}·{@code TOO_MANY_LOGIN_ATTEMPTS})를 "던지는
	 * 지점에서 남긴다" 는 전제로 건너뛰기 때문이다. {@code null} 을 허용하지 않는다 — 허용하면
	 * 배선이 빠진 것을 아무도 모른다.
	 */
	private final SecurityEventLogger securityEventLogger;

	private final Clock clock;

	@Autowired
	public OAuthAccountService(AuthIdentityRepository identityRepository, LocalCredentialRepository credentialRepository,
			AppUserRepository userRepository, UserConsentRepository consentRepository, AuthTokenService tokenService,
			AuthProperties properties, ConsentPolicy consentPolicy, OAuthSignupTicketService ticketService,
			PasswordEncoder passwordEncoder, LoginAttemptGuard loginAttemptGuard,
			SecurityEventLogger securityEventLogger, Clock clock) {
		this.identityRepository = identityRepository;
		this.credentialRepository = credentialRepository;
		this.userRepository = userRepository;
		this.consentRepository = consentRepository;
		this.tokenService = tokenService;
		this.properties = properties;
		this.consentPolicy = consentPolicy;
		this.ticketService = ticketService;
		this.passwordEncoder = passwordEncoder;
		this.loginAttemptGuard = loginAttemptGuard;
		this.securityEventLogger = securityEventLogger;
		this.clock = clock;
	}

	/**
	 * {@link #authenticate} 의 결과 — 셋 중 하나.
	 *
	 * <p>연결 필요는 예외가 아니라 반환값이어야 한다. 예외로 던지면 트랜잭션이 되돌려지면서 방금
	 * 만든 연결 티켓의 INSERT 도 함께 사라지고, 클라이언트는 DB 에 없는 티켓을 받는다.
	 */
	public sealed interface Outcome permits LoggedIn, SignupRequired, LinkRequired {
	}

	public record LoggedIn(AuthTokenService.IssuedTokens tokens, AppUser user, String email) implements Outcome {
	}

	public record SignupRequired(String signupTicket, Instant ticketExpiresAt, String email, String displayName,
			String language) implements Outcome {
		public boolean emailProvided() {
			return email != null;
		}
	}

	/** 같은 이메일의 로컬 계정이 있다. 화면은 비밀번호를 받아 {@link #linkWithPassword} 를 부른다. */
	public record LinkRequired(String linkTicket, Instant ticketExpiresAt, String maskedEmail, AuthProvider provider)
			implements Outcome {
	}

	/**
	 * 소셜 인증이 끝난 뒤 — 로그인인가, 가입 화면으로 갈 것인가, 연결이 필요한가.
	 *
	 * @param rawConsents 옛 앱이 첫 요청에 실어 보내는 동의. 있으면 한 번에 가입한다(호환)
	 * @param ageGateAccepted 옛 앱이 첫 요청에 실어 보내는 14세 확인
	 */
	@Transactional
	public Outcome authenticate(AuthProvider provider, OAuthProviderClient.OAuthUserProfile profile, String deviceId,
			Map<String, Boolean> rawConsents, boolean behaviorPersonalizationEnabled, boolean ageGateAccepted) {
		Optional<AuthIdentity> existing = identityRepository.findByProviderAndProviderSubject(provider, profile.subject());
		if (existing.isPresent()) {
			return login(existing.get(), profile, deviceId);
		}

		String email = normalizeEmailOrNull(profile.email());
		Optional<LocalCredential> local = findLocalCredential(email);

		AppUser attachTo = autoLinkTarget(provider, email, local);
		if (attachTo != null) {
			AuthIdentity linked = AuthIdentity.link(attachTo, provider, profile.subject(), email);
			applyProviderEmail(linked, profile, email);
			identityRepository.save(linked);
			return issue(attachTo, deviceId, email);
		}
		if (local.isPresent()) {
			return linkRequired(provider, profile.subject(), email, local.get().getUser(), deviceId);
		}

		boolean oneStep = ageGateAccepted && rawConsents != null && !rawConsents.isEmpty();
		if (oneStep) {
			// 옛 앱 호환 — 동의와 14세 확인을 첫 요청에 실어 보낸 경우는 그 자리에서 계정을 만든다.
			AppUser user = register(profile.displayName(), profile.language(), rawConsents, behaviorPersonalizationEnabled);
			AuthIdentity linked = AuthIdentity.link(user, provider, profile.subject(), email);
			applyProviderEmail(linked, profile, email);
			identityRepository.save(linked);
			return issue(user, deviceId, email);
		}

		OAuthSignupTicketService.IssuedTicket ticket = ticketService.issueSignup(provider, profile.subject(), email,
				normalizeDisplayName(profile.displayName()), LanguageNormalizer.normalize(profile.language()), deviceId);
		return new SignupRequired(ticket.rawTicket(), ticket.expiresAt(), email, normalizeDisplayName(profile.displayName()),
				LanguageNormalizer.normalize(profile.language()));
	}

	/**
	 * 가입 티켓으로 계정을 만든다 — 회원가입 화면의 "완료".
	 *
	 * <p>티켓이 발급된 뒤 같은 사람이 다른 기기에서 먼저 가입했을 수 있다. 그래서 티켓 소비 뒤에
	 * {@link #authenticate} 와 같은 판정을 한 번 더 한다.
	 *
	 * @param displayName 비우면 티켓의 provider 이름
	 * @param language 비우면 티켓의 provider 언어
	 */
	@Transactional
	public Outcome completeSignup(String rawTicket, String displayName, String language, Map<String, Boolean> rawConsents,
			boolean behaviorPersonalizationEnabled, String deviceId) {
		OAuthSignupTicket ticket = ticketService.consume(rawTicket, OAuthSignupTicket.Kind.SIGNUP);

		Optional<AuthIdentity> existing = identityRepository.findByProviderAndProviderSubject(ticket.getProvider(),
				ticket.getProviderSubject());
		if (existing.isPresent()) {
			return login(existing.get(), null, deviceId);
		}
		Optional<LocalCredential> local = findLocalCredential(ticket.getProviderEmail());
		AppUser attachTo = autoLinkTarget(ticket.getProvider(), ticket.getProviderEmail(), local);
		if (attachTo != null) {
			identityRepository.save(AuthIdentity.link(attachTo, ticket.getProvider(), ticket.getProviderSubject(),
					ticket.getProviderEmail()));
			return issue(attachTo, deviceId, ticket.getProviderEmail());
		}
		if (local.isPresent()) {
			return linkRequired(ticket.getProvider(), ticket.getProviderSubject(), ticket.getProviderEmail(),
					local.get().getUser(), deviceId);
		}

		String finalName = (displayName == null || displayName.isBlank()) ? ticket.getDisplayName() : displayName;
		String finalLanguage = (language == null || language.isBlank()) ? ticket.getLanguage() : language;
		AppUser user = register(finalName, finalLanguage, rawConsents, behaviorPersonalizationEnabled);
		// 이 경로만 이메일 신뢰도를 못 남긴다 — 가입 티켓에는 주소만 실려 있고 "검증됐나·아직
		// 유효한가" 는 provider 응답에만 있는데 그 응답은 티켓 발급 때 이미 지나갔다. 두 값은
		// null(모름)로 시작하고 다음 로그인 때 applyProviderEmail 이 채운다. 자동 연결 판정이
		// "명시적으로 유효하지 않다고 답한 경우에만 배제" 라 모름은 안전한 쪽으로 떨어진다.
		identityRepository.save(AuthIdentity.link(user, ticket.getProvider(), ticket.getProviderSubject(),
				ticket.getProviderEmail()));
		return issue(user, deviceId, ticket.getProviderEmail());
	}

	/**
	 * 연결 티켓 + 비밀번호로 기존 계정에 소셜 신원을 붙인다.
	 *
	 * <p>비밀번호 판정은 로그인({@code LocalAuthService.login})과 같은 규칙을 지킨다 — 잠긴 계정은
	 * 맞는 비밀번호도 거부하고, 틀리면 실패 횟수를 올린다. 이 경로가 비밀번호 추측의 우회로가
	 * 되지 않게 하려는 것이다.
	 *
	 * <p>틀린 비밀번호로 예외가 나가면 트랜잭션이 되돌려지므로 티켓 소비도 함께 취소된다 — 같은
	 * 티켓으로 다시 시도할 수 있고, 그것이 의도다. 실패 횟수는 {@link LoginAttemptGuard} 가 별도
	 * 트랜잭션에서 올리므로 되돌려지지 않는다.
	 */
	@Transactional
	public LoggedIn linkWithPassword(String rawTicket, String password, String deviceId) {
		OAuthSignupTicket ticket = ticketService.consume(rawTicket, OAuthSignupTicket.Kind.LINK);
		AppUser user = ticket.getExistingUser();
		LocalCredential credential = credentialRepository.findByUserUserId(user.getUserId())
				.orElseThrow(() -> new AuthException("OAUTH_TICKET_INVALID", "연결할 계정을 찾을 수 없어요.", HttpStatus.BAD_REQUEST));
		Instant now = clock.instant();

		if (credential.isLoginLocked(now)) {
			long seconds = Math.max(1, java.time.Duration.between(now, credential.getLoginLockedUntil()).toSeconds());
			securityEventLogger.lockedAccountAttempt(credential.getEmail(), seconds);
			throw new AuthException("TOO_MANY_LOGIN_ATTEMPTS",
					"로그인 시도가 너무 많습니다. " + ((seconds + 59) / 60) + "분 뒤에 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
		}
		if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
			// recordFailure 가 돌려주는 값이 이번 실패까지 포함한 횟수다.
			int attempts = loginAttemptGuard.recordFailure(credential.getLocalCredentialId(), now);
			securityEventLogger.loginFailure(credential.getEmail(), attempts);
			throw new AuthException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.FORBIDDEN);
		}
		credential.recordSuccessfulLogin();

		AuthIdentity identity = linkIdentity(user, ticket.getProvider(), ticket.getProviderSubject(), ticket.getProviderEmail());
		return issue(user, deviceId, identity.getProviderEmail() != null ? identity.getProviderEmail() : credential.getEmail());
	}

	/**
	 * 로그인한 계정에 소셜 신원을 붙인다 — 설정 화면. 이미 같은 계정에 붙어 있으면 그대로 돌려준다.
	 *
	 * @throws AuthException {@code OAUTH_IDENTITY_TAKEN}(409) 그 소셜 신원이 다른 계정에 붙어 있다
	 */
	@Transactional
	public LinkedIdentity linkAuthenticated(UUID userId, AuthProvider provider, OAuthProviderClient.OAuthUserProfile profile) {
		AppUser user = userRepository.findById(userId)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.FORBIDDEN));
		Optional<AuthIdentity> existing = identityRepository.findByProviderAndProviderSubject(provider, profile.subject());
		if (existing.isPresent()) {
			AuthIdentity identity = existing.get();
			if (identity.isActive()) {
				if (identity.getUser().getUserId().equals(userId)) {
					return new LinkedIdentity(identity, true);
				}
				throw identityTaken();
			}
			// 끊긴 연결은 누구의 것도 아니므로 다시 붙인다. 여기서 거절하면 한 번 뗀 소셜
			// 계정은 다시 붙일 수 없다.
			identity.relink(user, clock.instant());
			applyProviderEmail(identity, profile, normalizeEmailOrNull(profile.email()));
			identityRepository.save(identity);
			return new LinkedIdentity(identity, false);
		}
		String linkedEmail = normalizeEmailOrNull(profile.email());
		AuthIdentity linked = AuthIdentity.link(user, provider, profile.subject(), linkedEmail);
		applyProviderEmail(linked, profile, linkedEmail);
		identityRepository.save(linked);
		return new LinkedIdentity(linked, false);
	}

	public record LinkedIdentity(AuthIdentity identity, boolean alreadyLinked) {
	}

	// ── 안쪽 규칙 ──────────────────────────────────────────────────────────────

	private LoggedIn login(AuthIdentity identity, OAuthProviderClient.OAuthUserProfile profile, String deviceId) {
		if (!identity.isActive()) {
			throw new AuthException("OAUTH_IDENTITY_UNLINKED", "연결이 해제된 소셜 계정입니다.", HttpStatus.CONFLICT);
		}
		if (identity.getUser().getStatus() != UserStatus.ACTIVE) {
			throw new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.FORBIDDEN);
		}
		String email = (profile == null || profile.email() == null || profile.email().isBlank())
				? identity.getProviderEmail() : normalizeEmail(profile.email());
		applyProviderEmail(identity, profile, email);
		identityRepository.save(identity);
		return issue(identity.getUser(), deviceId, email);
	}

	/**
	 * 로그인할 때마다 provider 가 준 이메일과 그 신뢰도를 저장된 값에 다시 쓴다.
	 *
	 * <p>갱신이 {@link #autoLinkTarget} 판정의 전제다. 카카오는 주소가 다른 카카오계정으로
	 * 옮겨가면 유효하지 않다고 답하는데({@code is_email_valid=false}), 그 신호를 갱신하지 않으면
	 * 옮겨간 뒤에도 유효한 줄 알고 엉뚱한 사람에게 계정을 잇는다.
	 *
	 * <p>이메일이 안 왔으면 아무것도 지우지 않는다. 안 온 것과 없어진 것은 다르다.
	 *
	 * <p>값을 먼저 채우고 그 객체를 저장한다 — 저장소 반환값에 기대면 mock 이 {@code null} 을
	 * 돌려주는 자리에서만 죽는다.
	 */
	private static void applyProviderEmail(AuthIdentity identity, OAuthProviderClient.OAuthUserProfile profile,
			String normalizedEmail) {
		if (identity == null || profile == null || normalizedEmail == null || normalizedEmail.isBlank()) {
			return;
		}
		identity.recordProviderEmail(normalizedEmail, profile.emailVerified(), profile.emailValid());
	}

	/** 이메일을 안 주는 provider 는 겹칠지 볼 수가 없으니 질의도 하지 않는다. */
	private Optional<LocalCredential> findLocalCredential(String email) {
		return email == null ? Optional.empty() : credentialRepository.findByEmail(email);
	}

	/**
	 * 이 이메일을 이미 쓰고 있는 계정 — 있으면 새 계정을 만들지 않고 거기에 신원을 붙인다.
	 *
	 * <p>고르는 순서는 확인된 비밀번호 계정이 먼저고, 없으면 그 주소로 연결돼 있는 소셜 신원 가운데
	 * 가장 먼저 가입한 계정이다. 기준이 호출 순서나 DB 반환 순서에 달려 있으면 같은 사람이 같은
	 * 조건에서 다른 계정에 붙으므로, 이 순서는 결정적이어야 한다.
	 *
	 * <p>붙이지 않는 경우가 셋이다. 메일 인증을 안 끝낸 비밀번호 계정은 그 주소가 그 사람 것인지
	 * 확인하지 못했으므로 비밀번호를 묻는다. 카카오가 유효하지 않다고 답한 주소
	 * ({@code email_valid=false})는 다른 카카오계정으로 옮겨갔을 수 있어 그대로 믿으면 엉뚱한
	 * 사람에게 이어진다. 대상 계정에 같은 제공자가 이미 붙어 있으면 같은 주소를 든 다른 계정이
	 * 온 것이라 자동으로 처리할 자리가 아니다.
	 */
	private AppUser autoLinkTarget(AuthProvider provider, String email, Optional<LocalCredential> local) {
		if (email == null) {
			return null;
		}
		if (local.isPresent()) {
			LocalCredential credential = local.get();
			if (credential.getEmailVerifiedAt() == null) {
				return null;
			}
			return canAttach(provider, credential.getUser()) ? credential.getUser() : null;
		}
		return identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull(email).stream()
				.filter((identity) -> !Boolean.FALSE.equals(identity.getEmailValid()))
				.map(AuthIdentity::getUser)
				.filter((user) -> canAttach(provider, user))
				.min(Comparator.comparing(AppUser::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.orElse(null);
	}

	private boolean canAttach(AuthProvider provider, AppUser user) {
		if (user == null || user.getStatus() != UserStatus.ACTIVE) {
			return false;
		}
		return identityRepository.findAllByUserUserId(user.getUserId()).stream()
				.noneMatch((identity) -> identity.isActive() && identity.getProvider() == provider);
	}

	/** 비밀번호를 확인하고 붙이는 옛 경로 — 메일 인증을 안 끝낸 계정에만 남는다. */
	private LinkRequired linkRequired(AuthProvider provider, String subject, String email, AppUser user,
			String deviceId) {
		OAuthSignupTicketService.IssuedTicket ticket = ticketService.issueLink(provider, subject, email, user, deviceId);
		return new LinkRequired(ticket.rawTicket(), ticket.expiresAt(), com.gabolle.backend.auth.api.OAuthLoginResponse
				.mask(email), provider);
	}

	private AuthIdentity linkIdentity(AppUser user, AuthProvider provider, String subject, String email) {
		Optional<AuthIdentity> existing = identityRepository.findByProviderAndProviderSubject(provider, subject);
		if (existing.isPresent()) {
			if (existing.get().getUser().getUserId().equals(user.getUserId()) && existing.get().isActive()) {
				return existing.get();
			}
			throw identityTaken();
		}
		return identityRepository.save(AuthIdentity.link(user, provider, subject, email));
	}

	private AppUser register(String displayName, String language, Map<String, Boolean> rawConsents,
			boolean behaviorPersonalizationEnabled) {
		Map<ConsentType, Boolean> consents = consentPolicy.validate(rawConsents, behaviorPersonalizationEnabled);
		Instant now = clock.instant();
		AppUser user = userRepository.save(AppUser.register(
				normalizeDisplayName(displayName),
				LanguageNormalizer.normalize(language), now,
				properties.getAgeGatePolicyVersion(),
				behaviorPersonalizationEnabled ? PersonalizationMode.BEHAVIOR_ENABLED : PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE));
		consents.forEach((type, granted) -> consentRepository.save(UserConsent.decide(user, type,
				Boolean.TRUE.equals(granted) ? ConsentStatus.GRANTED : ConsentStatus.REVOKED,
				properties.getConsentPolicyVersion())));
		return user;
	}

	private LoggedIn issue(AppUser user, String deviceId, String email) {
		AuthTokenService.IssuedTokens tokens = (email == null)
				? tokenService.issue(user, deviceId)
				: tokenService.issue(user, deviceId, email);
		return new LoggedIn(tokens, user, email);
	}

	private AuthException identityTaken() {
		return new AuthException("OAUTH_IDENTITY_TAKEN", "이 소셜 계정은 이미 다른 계정에 연결돼 있어요.", HttpStatus.CONFLICT);
	}

	private String normalizeEmailOrNull(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		String normalized = normalizeEmail(email);
		if (normalized.length() > 254) {
			throw new AuthException("PROVIDER_EMAIL_INVALID", "소셜 계정 이메일 형식이 올바르지 않습니다.",
					HttpStatus.UNPROCESSABLE_CONTENT);
		}
		return normalized;
	}

	/** 규칙은 {@link EmailNormalizer} 하나만 쓴다 — 로컬 가입과 소셜 연결이 다르게 정규화하면 같은 사람이 두 계정이 된다. */
	private String normalizeEmail(String email) {
		return EmailNormalizer.normalize(email);
	}

	private String normalizeDisplayName(String displayName) {
		String value = displayName == null || displayName.isBlank() ? "GABOLLE 사용자" : displayName.trim();
		return value.codePoints().limit(50)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
				.toString();
	}
}
