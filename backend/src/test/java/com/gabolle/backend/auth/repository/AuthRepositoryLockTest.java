package com.gabolle.backend.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.domain.AuthTokenPurpose;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class AuthRepositoryLockTest {

	@Test
	void oneTimeTokenAndRefreshLookupUsePessimisticWriteLock() throws Exception {
		Method tokenLookup = AuthOneTimeTokenRepository.class.getMethod(
				"findByTokenHashAndPurpose", String.class, AuthTokenPurpose.class);
		Method refreshLookup = AuthRefreshTokenRepository.class.getMethod("findByTokenHash", String.class);
		Method challengeLookup = OAuthChallengeRepository.class.getMethod(
				"findByProviderAndStateHash", com.gabolle.backend.auth.domain.AuthProvider.class, String.class);

		assertThat(tokenLookup.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		assertThat(refreshLookup.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
		assertThat(challengeLookup.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
	}
}
