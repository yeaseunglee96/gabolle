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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gabolle.backend.auth.api.OAuthLoginResponse;
import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

/**
 * S15P21E201-689 · -690 — 소셜 인증이 세 갈래로 갈리는 규칙.
 *
 * <p>2026-09-07 재설계로 이 클래스가 보는 것이 바뀌었다. 예전에는 "처음 보는 신원이면 그 자리에서 계정을 만든다" 가
 * 규칙이었고 테스트도 그것을 고정했다. 지금은 <b>계정을 만들지 않는 것</b>이 규칙이라, 그 자리에 새 단정을 둔다.
 */
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

	@BeforeEach
	void setUp() {
		service = new OAuthAccountService(identityRepository, credentialRepository, userRepository, consentRepository,
				tokenService, new AuthProperties(), new ConsentPolicy(), ticketService, passwordEncoder,
				loginAttemptGuard, Clock.fixed(NOW, ZoneOffset.UTC));
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
		// 🔴 이것이 이 티켓의 핵심이다 — 동의도 안 받았는데 계정이 생기면 안 된다.
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
				new OAuthProviderClient.OAuthUserProfile("kakao-subject", null, "카카오 사용자", "KO"), "device-1", null,
				false, false);

		OAuthAccountService.SignupRequired signup = (OAuthAccountService.SignupRequired) outcome;
		assertThat(signup.email()).isNull();
		assertThat(signup.emailProvided()).isFalse();
		// 이메일이 없으면 로컬 계정과 겹칠지 볼 수가 없다 — 그 질의를 하지 않는다.
		verify(credentialRepository, never()).findByEmail(anyString());
	}

	@Test
	@DisplayName("🔴 같은 이메일의 로컬 계정이 있으면 연결 티켓과 함께 409 다 — 자동으로 붙이지 않는다")
	void doesNotImplicitlyLinkSocialIdentityToLocalEmail() {
		AppUser owner = AppUser.register("여행자", "KO", NOW, "2026-01", PersonalizationMode.EXPLICIT_ONLY,
				UserStatus.ACTIVE);
		LocalCredential credential = mock(LocalCredential.class);
		when(credential.getUser()).thenReturn(owner);
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
		// 🔴 옛 앱이 동의까지 보냈어도(oneStep) 연결 필요가 먼저다 — 계정을 새로 만들지 않는다.
		verify(userRepository, never()).save(any(AppUser.class));
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
		return new OAuthProviderClient.OAuthUserProfile("google-subject", "traveler@example.com", "여행자", language);
	}

	private Map<String, Boolean> requiredConsents() {
		return Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true);
	}
}
