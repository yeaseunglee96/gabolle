package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

	@Mock private AuthIdentityRepository identityRepository;
	@Mock private LocalCredentialRepository credentialRepository;
	@Mock private AppUserRepository userRepository;
	@Mock private UserConsentRepository consentRepository;
	@Mock private AuthTokenService tokenService;

	private OAuthAccountService service;

	@BeforeEach
	void setUp() {
		service = new OAuthAccountService(identityRepository, credentialRepository, userRepository, consentRepository,
				tokenService, new AuthProperties(), new ConsentPolicy(),
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void springCreatesServiceUsingConfiguredConstructor() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("dev");
			context.registerBean(AuthIdentityRepository.class, () -> mock(AuthIdentityRepository.class));
			context.registerBean(LocalCredentialRepository.class, () -> mock(LocalCredentialRepository.class));
			context.registerBean(AppUserRepository.class, () -> mock(AppUserRepository.class));
			context.registerBean(UserConsentRepository.class, () -> mock(UserConsentRepository.class));
			context.registerBean(AuthTokenService.class, () -> mock(AuthTokenService.class));
			context.registerBean(AuthProperties.class, AuthProperties::new);
			context.registerBean(ConsentPolicy.class, ConsentPolicy::new);
			context.register(OAuthAccountService.class);

			context.refresh();

			assertThat(context.getBean(OAuthAccountService.class)).isNotNull();
		}
	}

	@Test
	void requiresAppConsentsForFirstOAuthSignup() {
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loginOrRegister(AuthProvider.GOOGLE,
				profile("en-US"), "device-1", Map.of("TERMS_OF_SERVICE", true), false))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("필수 약관");
		verify(userRepository, never()).save(any(AppUser.class));
	}

	@Test
	void doesNotImplicitlyLinkSocialIdentityToLocalEmail() {
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.empty());
		when(credentialRepository.findByEmail("traveler@example.com"))
				.thenReturn(Optional.of(mock(LocalCredential.class)));

		assertThatThrownBy(() -> service.loginOrRegister(AuthProvider.GOOGLE,
				profile("KO"), "device-1", requiredConsents(), false))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("기존 계정");
		verify(userRepository, never()).save(any(AppUser.class));
	}

	@Test
	void blocksSocialLoginForUnavailableAccount() {
		AppUser suspended = AppUser.register("여행자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.DELETED);
		AuthIdentity identity = AuthIdentity.link(suspended, AuthProvider.GOOGLE, "google-subject",
				"traveler@example.com");
		when(identityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-subject"))
				.thenReturn(Optional.of(identity));

		assertThatThrownBy(() -> service.loginOrRegister(AuthProvider.GOOGLE,
				profile("KO"), "device-1", requiredConsents(), false))
				.isInstanceOf(AuthException.class)
				.hasMessageContaining("사용할 수 없는 계정");
		verify(tokenService, never()).issue(any(AppUser.class), anyString(), anyString());
	}

	private OAuthProviderClient.OAuthUserProfile profile(String language) {
		return new OAuthProviderClient.OAuthUserProfile("google-subject", "traveler@example.com", "여행자", language);
	}

	private Map<String, Boolean> requiredConsents() {
		return Map.of("TERMS_OF_SERVICE", true, "PRIVACY_POLICY", true);
	}
}
