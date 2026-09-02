package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.config.AuthProperties;
import com.gabolle.backend.auth.domain.AuthOneTimeToken;
import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthOneTimeTokenRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
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

	@Mock
	private AuthOneTimeTokenRepository oneTimeTokenRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthTokenService authTokenService;

	@Mock
	private EmailSender emailSender;

	private LocalAuthService service;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties();
		service = new LocalAuthService(userRepository, consentRepository, credentialRepository, oneTimeTokenRepository, passwordEncoder,
				new SessionTokenGenerator(), authTokenService, emailSender, properties,
				new ConsentPolicy(),
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
