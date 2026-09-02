package com.gabolle.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurrentUserServiceTest {

	private final AppUserRepository userRepository = mock(AppUserRepository.class);
	private final LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
	private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
	private CurrentUserService service;

	@BeforeEach
	void setUp() {
		service = new CurrentUserService(userRepository, localCredentialRepository, authIdentityRepository);
	}

	@Test
	void returnsLocalAccountEmail() {
		UUID userId = UUID.randomUUID();
		AppUser user = activeUser();
		LocalCredential credential = mock(LocalCredential.class);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(localCredentialRepository.findByUserUserId(userId)).thenReturn(Optional.of(credential));
		when(credential.getEmail()).thenReturn("traveler@example.com");

		CurrentUserService.CurrentUser result = service.get(userId);

		assertThat(result.user()).isSameAs(user);
		assertThat(result.email()).isEqualTo("traveler@example.com");
	}

	@Test
	void returnsActiveSocialIdentityEmailWhenLocalCredentialDoesNotExist() {
		UUID userId = UUID.randomUUID();
		AppUser user = activeUser();
		AuthIdentity identity = mock(AuthIdentity.class);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(localCredentialRepository.findByUserUserId(userId)).thenReturn(Optional.empty());
		when(authIdentityRepository.findAllByUserUserId(userId)).thenReturn(List.of(identity));
		when(identity.isActive()).thenReturn(true);
		when(identity.getProviderEmail()).thenReturn("social@example.com");

		assertThat(service.get(userId).email()).isEqualTo("social@example.com");
	}

	@Test
	void rejectsUnavailableAccount() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(userId))
				.isInstanceOfSatisfying(AuthException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("ACCOUNT_UNAVAILABLE");
					assertThat(exception.getStatus().value()).isEqualTo(401);
				});
	}

	private AppUser activeUser() {
		AppUser user = AppUser.register("여행자", "ko", Instant.now(), "2026-01",
				com.gabolle.backend.user.domain.PersonalizationMode.BEHAVIOR_ENABLED,
				UserStatus.PENDING_EMAIL_VERIFICATION);
		user.activate();
		return user;
	}
}
