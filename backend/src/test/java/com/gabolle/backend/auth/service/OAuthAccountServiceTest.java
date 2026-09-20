package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gabolle.backend.common.security.SecurityEventLogger;
import com.gabolle.backend.common.security.SecurityAlertProperties;
import com.gabolle.backend.common.security.SecurityAlertNotifier;
import com.gabolle.backend.auth.api.OAuthLoginResponse;
import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.domain.OAuthSignupTicket;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

/** 소셜 인증이 세 갈래로 갈리는 규칙. 처음 보는 신원이어도 그 자리에서 계정을 만들지 않는다. */
@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

	@Mock private AuthIdentityRepository identityRepository;
	@Mock private LocalCredentialRepository credentialRepository;
	@Mock private AppUserRepository userRepository;
	@Mock private UserConsentRepository consentRepository;
	@Mock private AuthTokenService tokenService;
	@Mock private OAuthSignupTicketService ticketService;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private LoginAttemptGuard loginAttemptGuard;

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	private OAuthAccountService service;
	private ch.qos.logback.classic.Logger logbackLogger;
	private ListAppender<ILoggingEvent> appender;
	private Level originalLevel;

	@BeforeEach
	void setUp() {
		// 수준을 명시하고 원래대로 되돌린다. 앞선 Spring 검사가 로그백을 재설정하면
		// 로깅 확인이 빈 목록을 훑고 조용히 통과한다.
		logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
		originalLevel = logbackLogger.getLevel();
		logbackLogger.setLevel(Level.INFO);
		appender = new ListAppender<>();
		appender.start();
		logbackLogger.addAppender(appender);

		service = new OAuthAccountService(identityRepository, credentialRepository, userRepository, consentRepository,
				tokenService, new AuthProperties(), new ConsentPolicy(), ticketService, passwordEncoder,
				loginAttemptGuard,
				new SecurityEventLogger(new SecurityAlertNotifier(new SecurityAlertProperties(),
						org.springframework.web.client.RestClient.builder(), Clock.systemUTC())),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("🔴 처음 보는 소셜 계정은 계정을 만들지 않고 가입 티켓과 미리 채울 값을 준다")
	void firstTimeSocialIdentityGetsSignupTicketNotAnAccount() {
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(ticketService.issueSignup(eq(AuthProvider.GOOGLE), eq("google-subject"), eq("traveler@example.com"),
				anyString(), anyString(), anyString()))
				.thenReturn(new OAuthSignupTicketService.IssuedTicket("raw-ticket", NOW.plusSeconds(600)));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("en-US"), "device-1",
				null, false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.SignupRequired.class);
		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) outcome;
		assertThat(signup.signupTicket()).isEqualTo("raw-ticket");
		assertThat(signup.email()).isEqualTo("traveler@example.com");
		assertThat(signup.displayName()).isEqualTo("여행자");
		assertThat(signup.language()).isEqualTo("EN");
		assertThat(signup.emailProvided()).isTrue();
		// 동의도 안 받았는데 계정이 생기면 안 된다.
		verify(userRepository, never()).save(any(AppUser.class));
		verify(identityRepository, never()).save(any(AuthIdentity.class));
	}

	@Test
	@DisplayName("이메일을 안 주는 provider 도 가입 티켓을 받는다 — 예전엔 422 로 막혔다")
	void providerWithoutEmailStillGetsSignupTicket() {
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, "kakao-subject"))
				.thenReturn(Optional.empty());
		when(ticketService.issueSignup(eq(AuthProvider.KAKAO), eq("kakao-subject"), eq(null), anyString(), anyString(),
				anyString())).thenReturn(new OAuthSignupTicketService.IssuedTicket("kakao-ticket", NOW.plusSeconds(600)));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.KAKAO,
				new OAuthProviderClient.OAuthUserProfile("kakao-subject", null, "카카오 사용자", "KO", null, null), "device-1",
				null, false, false);

		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) outcome;
		assertThat(signup.email()).isNull();
		assertThat(signup.emailProvided()).isFalse();
		// 이메일이 없으면 로컬 계정과 겹칠지 볼 수가 없다 — 그 질의를 하지 않는다.
		verify(credentialRepository, never()).findByEmail(anyString());
	}

	@AfterEach
	void detachAppender() {
		logbackLogger.detachAppender(appender);
		logbackLogger.setLevel(originalLevel);
	}

	@Test
	@DisplayName("🔴 소셜 연결에서 비밀번호가 틀리면 보안 로그에 남는다 — 여기가 사각지대였다")
	void wrongPasswordOnSocialLinkIsRecorded() {
		// 전역 예외 처리기는 INVALID_CREDENTIALS 를 던지는 지점에서 이미 남긴다고 보고
		// 건너뛴다. 그 전제는 LocalAuthService 에서만 참이라 여기서 직접 남긴다.
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		LocalCredential credential = mock(LocalCredential.class);
		when(credential.getEmail()).thenReturn("traveler@example.com");
		when(credential.isLoginLocked(NOW)).thenReturn(false);
		when(credential.getPasswordHash()).thenReturn("stored-hash");
		OAuthSignupTicket ticket = OAuthSignupTicket.forLink("hash", AuthProvider.GOOGLE, "google-subject",
				"traveler@example.com", owner, "device-1", NOW, NOW.plusSeconds(600));
		when(ticketService.consume("raw-ticket", OAuthSignupTicket.Kind.LINK)).thenReturn(ticket);
		when(credentialRepository.findByUserUserId(owner.getUserId())).thenReturn(Optional.of(credential));
		when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);
		when(loginAttemptGuard.recordFailure(any(), eq(NOW))).thenReturn(3);

		assertThatThrownBy(() -> service.linkWithPassword("raw-ticket", "wrong", "device-1"))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));

		List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter((message) -> message.contains("event=AUTH_LOGIN_FAILURE")).toList();
		assertThat(lines)
				.withFailMessage("소셜 연결의 비밀번호 실패가 로그에 없습니다. 배선이 빠지면 이 경로로 "
						+ "들어오는 시도가 통째로 안 보입니다.")
				.hasSize(1);
		assertThat(lines.get(0)).contains("attempts=3").contains("emailHash=");
		assertThat(lines.get(0)).doesNotContain("traveler@example.com").doesNotContain("wrong");
	}

	@Test
	@DisplayName("🔴 이미 잠긴 계정으로 소셜 연결을 시도하면 그것도 남는다")
	void lockedAccountOnSocialLinkIsRecorded() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		LocalCredential credential = mock(LocalCredential.class);
		when(credential.getEmail()).thenReturn("traveler@example.com");
		when(credential.isLoginLocked(NOW)).thenReturn(true);
		when(credential.getLoginLockedUntil()).thenReturn(NOW.plusSeconds(300));
		OAuthSignupTicket ticket = OAuthSignupTicket.forLink("hash", AuthProvider.GOOGLE, "google-subject",
				"traveler@example.com", owner, "device-1", NOW, NOW.plusSeconds(600));
		when(ticketService.consume("raw-ticket", OAuthSignupTicket.Kind.LINK)).thenReturn(ticket);
		when(credentialRepository.findByUserUserId(owner.getUserId())).thenReturn(Optional.of(credential));

		assertThatThrownBy(() -> service.linkWithPassword("raw-ticket", "whatever", "device-1"))
				.isInstanceOfSatisfying(AuthException.class,
						(e) -> assertThat(e.getCode()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS"));

		List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter((message) -> message.contains("event=AUTH_LOCKED_ACCOUNT_ATTEMPT")).toList();
		assertThat(lines).hasSize(1);
		assertThat(lines.get(0)).contains("lockedForSeconds=300");
		// 잠긴 동안에는 비밀번호를 보지 않으므로 실패 횟수를 올리지 않는다.
		verify(loginAttemptGuard, never()).recordFailure(any(), any());
	}

	@Test
	@DisplayName("🔴 메일 인증을 안 끝낸 로컬 계정과 같은 이메일이면 연결 티켓과 함께 409 다 — 여기는 비밀번호를 묻는다")
	void unverifiedLocalAccountStillRequiresPassword() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		LocalCredential credential = mock(LocalCredential.class);
		when(credential.getUser()).thenReturn(owner);
		// 인증 전에는 그 주소가 이 사람 것인지 확인되지 않았다. 자동 연결의 전제가 없다.
		when(credential.getEmailVerifiedAt()).thenReturn(null);
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(credential));
		when(ticketService.issueLink(eq(AuthProvider.GOOGLE), eq("google-subject"), eq("traveler@example.com"),
				eq(owner), anyString()))
				.thenReturn(new OAuthSignupTicketService.IssuedTicket("link-ticket", NOW.plusSeconds(600)));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1",
				requiredConsents(), false, true);

		assertThat(outcome).isInstanceOf(OAuthAccountService.LinkRequired.class);
		OAuthAccountService.LinkRequired link = (OAuthAccountService.LinkRequired) outcome;
		assertThat(link.linkTicket()).isEqualTo("link-ticket");
		assertThat(link.maskedEmail()).isEqualTo("t***@example.com");
		assertThat(link.provider()).isEqualTo(AuthProvider.GOOGLE);
		// 동의까지 함께 보냈어도 연결 필요가 먼저다 — 계정을 새로 만들지 않는다.
		verify(userRepository, never()).save(any(AppUser.class));
		verify(identityRepository, never()).save(any(AuthIdentity.class));
	}

	@Test
	@DisplayName("메일 인증을 끝낸 비밀번호 계정과 같은 이메일이면 비밀번호를 묻지 않고 그 계정에 붙는다")
	void attachesToVerifiedLocalAccountWithSameEmail() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		LocalCredential credential = mock(LocalCredential.class);
		when(credential.getUser()).thenReturn(owner);
		when(credential.getEmailVerifiedAt()).thenReturn(NOW);
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(credential));
		when(tokenService.issue(eq(owner), anyString(), anyString()))
				.thenReturn(mock(AuthTokenService.IssuedTokens.class));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", null,
				false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.LoggedIn.class);
		assertThat(((OAuthAccountService.LoggedIn) outcome).user()).isSameAs(owner);
		verify(userRepository, never()).save(any(AppUser.class));
		verify(identityRepository).save(any(AuthIdentity.class));
		verify(ticketService, never()).issueLink(any(), anyString(), anyString(), any(), anyString());
		verify(ticketService, never()).issueSignup(any(), anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("같은 이메일을 쓰는 다른 소셜 계정이 있으면 계정이 하나 더 생기지 않고 거기에 붙는다")
	void attachesToExistingSocialAccountWithSameEmail() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		AuthIdentity naver = AuthIdentity.link(owner, AuthProvider.NAVER, "naver-subject", "traveler@example.com");
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of(naver));
		when(identityRepository.findAllByUserUserId(owner.getUserId())).thenReturn(List.of(naver));
		when(tokenService.issue(eq(owner), anyString(), anyString()))
				.thenReturn(mock(AuthTokenService.IssuedTokens.class));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", null,
				false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.LoggedIn.class);
		assertThat(((OAuthAccountService.LoggedIn) outcome).user()).isSameAs(owner);
		verify(userRepository, never()).save(any(AppUser.class));
		verify(identityRepository).save(any(AuthIdentity.class));
	}

	@Test
	@DisplayName("유효하지 않다고 표시된 주소로는 안 붙는다 — 그 주소는 이미 남의 것일 수 있다")
	void doesNotAttachThroughAnEmailMarkedInvalid() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		AuthIdentity kakao = AuthIdentity.link(owner, AuthProvider.KAKAO, "kakao-subject", "traveler@example.com");
		// 카카오는 그 주소가 다른 카카오계정으로 옮겨가면 유효하지 않다고 답한다.
		kakao.recordProviderEmail("traveler@example.com", Boolean.TRUE, Boolean.FALSE);
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of(kakao));
		when(ticketService.issueSignup(eq(AuthProvider.GOOGLE), eq("google-subject"), eq("traveler@example.com"),
				anyString(), anyString(), anyString()))
				.thenReturn(new OAuthSignupTicketService.IssuedTicket("raw-ticket", NOW.plusSeconds(600)));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", null,
				false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.SignupRequired.class);
		verify(identityRepository, never()).save(any(AuthIdentity.class));
	}

	@Test
	@DisplayName("같은 제공자가 이미 붙어 있는 계정에는 안 붙는다 — 같은 주소를 든 다른 계정이다")
	void doesNotAttachWhenTheSameProviderIsAlreadyLinked() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		AuthIdentity alreadyGoogle = AuthIdentity.link(owner, AuthProvider.GOOGLE, "another-google-subject",
				"traveler@example.com");
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of(alreadyGoogle));
		when(identityRepository.findAllByUserUserId(owner.getUserId())).thenReturn(List.of(alreadyGoogle));
		when(ticketService.issueSignup(eq(AuthProvider.GOOGLE), eq("google-subject"), eq("traveler@example.com"),
				anyString(), anyString(), anyString()))
				.thenReturn(new OAuthSignupTicketService.IssuedTicket("raw-ticket", NOW.plusSeconds(600)));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", null,
				false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.SignupRequired.class);
		verify(identityRepository, never()).save(any(AuthIdentity.class));
	}

	@Test
	@DisplayName("쓸 수 없는 계정에는 안 붙는다 — 탈퇴한 계정이 같은 주소를 들고 있어도 새로 가입한다")
	void doesNotAttachToUnavailableAccount() {
		AppUser deleted = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.DELETED);
		AuthIdentity naver = AuthIdentity.link(deleted, AuthProvider.NAVER, "naver-subject", "traveler@example.com");
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of(naver));
		when(ticketService.issueSignup(eq(AuthProvider.GOOGLE), eq("google-subject"), eq("traveler@example.com"),
				anyString(), anyString(), anyString()))
				.thenReturn(new OAuthSignupTicketService.IssuedTicket("raw-ticket", NOW.plusSeconds(600)));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", null,
				false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.SignupRequired.class);
		verify(identityRepository, never()).save(any(AuthIdentity.class));
	}

	@Test
	@DisplayName("옛 앱처럼 14세 확인과 동의를 첫 요청에 실어 보내면 예전처럼 한 번에 가입한다")
	void oneStepSignupStillWorksForOlderClients() {
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		AppUser saved = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		when(userRepository.save(any(AppUser.class))).thenReturn(saved);
		when(tokenService.issue(eq(saved), anyString(), anyString())).thenReturn(mock(AuthTokenService.IssuedTokens.class));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1",
				requiredConsents(), false, true);

		assertThat(outcome).isInstanceOf(OAuthAccountService.LoggedIn.class);
		verify(identityRepository).save(any(AuthIdentity.class));
		verify(ticketService, never()).issueSignup(any(), anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("이미 붙어 있는 소셜 계정은 동의를 다시 묻지 않고 바로 로그인된다")
	void returningSocialUserLogsInWithoutConsents() {
		AppUser user = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		AuthIdentity identity = AuthIdentity.link(user, AuthProvider.GOOGLE, "google-subject", "traveler@example.com");
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.of(identity));
		when(tokenService.issue(eq(user), anyString(), anyString())).thenReturn(mock(AuthTokenService.IssuedTokens.class));

		OAuthAccountService.Outcome outcome = service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", null,
				false, false);

		assertThat(outcome).isInstanceOf(OAuthAccountService.LoggedIn.class);
		verify(ticketService, never()).issueSignup(any(), anyString(), anyString(), anyString(), anyString(), anyString());
		verify(userRepository, never()).save(any(AppUser.class));
	}

	@Test
	@DisplayName("쓸 수 없는 계정의 소셜 로그인은 막힌다")
	void blocksSocialLoginForUnavailableAccount() {
		AppUser suspended = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.DELETED);
		AuthIdentity identity = AuthIdentity.link(suspended, AuthProvider.GOOGLE, "google-subject",
				"traveler@example.com");
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.of(identity));

		assertThatThrownBy(() -> service.authenticate(AuthProvider.GOOGLE, profile("KO"), "device-1", requiredConsents(),
				false, true))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("사용할 수 없는 계정");
		verify(tokenService, never()).issue(any(AppUser.class), anyString(), anyString());
	}

	@Test
	@DisplayName("가려진 이메일은 첫 글자만 남긴다 — 한 글자 계정은 전부 가린다")
	void masksLocalPartOfEmail() {
		assertThat(OAuthLoginResponse.mask("traveler@example.com")).isEqualTo("t***@example.com");
		assertThat(OAuthLoginResponse.mask("a@example.com")).isEqualTo("*@example.com");
		assertThat(OAuthLoginResponse.mask("not-an-email")).isNull();
		assertThat(OAuthLoginResponse.mask(null)).isNull();
	}

	private OAuthProviderClient.OAuthUserProfile profile(String language) {
		return new OAuthProviderClient.OAuthUserProfile("google-subject", "traveler@example.com", "여행자", language,
				Boolean.TRUE, null);
	}

	private Map<String, Boolean> requiredConsents() {
		return Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true);
	}
}
