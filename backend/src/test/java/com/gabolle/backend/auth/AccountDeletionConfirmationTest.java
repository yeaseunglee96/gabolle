package com.gabolle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.util.UUID;

import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.story.application.StorageCleanupService;
import com.gabolle.backend.user.repository.AppUserRepository;
import com.gabolle.backend.user.repository.UserConsentRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 탈퇴 확인 값 검사 — DB 없이 도는 부분 (S15P21E201-837).
 *
 * <h2>왜 통합 검사와 따로 두나</h2>
 *
 * {@code AccountDeletionIntegrationTest} 와 {@code AccountDeletionJourneyFunctionalTest} 는
 * 진짜 PostgreSQL 이 있어야 돈다. 그래서 DB 가 없는 기계에서는 <b>확인 값 검사가 살아 있는지를
 * 한 번도 못 본다.</b> 이 검사는 저장소를 전부 흉내 내서 그 한 겹만 본다 — 개발 기계에서도 돌고,
 * 확인 검사를 떼면 여기서 바로 빨개진다.
 *
 * <p>🔴 <b>저장소를 한 번도 안 건드리는 것</b>까지 함께 본다. 확인이 틀렸는데 계정을 조회하면,
 * 그 자체가 "확인 전에 무언가를 했다" 는 뜻이다. 지금은 조회뿐이라 해가 없지만 나중에 그 자리에
 * 지우는 코드가 끼어들 수 있다.
 */
class AccountDeletionConfirmationTest {

	private final LocalCredentialRepository credentialRepository = mock(LocalCredentialRepository.class);

	private final AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);

	private final AuthIdentityRepository identityRepository = mock(AuthIdentityRepository.class);

	private final UserConsentRepository consentRepository = mock(UserConsentRepository.class);

	private final AppUserRepository userRepository = mock(AppUserRepository.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	private final StorageCleanupService storageCleanupService = mock(StorageCleanupService.class);

	private final AccountDeletionService service = new AccountDeletionService(this.credentialRepository,
			this.sessionRepository, this.identityRepository, this.consentRepository, this.userRepository,
			this.passwordEncoder, Clock.systemUTC(), this.storageCleanupService);

	@ParameterizedTest(name = "확인 값이 \"{0}\" 이면 거절한다")
	@NullSource
	@ValueSource(strings = {"", " ", "delete", "Delete", "DELETE ", "탈퇴", "true"})
	@DisplayName("🔴 확인 값이 정확히 일치하지 않으면 거절하고 저장소를 건드리지 않는다")
	void rejectsAnythingButTheExactPhrase(String confirmation) {
		assertThatThrownBy(() -> this.service.delete(UUID.randomUUID(), confirmation, null))
				.isInstanceOf(AuthException.class)
				.satisfies(thrown -> {
					AuthException exception = (AuthException) thrown;
					assertThat(exception.getCode()).isEqualTo("DELETION_NOT_CONFIRMED");
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
				});

		verifyNoInteractions(this.credentialRepository, this.sessionRepository, this.identityRepository,
				this.consentRepository, this.userRepository, this.passwordEncoder, this.storageCleanupService);
	}

	@Test
	@DisplayName("확인 값은 화면이 그대로 보낼 수 있는 고정 문자열이다 — 언어별로 갈리지 않는다")
	void confirmationPhraseIsLocaleIndependent() {
		assertThat(AccountDeletionService.CONFIRMATION_PHRASE).isEqualTo("DELETE");
	}
}
