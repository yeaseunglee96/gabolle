package com.gabolle.backend.auth.service;

import java.time.Clock;
import java.time.Instant;
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
 * 소셜 신원을 계정으로 잇는 규칙 — S15P21E201-689 · -690 (2026-09-07 재설계).
 *
 * <h2>예전과 달라진 것</h2>
 * 예전 {@code loginOrRegister} 는 처음 보는 소셜 신원이면 <b>그 자리에서</b> 계정을 만들었다. 그래서 앱은 소셜 버튼을
 * 누르기 전에 14세 확인·약관 동의를 다 받아 둬야 했고, 사용자는 닉네임을 고를 자리가 없었다. 이제 {@link #authenticate}
 * 는 셋 중 하나로 답한다.
 * <ul>
 *   <li>{@link LoggedIn} — 이미 붙어 있는 소셜 신원. 동의를 다시 묻지 않는다.</li>
 *   <li>{@link SignupRequired} — 처음 보는 신원. 계정을 만들지 않고 10분짜리 가입 티켓과 미리 채울 값을 준다.
 *       {@link #completeSignup} 이 그 티켓으로 계정을 만든다.</li>
 *   <li>{@link OAuthLinkRequiredException}(409) — 같은 이메일의 로컬 계정이 있다. 연결 티켓을 싣고, {@link #linkWithPassword}
 *       가 비밀번호를 확인한 뒤 붙인다. 이메일이 같다고 자동으로 붙이지 않는 이유는 {@code DEC-AUTH-010} 이 소유한다.</li>
 * </ul>
 *
 * <p>🔴 <b>옛 앱과의 호환.</b> 지금 배포된 앱은 첫 요청에 14세 확인과 동의를 함께 보낸다. 그 요청이 오면 예전처럼
 * 한 번에 계정을 만들어 {@link LoggedIn} 으로 답한다({@link #authenticate} 의 {@code oneStep}). 동의가 없으면 티켓이다.
 *
 * <p>🔴 <b>이메일 없는 소셜 계정.</b> 카카오 기본 동의는 이메일을 주지 않는다. 예전에는 422 로 거절했는데, 이제 이메일
 * 없이 가입할 수 있다 — {@code auth_identity.provider_email} 이 원래 NULL 허용이다. 그 계정은 비밀번호 재설정 같은
 * 메일 기능을 쓸 수 없다는 점을 {@code Prefill.emailProvided=false} 로 화면에 알린다. 정책 결정은 사람 몫이라
 * {@code DEC-AUTH-011} 에 적어 두었다.
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
	 * 🔴 S15P21E201-682 후속 — 소셜 연결의 비밀번호 확인 실패가 어디에도 안 남고 있었다.
	 *
	 * <p>{@code GlobalAuthExceptionHandler} 는 상태 코드로 보고 남기는데, 로그인 전용 코드
	 * ({@code INVALID_CREDENTIALS}·{@code TOO_MANY_LOGIN_ATTEMPTS})는 "던지는 지점에서 이미
	 * 더 정확하게 남긴다" 는 이유로 건너뛴다. 그 전제가 {@code LocalAuthService} 에서만 참이었고
	 * 이 클래스에는 그 로깅이 없어서, 소셜 연결 화면을 통한 비밀번호 시도는 통째로 사각지대였다.
	 * 그래서 여기서도 같은 방식으로 남긴다 — {@code null} 을 허용하지 않는다. 널을 허용하면
	 * 배선이 빠진 것을 아무도 모르고, 그것이 정확히 이 필드가 막으려는 상황이다.
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
	 * <p>🔴 연결 필요를 <b>예외가 아니라 반환값</b>으로 둔 이유. 처음에는 409 예외로 던졌는데, 그러면 이 메서드의
	 * 트랜잭션이 되돌려지면서 방금 만든 연결 티켓의 INSERT 도 함께 사라진다 — 클라이언트는 DB 에 없는 티켓을 받고,
	 * 그것으로 연결을 시도하면 "만료됐다" 가 온다. 통합 테스트가 티켓 행 수를 세다가 잡았다. 정상 흐름의 한 갈래를
	 * 예외로 표현하면 트랜잭션 경계와 싸우게 된다.
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
		LinkRequired link = linkRequiredIfLocalAccountExists(provider, profile.subject(), email, deviceId);
		if (link != null) {
			return link;
		}

		boolean oneStep = ageGateAccepted && rawConsents != null && !rawConsents.isEmpty();
		if (oneStep) {
			// 🔴 옛 앱 호환 — 동의와 14세 확인을 첫 요청에 실어 보낸 경우는 예전처럼 그 자리에서 계정을 만든다.
			AppUser user = register(profile.displayName(), profile.language(), rawConsents, behaviorPersonalizationEnabled);
			identityRepository.save(AuthIdentity.link(user, provider, profile.subject(), email));
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
	 * <p>티켓이 발급된 뒤 같은 사람이 다른 기기에서 먼저 가입했을 수 있다. 그래서 신원이 이미 있으면 새로 만들지 않고
	 * 그 계정으로 로그인시키고, 같은 이메일의 로컬 계정이 그 사이 생겼으면 {@link LinkRequired} 로 돌린다 —
	 * {@link #authenticate} 와 같은 판정을 티켓 소비 뒤에 한 번 더 한다.
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
		LinkRequired link = linkRequiredIfLocalAccountExists(ticket.getProvider(), ticket.getProviderSubject(),
				ticket.getProviderEmail(), deviceId);
		if (link != null) {
			return link;
		}

		String finalName = (displayName == null || displayName.isBlank()) ? ticket.getDisplayName() : displayName;
		String finalLanguage = (language == null || language.isBlank()) ? ticket.getLanguage() : language;
		AppUser user = register(finalName, finalLanguage, rawConsents, behaviorPersonalizationEnabled);
		identityRepository.save(AuthIdentity.link(user, ticket.getProvider(), ticket.getProviderSubject(),
				ticket.getProviderEmail()));
		return issue(user, deviceId, ticket.getProviderEmail());
	}

	/**
	 * 연결 티켓 + 비밀번호로 기존 계정에 소셜 신원을 붙인다 — S15P21E201-690.
	 *
	 * <p>비밀번호 판정은 로그인({@code LocalAuthService.login})과 같은 규칙을 지킨다 — 잠긴 계정은 맞는 비밀번호도 거부하고,
	 * 틀리면 실패 횟수를 올린다. 이 경로를 비밀번호 추측의 우회로로 쓰지 못하게 하려는 것이다.
	 *
	 * <p>🔴 틀린 비밀번호로 예외가 나가면 이 트랜잭션이 되돌려지므로 <b>티켓 소비도 함께 취소된다</b> — 사용자는 같은
	 * 티켓으로 다시 시도할 수 있다(만료·잠금 전까지). 그것이 의도다. 실패 횟수 기록은 {@link LoginAttemptGuard} 가
	 * 별도 트랜잭션에서 하므로 되돌려지지 않는다.
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
			// 🔴 세는 것과 남기는 것을 같은 자리에서 한다. recordFailure 가 돌려주는 값이
			//    이번 실패까지 포함한 횟수라, 이 줄이 LocalAuthService 와 같은 정확도를 갖는다.
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
	 * 로그인한 계정에 소셜 신원을 붙인다 — 설정 화면 (S15P21E201-690). 이미 같은 계정에 붙어 있으면 그대로 돌려준다.
	 *
	 * @throws AuthException {@code OAUTH_IDENTITY_TAKEN}(409) 그 소셜 신원이 다른 계정에 붙어 있다
	 */
	@Transactional
	public LinkedIdentity linkAuthenticated(UUID userId, AuthProvider provider, OAuthProviderClient.OAuthUserProfile profile) {
		AppUser user = userRepository.findById(userId)
				.orElseThrow(() -> new AuthException("ACCOUNT_UNAVAILABLE", "사용할 수 없는 계정입니다.", HttpStatus.FORBIDDEN));
		Optional<AuthIdentity> existing = identityRepository.findByProviderAndProviderSubject(provider, profile.subject());
		if (existing.isPresent()) {
			if (existing.get().getUser().getUserId().equals(userId) && existing.get().isActive()) {
				return new LinkedIdentity(existing.get(), true);
			}
			throw identityTaken();
		}
		return new LinkedIdentity(identityRepository.save(AuthIdentity.link(user, provider, profile.subject(),
				normalizeEmailOrNull(profile.email()))), false);
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
		return issue(identity.getUser(), deviceId, email);
	}

	/**
	 * 같은 이메일의 로컬 계정이 있으면 연결 티켓을 만들어 {@link LinkRequired} 를 돌려준다. 없으면 {@code null} 이고
	 * 호출자는 그대로 진행한다. 이메일을 안 주는 provider 는 겹칠지 볼 수가 없으니 질의도 하지 않는다.
	 */
	private LinkRequired linkRequiredIfLocalAccountExists(AuthProvider provider, String subject, String email,
			String deviceId) {
		if (email == null) {
			return null;
		}
		Optional<LocalCredential> local = credentialRepository.findByEmail(email);
		if (local.isEmpty()) {
			return null;
		}
		OAuthSignupTicketService.IssuedTicket ticket = ticketService.issueLink(provider, subject, email,
				local.get().getUser(), deviceId);
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
