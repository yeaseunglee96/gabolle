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
 *   <li>{@link OAuthLinkRequiredException}(409) — 메일 인증을 안 끝낸 로컬 계정이 같은 이메일을 쓰고 있다. 연결 티켓을
 *       싣고, {@link #linkWithPassword} 가 비밀번호를 확인한 뒤 붙인다.</li>
 * </ul>
 *
 * <p><b>2026-09-14 (S15P21E201-923) — 같은 이메일이면 자동으로 붙인다.</b> 처음 보는 신원이라도 그 이메일을 이미
 * 쓰고 있는 계정이 있으면 새 계정을 만들지 않고 그 계정에 붙인 뒤 {@link LoggedIn} 으로 답한다. 그전까지는 제공자를
 * 바꿔 로그인할 때마다 계정이 갈라졌다. 고르는 기준과 붙이지 않는 경우는 {@link #autoLinkTarget} 이 소유한다.
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
			// 🔴 옛 앱 호환 — 동의와 14세 확인을 첫 요청에 실어 보낸 경우는 예전처럼 그 자리에서 계정을 만든다.
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
		// 🔴 S15P21E201-741 — 이 경로만 이메일 신뢰도를 못 남긴다. 가입 티켓에는 주소만 실려 있고
		//    "검증됐나·아직 유효한가" 는 provider 응답에만 있었는데 그 응답은 티켓을 발급할 때
		//    이미 지나갔다. 그래서 여기서 만들어진 신원은 두 값이 null(모름)로 시작하고,
		//    그 사람이 다음에 로그인할 때 refreshProviderEmail 이 채운다.
		//
		//    지금은 이것이 안전한 쪽으로 틀린다 — 판정 규칙이 "명시적으로 유효하지 않다고
		//    답한 경우에만 배제" 이므로 모름은 배제되지 않는다. 티켓에 두 칸을 더하면 한 번에
		//    채울 수 있지만, 그러려면 티켓 표에 칸을 늘려야 해서 이번 범위 밖으로 뒀다.
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
			AuthIdentity identity = existing.get();
			if (identity.isActive()) {
				if (identity.getUser().getUserId().equals(userId)) {
					return new LinkedIdentity(identity, true);
				}
				throw identityTaken();
			}
			// 🔴 끊긴 연결은 누구의 것도 아니다 — 다시 붙인다 (S15P21E201-1317). 여기서 거절하면
			//    한 번 뗀 소셜 계정은 영영 다시 못 붙는다. 그 거절은 「이미 다른 계정에 연결돼
			//    있어요」라고 말하는데 사실과도 다르다.
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
	 * 로그인할 때마다 provider 가 준 이메일과 그 신뢰도를 저장된 값에 다시 쓴다 — S15P21E201-741.
	 *
	 * <p>🔴 <b>예전에는 처음 연결할 때 한 번 쓰고 다시 안 봤다.</b> 그래서 사용자가 소셜에서 이메일을
	 * 바꾸면 우리 쪽에는 옛 주소가 그대로 남았다. 그 값을 계정 연결 판정에 쓰기 시작하면
	 * <b>이미 그 사람의 것이 아닌 주소로 판단하게 된다.</b>
	 *
	 * <p>🔴 카카오는 그 주소가 <b>다른 카카오계정으로 옮겨가면</b> 유효하지 않다고 답한다
	 * ({@code is_email_valid=false}). 그 신호를 갱신하지 않으면 옮겨간 뒤에도 우리는 계속 유효한
	 * 줄 안다 — 그때 이메일로 계정을 이으면 <b>엉뚱한 사람에게 이어진다.</b> 갱신이 이 판정의
	 * 전제다.
	 *
	 * <p>🔴 <b>이메일이 안 왔으면 아무것도 지우지 않는다.</b> 카카오는 동의 상태에 따라 이메일을
	 * 아예 안 줄 수 있는데, 그때 저장된 값을 {@code null} 로 덮으면 <b>있던 정보가 조용히
	 * 사라진다.</b> 안 온 것과 없어진 것은 다르다.
	 *
	 * <p>🔴 <b>저장소가 돌려주는 값에 기대지 않는다.</b> 처음에 {@code save(...)} 의 반환값을 받아
	 * 거기에 값을 썼더니 옛 앱 호환 경로가 {@link NullPointerException} 으로 죽었다 — 그 자리를
	 * 재는 테스트의 mock 이 {@code null} 을 돌려주기 때문이다. 운영에서는 JPA 가 엔티티를
	 * 돌려주므로 안 드러나고 <b>테스트에서만 죽는</b> 조합이었다. 값을 먼저 채우고 그 객체를
	 * 저장하면 반환값이 무엇이든 상관없다.
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
	 * 이 이메일을 이미 쓰고 있는 계정 — 있으면 새 계정을 만들지 않고 거기에 신원을 붙인다 (S15P21E201-923).
	 *
	 * <p>계정을 {@code (provider, provider_subject)} 로만 찾던 동안은 같은 사람이 제공자를 바꿔 로그인할 때마다
	 * 계정이 하나씩 늘었다. 한 사람의 같은 주소로 네이버·카카오·애플에 계정이 세 개 생긴 것을 실제로 확인했고,
	 * 그 사람이 한쪽에서 만든 여행은 다른 쪽에서 보이지 않는다. {@code DEC-AUTH-010} 이 자동 연결을 막고 있었으나
	 * 그 근거(남의 주소로 소셜 계정을 만들면 비밀번호 없이 들어온다)는 로컬 가입이 메일 인증을 거치도록 바뀌면서
	 * 절반이 사라졌다 — 비밀번호 계정의 주소는 우리가 확인한 주소다.
	 *
	 * <p>고르는 순서는 <b>확인된 비밀번호 계정이 먼저</b>고, 없으면 그 주소로 연결돼 있는 소셜 신원 가운데 가장 먼저
	 * 가입한 계정이다. 같은 주소로 이미 갈라져 있는 계정들을 합치지는 않으므로 <b>어느 하나를 골라야 하고</b>, 그 기준이
	 * 호출 순서나 DB 반환 순서에 달려 있으면 같은 사람이 같은 조건에서 다른 계정에 붙는다.
	 *
	 * <p>붙이지 않는 경우가 셋이다. 메일 인증을 안 끝낸 비밀번호 계정은 그 주소가 그 사람 것인지 우리가 확인하지
	 * 못했으므로 예전처럼 비밀번호를 묻는다. 카카오가 유효하지 않다고 답한 주소({@code email_valid=false})는 이미 다른
	 * 카카오계정으로 옮겨갔을 수 있어 그대로 믿으면 <b>엉뚱한 사람에게 이어진다.</b> 그리고 대상 계정에 같은 제공자가
	 * 이미 붙어 있으면 같은 주소를 든 <b>다른</b> 계정이 온 것이라 자동으로 처리할 자리가 아니다.
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
