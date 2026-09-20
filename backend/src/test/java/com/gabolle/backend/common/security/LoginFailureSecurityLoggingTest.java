package com.gabolle.backend.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.AuthTokenService;
import com.gabolle.backend.auth.service.ConsentPolicy;
import com.gabolle.backend.auth.service.EmailSender;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.service.LoginAttemptGuard;
import com.gabolle.backend.auth.service.SessionTokenGenerator;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 로그인 연속 실패가 mock 이 아닌 진짜 {@link SecurityEventLogger} 로 남는지 본다. 관심사는 로깅이
 * 올바른 시점에 올바른 attempts 값으로 나가는가이므로 리포지토리는 mock 이면 충분하다 — 트랜잭션이
 * 되돌려져도 실패 횟수가 남는지는 {@code LoginAttemptLimitIntegrationTest} 가 진짜 DB 로 본다.
 */
@ExtendWith(MockitoExtension.class)
class LoginFailureSecurityLoggingTest {

	private static final String EMAIL = "traveler@example.com";
	private static final String RIGHT_PASSWORD = "Route!2026";
	private static final String WRONG_PASSWORD = "WrongRoute!2026";

	@Mock private AppUserRepository userRepository;
	@Mock private UserConsentRepository consentRepository;
	@Mock private LocalCredentialRepository credentialRepository;
	@Mock private AuthIdentityRepository identityRepository;
	@Mock private AuthOneTimeTokenRepository oneTimeTokenRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private AuthTokenService authTokenService;
	@Mock private EmailSender emailSender;
	@Mock private com.gabolle.backend.auth.service.AnonymousSessionService anonymousSessionService;
	@Mock private com.gabolle.backend.trip.application.AnonymousTripClaimService anonymousTripClaimService;

	private LocalAuthService localAuthService;
	private Logger logbackLogger;
	private ListAppender<ILoggingEvent> appender;

	private Level originalLevel;

	@BeforeEach
	void setUp() {
		this.logbackLogger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
		// 수준을 명시한다. 앞선 Spring 테스트가 로그백을 재설정하면 아무것도 안 잡혀,
		// 아래 확인들이 빈 목록을 훑고 조용히 통과한다.
		this.originalLevel = this.logbackLogger.getLevel();
		this.logbackLogger.setLevel(Level.INFO);
		this.appender = new ListAppender<>();
		this.appender.start();
		this.logbackLogger.addAppender(this.appender);

		AuthProperties authProperties = new AuthProperties(); // loginFailureThreshold 기본값 5
		LoginAttemptGuard loginAttemptGuard = new LoginAttemptGuard(this.credentialRepository, authProperties);

		SecurityAlertProperties alertProperties = new SecurityAlertProperties(); // webhookUrl 기본 빈 값
		SecurityAlertNotifier alertNotifier = new SecurityAlertNotifier(alertProperties, RestClient.builder(),
				Clock.systemUTC());
		SecurityEventLogger securityEventLogger = new SecurityEventLogger(alertNotifier);

		// LocalAuthService 의 Clock 주입 생성자는 패키지 접근이라 여기서는 못 쓴다.
		// 이 테스트는 시각 자체를 검증하지 않으므로 공개 생성자로 충분하다.
		this.localAuthService = new LocalAuthService(this.userRepository, this.consentRepository,
				this.credentialRepository, this.identityRepository, this.oneTimeTokenRepository, this.passwordEncoder,
				new SessionTokenGenerator(), this.authTokenService, this.emailSender, authProperties,
				new ConsentPolicy(), loginAttemptGuard, securityEventLogger,
				this.anonymousSessionService, this.anonymousTripClaimService);
	}

	@AfterEach
	void tearDown() {
		this.logbackLogger.detachAppender(this.appender);
		this.logbackLogger.setLevel(this.originalLevel);
	}

	@Test
	@DisplayName("🔴 완료 기준 — 로그인 5회 연속 실패가 로그에 남고, 다섯 번째 줄의 attempts 가 5다")
	void fifthConsecutiveFailureLogsAttemptsFive() {
		LocalCredential credential = activeVerifiedCredential();
		// credential 은 저장한 적이 없어 id 가 아직 null 이다. findById 를 any() 로 받아 항상 같은
		// 인스턴스를 돌려줘야 "다시 읽으면 상태가 이어지는가" 를 잴 수 있다.
		when(this.credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential));
		when(this.credentialRepository.findById(any())).thenReturn(Optional.of(credential));
		when(this.passwordEncoder.matches(WRONG_PASSWORD, credential.getPasswordHash())).thenReturn(false);

		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		List<String> failureLines = this.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter(message -> message.contains("event=AUTH_LOGIN_FAILURE")).toList();
		assertThat(failureLines).hasSize(5);
		assertThat(failureLines.get(4)).as("다섯 번째 줄").contains("attempts=5");
	}

	@Test
	@DisplayName("🔴 완료 기준 — 잠기면 AUTH_ACCOUNT_LOCKED 가 남는다")
	void lockingLogsAccountLockedEvent() {
		LocalCredential credential = activeVerifiedCredential();
		when(this.credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential));
		when(this.credentialRepository.findById(any())).thenReturn(Optional.of(credential));
		when(this.passwordEncoder.matches(WRONG_PASSWORD, credential.getPasswordHash())).thenReturn(false);

		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		List<ILoggingEvent> lockedEvents = this.appender.list.stream()
				.filter(event -> event.getFormattedMessage().contains("event=AUTH_ACCOUNT_LOCKED")).toList();
		assertThat(lockedEvents).hasSize(1);
		assertThat(lockedEvents.get(0).getLevel()).isEqualTo(Level.WARN);
		assertThat(lockedEvents.get(0).getFormattedMessage()).contains("attempts=5");
	}

	@Test
	@DisplayName("🔴 잠긴 뒤에 또 두드리면 AUTH_LOCKED_ACCOUNT_ATTEMPT 가 남는다 — 잠금이 값을 내는지 보려면 이것이 필요하다")
	void attemptsAfterLockAreRecordedSeparately() {
		LocalCredential credential = activeVerifiedCredential();
		when(this.credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential));
		when(this.credentialRepository.findById(any())).thenReturn(Optional.of(credential));
		when(this.passwordEncoder.matches(WRONG_PASSWORD, credential.getPasswordHash())).thenReturn(false);

		// 다섯 번으로 잠그고, 잠긴 뒤에 두 번 더 두드린다.
		for (int attempt = 1; attempt <= 7; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		List<String> lockedAttempts = this.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter(message -> message.contains("event=AUTH_LOCKED_ACCOUNT_ATTEMPT")).toList();
		assertThat(lockedAttempts)
				.withFailMessage("잠긴 뒤의 시도가 로그에 없습니다 — 잠금이 공격을 막고 있는지 "
						+ "공격자가 이미 떠났는지 구분할 수 없습니다.")
				.hasSize(2);
		assertThat(lockedAttempts.get(0)).contains("lockedForSeconds=").contains("outcome=REJECTED");

		// 잠긴 뒤의 시도는 실패 횟수를 더 올리지 않으므로 AUTH_LOGIN_FAILURE 는 다섯 줄에서 멈춘다.
		List<String> failures = this.appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.filter(message -> message.contains("event=AUTH_LOGIN_FAILURE")).toList();
		assertThat(failures).hasSize(5);
	}

	@Test
	@DisplayName("네 번만 틀리면 아직 AUTH_ACCOUNT_LOCKED 가 없다")
	void fourFailuresDoNotLogAccountLocked() {
		LocalCredential credential = activeVerifiedCredential();
		when(this.credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential));
		when(this.credentialRepository.findById(any())).thenReturn(Optional.of(credential));
		when(this.passwordEncoder.matches(WRONG_PASSWORD, credential.getPasswordHash())).thenReturn(false);

		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		boolean anyLocked = this.appender.list.stream()
				.anyMatch(event -> event.getFormattedMessage().contains("event=AUTH_ACCOUNT_LOCKED"));
		assertThat(anyLocked).isFalse();
	}

	@Test
	@DisplayName("🔴 완료 기준 — 이메일 원문도 비밀번호도 로그에 없다")
	void neitherEmailNorPasswordLeakIntoLogs() {
		LocalCredential credential = activeVerifiedCredential();
		when(this.credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential));
		when(this.credentialRepository.findById(any())).thenReturn(Optional.of(credential));
		when(this.passwordEncoder.matches(WRONG_PASSWORD, credential.getPasswordHash())).thenReturn(false);

		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		assertThat(this.appender.list).allSatisfy(event -> {
			String message = event.getFormattedMessage();
			assertThat(message).doesNotContain(EMAIL).doesNotContain("traveler").doesNotContain(WRONG_PASSWORD);
		});
	}

	@Test
	@DisplayName("🔴 가입되지 않은 이메일로 실패해도 로그에 남는다 — 넓게 뿌리는 공격이 여기로 들어온다")
	void unknownEmailFailureIsLogged() {
		// 그 이메일로 가입한 계정이 없다
		when(this.credentialRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.localAuthService.login(
				new AuthCommands.Login("nobody@example.com", WRONG_PASSWORD, "device-test")))
				.isInstanceOfSatisfying(AuthException.class,
						e -> assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));

		List<String> messages = this.appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(messages).isNotEmpty();
		assertThat(messages).anySatisfy(message -> assertThat(message)
				.contains("event=AUTH_LOGIN_FAILURE")
				// 실패 횟수를 지어내지 않는다 — 계정이 없으면 셀 행이 없다
				.contains("accountExists=false")
				.doesNotContain("attempts="));
	}

	@Test
	@DisplayName("가입되지 않은 이메일도 원문이 로그에 없다 — 해시로만 남는다")
	void unknownEmailIsHashedInLogs() {
		when(this.credentialRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.localAuthService.login(
				new AuthCommands.Login("nobody@example.com", WRONG_PASSWORD, "device-test")))
				.isInstanceOf(AuthException.class);

		// 이 줄이 없으면 아무것도 안 잡혔을 때 아래 for 문이 한 번도 안 돌고 조용히 통과한다.
		assertThat(this.appender.list).isNotEmpty();
		for (ILoggingEvent event : this.appender.list) {
			assertThat(event.getFormattedMessage())
					.doesNotContain("nobody@example.com")
					.doesNotContain("nobody")
					.doesNotContain(WRONG_PASSWORD);
		}
	}

	@Test
	@DisplayName("🔴 응답은 가입된 계정의 실패와 똑같다 — 그 이메일로 가입했는지 응답으로 알려주지 않는다")
	void responseIsIndistinguishableFromAWrongPassword() {
		// 없는 계정
		when(this.credentialRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
		AuthException unknownAccount = catchThrowableOfType(() -> this.localAuthService.login(
				new AuthCommands.Login("nobody@example.com", WRONG_PASSWORD, "device-test")), AuthException.class);

		// 있는 계정 + 틀린 비밀번호
		LocalCredential credential = activeVerifiedCredential();
		when(this.credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential));
		when(this.passwordEncoder.matches(WRONG_PASSWORD, credential.getPasswordHash())).thenReturn(false);
		AuthException wrongPassword = catchThrowableOfType(() -> login(WRONG_PASSWORD), AuthException.class);

		// 코드·메시지·상태가 모두 같아야 한다 — 로그만 갈라지고 응답은 갈라지지 않는다.
		assertThat(unknownAccount.getCode()).isEqualTo(wrongPassword.getCode());
		assertThat(unknownAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
		assertThat(unknownAccount.getStatus()).isEqualTo(wrongPassword.getStatus());
	}

	private void login(String password) {
		this.localAuthService.login(new AuthCommands.Login(EMAIL, password, "device-test"));
	}

	private LocalCredential activeVerifiedCredential() {
		AppUser user = AppUser.register("여행자", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		LocalCredential credential = LocalCredential.create(user, EMAIL, "bcrypt-hash-of-" + RIGHT_PASSWORD);
		credential.markEmailVerified(Instant.parse("2026-01-01T00:00:00Z"));
		return credential;
	}
}
