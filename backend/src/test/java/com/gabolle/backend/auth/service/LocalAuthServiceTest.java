package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthIdentity;
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
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceTest {

	@Mock
	private AppUserRepository userRepository;

	@Mock
	private UserConsentRepository consentRepository;

	@Mock
	private LocalCredentialRepository credentialRepository;

	// S15P21E201-742 — 이메일 중복을 볼 때 소셜 계정도 함께 본다. 소셜로 가입한 이메일로
	// 비밀번호 회원가입을 하면 계정이 하나 더 생기던 것을 막는 의존성이다.
	@Mock
	private AuthIdentityRepository identityRepository;

	@Mock
	private AuthOneTimeTokenRepository oneTimeTokenRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthTokenService authTokenService;

	@Mock
	private EmailSender emailSender;

	@Mock private LoginAttemptGuard loginAttemptGuard;

	// S15P21E201-682 — LocalAuthService 가 로그인 실패·잠금을 구조화된 로그로 남기도록 이 의존성이
	// 추가됐다. 로깅 자체는 SecurityEventLoggerTest 등 common/security 쪽 테스트가 검증하므로
	// 여기서는 생성자 호출을 컴파일되게 유지하는 목적의 mock 이다.
	@Mock private SecurityEventLogger securityEventLogger;

	// S15P21E201-317 — 가입 시 익명 여행 승계 의존성. 세션 토큰을 안 보내는 기존 테스트들은
	// 이 둘을 그냥 안 부르므로(resolve 호출 자체가 없다) 별도 스텁이 필요 없다.
	@Mock private AnonymousSessionService anonymousSessionService;
	@Mock private AnonymousTripClaimService anonymousTripClaimService;

	private LocalAuthService service;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties();
		service = new LocalAuthService(userRepository, consentRepository, credentialRepository, identityRepository,
				oneTimeTokenRepository, passwordEncoder,
				new SessionTokenGenerator(), authTokenService, emailSender, properties,
				new ConsentPolicy(), loginAttemptGuard, securityEventLogger,
				anonymousSessionService, anonymousTripClaimService,
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void signupCreatesPendingCredentialAndVerificationToken() {
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		AppUser user = AppUser.register("여행자", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.PENDING_EMAIL_VERIFICATION);
		when(userRepository.save(any(AppUser.class))).thenReturn(user);
		when(passwordEncoder.encode("Route!2026")).thenReturn("bcrypt-hash");
		when(credentialRepository.save(any(LocalCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(oneTimeTokenRepository.save(any(AuthOneTimeToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LocalAuthService.Registration result = service.register(new AuthCommands.Register(" Traveler@Example.COM ",
				"Route!2026", " 여행자 ", "KO", true, "device-1", requiredConsents(), false));

		assertThat(result.email()).isEqualTo("traveler@example.com");
		assertThat(result.status()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
		verify(passwordEncoder).encode("Route!2026");
		verify(emailSender).sendEmailVerification(any(), any());
	}

	@Test
	void rejectsMissingRequiredConsent() {
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(new AuthCommands.Register("traveler@example.com", "Route!2026",
				"여행자", "KO", true, null, Map.of("TERMS_OF_SERVICE", true), false)))
				.isInstanceOf(AuthException.class).hasMessageContaining("필수 약관");
		verify(userRepository, never()).save(any(AppUser.class));
	}

	@Test
	void resendsVerificationWithoutRevealingUnknownEmail() {
		AppUser user = AppUser.register("여행자", "KO", Instant.now(), "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.PENDING_EMAIL_VERIFICATION);
		LocalCredential credential = LocalCredential.create(user, "traveler@example.com", "bcrypt-hash");
		when(credentialRepository.findByEmailForUpdate("traveler@example.com")).thenReturn(Optional.of(credential));
		when(oneTimeTokenRepository.findTopByLocalCredentialAndPurposeOrderByCreatedAtDesc(
				credential, AuthTokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.empty());
		when(oneTimeTokenRepository.findAllByLocalCredentialAndPurposeAndConsumedAtIsNull(
				credential, AuthTokenPurpose.EMAIL_VERIFICATION)).thenReturn(List.of());
		when(oneTimeTokenRepository.save(any(AuthOneTimeToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.resendEmailVerification(" Traveler@Example.COM ");
		service.resendEmailVerification("unknown@example.com");

		verify(emailSender).sendEmailVerification(any(), any());
	}

	/**
	 * S15P21E201-742 — 소셜로 가입한 이메일로 비밀번호 회원가입을 하면 막힌다.
	 *
	 * <p>🔴 예전에는 {@code local_credential} 만 봐서 이 자리가 뚫려 있었다. 구글로 가입한 사람은
	 * 비밀번호 계정이 없으므로 같은 주소로 회원가입이 그대로 통과했고, <b>같은 사람에게 계정이
	 * 하나 더 생겼다.</b> 사용자 눈에는 "가입했는데 내 여행이 없다" 로 보인다.
	 *
	 * <p>신원 객체를 mock 으로 쓰는 이유 — 이 테스트가 재는 것은 "찾았을 때 막는가" 이지 신원의
	 * 내용이 아니다. 실물을 만들면 {@code AuthIdentity} 의 생성자가 바뀔 때마다 이 테스트가
	 * 무관한 이유로 깨진다.
	 */
	@Test
	void 소셜로_가입한_이메일이면_비밀번호_가입을_막는다() {
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of(mock(AuthIdentity.class)));

		assertThatThrownBy(() -> service.register(new AuthCommands.Register("traveler@example.com", "Route!2026",
				"여행자", "KO", true, "device-1", requiredConsents(), false)))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("이미 가입된 이메일")
				// 🔴 어느 소셜인지가 새면 아무나 이메일을 넣어 보며 그 사람이 무엇을 쓰는지 알아낸다.
				.hasMessageNotContainingAny("구글", "카카오", "네이버", "GOOGLE", "KAKAO", "NAVER");

		verify(userRepository, never()).save(any(AppUser.class));
		verify(credentialRepository, never()).save(any(LocalCredential.class));
	}

	/**
	 * 대소문자와 앞뒤 공백만 다른 주소도 같은 것으로 본다 — S15P21E201-742.
	 *
	 * <p>🔴 이 검사가 지키는 것은 <b>두 저장소가 같은 정규화를 쓴다</b> 는 전제다. 소셜 쪽 이메일도
	 * 같은 규칙으로 소문자로 내려 저장되므로 대조가 성립한다. 한쪽만 규칙이 바뀌면 이 자리는
	 * 빨개지지 않고 <b>그냥 아무것도 안 잡는다</b> — 조용히 뚫리는 종류라 검사로 못 박는다.
	 */
	@Test
	void 대소문자만_다른_주소도_같은_계정으로_본다() {
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of(mock(AuthIdentity.class)));

		assertThatThrownBy(() -> service.register(new AuthCommands.Register(" Traveler@Example.COM ", "Route!2026",
				"여행자", "KO", true, "device-1", requiredConsents(), false)))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("이미 가입된 이메일");

		verify(userRepository, never()).save(any(AppUser.class));
	}

	/**
	 * 연결이 끊긴 소셜만 있으면 가입을 막지 않는다 — S15P21E201-742.
	 *
	 * <p>끊긴 신원은 더 이상 로그인 경로가 아니다. 그것 때문에 가입을 막으면 <b>아무도 못 쓰는
	 * 이메일</b>이 생긴다. 저장소 질의가 끊긴 행을 걸러 주므로 여기서는 걸러진 결과가 비었을 때
	 * 가입이 끝까지 가는지를 본다.
	 */
	@Test
	void 연결이_끊긴_소셜만_있으면_가입은_된다() {
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());
		when(identityRepository.findAllByProviderEmailAndUnlinkedAtIsNull("traveler@example.com"))
				.thenReturn(List.of());
		AppUser user = AppUser.register("여행자", "KO", Instant.parse("2026-01-01T00:00:00Z"), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.PENDING_EMAIL_VERIFICATION);
		when(userRepository.save(any(AppUser.class))).thenReturn(user);
		when(passwordEncoder.encode("Route!2026")).thenReturn("bcrypt-hash");
		when(credentialRepository.save(any(LocalCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(oneTimeTokenRepository.save(any(AuthOneTimeToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

		LocalAuthService.Registration result = service.register(new AuthCommands.Register("traveler@example.com",
				"Route!2026", "여행자", "KO", true, "device-1", requiredConsents(), false));

		assertThat(result.email()).isEqualTo("traveler@example.com");
	}

	@Test
	void rejectsDuplicateEmailAndAgeGate() {
		assertThatThrownBy(() -> service.register(new AuthCommands.Register("traveler@example.com", "Route!2026",
				"여행자", "KO", false, null))).isInstanceOf(AuthException.class)
				.hasMessageContaining("14세");
		verify(userRepository, never()).save(any(AppUser.class));
	}

	@Test
	void blocksLoginUntilEmailIsVerified() {
		AppUser user = AppUser.register("여행자", "KO", Instant.now(), "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.PENDING_EMAIL_VERIFICATION);
		LocalCredential credential = LocalCredential.create(user, "traveler@example.com", "bcrypt-hash");
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(credential));
		when(passwordEncoder.matches("Route!2026", "bcrypt-hash")).thenReturn(true);

		assertThatThrownBy(() -> service.login(new AuthCommands.Login("traveler@example.com", "Route!2026", "device-1")))
				.isInstanceOf(AuthException.class).hasMessageContaining("이메일 인증");
		verify(authTokenService, never()).issue(any(), any());
	}

	private Map<String, Boolean> requiredConsents() {
		return Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true);
	}
}
