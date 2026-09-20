package com.gabolle.backend.auth.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 연속 실패를 세고 잠그는 규칙.
 *
 * <p>시간이 걸린 규칙이라 고정된 시각으로 잰다. 실제 DB 에 남는지는
 * {@code LoginAttemptLimitIntegrationTest} 가 따로 본다.
 */
class LocalCredentialLoginLockTest {

	private static final int THRESHOLD = 5;

	private static final Duration LOCKOUT = Duration.ofMinutes(5);

	private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

	private LocalCredential credential;

	@BeforeEach
	void setUp() {
		AppUser user = AppUser.register("여행자", "KO", NOW, "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE);
		this.credential = LocalCredential.create(user, "traveler@example.com", "hash");
	}

	@Test
	@DisplayName("아무 일도 없으면 잠겨 있지 않다")
	void freshCredentialIsNotLocked() {
		assertThat(this.credential.isLoginLocked(NOW)).isFalse();
		assertThat(this.credential.getFailedLoginAttempts()).isZero();
	}

	@Test
	@DisplayName("완료 기준 — 네 번 틀려도 안 잠기고, 다섯 번째에 잠긴다")
	void locksOnlyAtTheThreshold() {
		for (int attempt = 1; attempt <= THRESHOLD - 1; attempt++) {
			this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);
			assertThat(this.credential.isLoginLocked(NOW))
					.as("%d번째 실패", attempt)
					.isFalse();
		}

		this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);

		assertThat(this.credential.isLoginLocked(NOW)).isTrue();
		assertThat(this.credential.getLoginLockedUntil()).isEqualTo(NOW.plus(LOCKOUT));
	}

	@Test
	@DisplayName("잠금은 시간이 지나면 저절로 풀린다 — 영구 잠금은 없다")
	void lockExpiresOnItsOwn() {
		for (int attempt = 0; attempt < THRESHOLD; attempt++) {
			this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);
		}

		assertThat(this.credential.isLoginLocked(NOW.plus(LOCKOUT).minusSeconds(1))).isTrue();
		assertThat(this.credential.isLoginLocked(NOW.plus(LOCKOUT))).isFalse();
	}

	@Test
	@DisplayName("🔴 잠금이 풀린 뒤에는 다시 다섯 번의 여유가 있다 — 한 번 틀렸다고 바로 다시 잠기지 않는다")
	void unlockingGivesAFreshAllowance() {
		for (int attempt = 0; attempt < THRESHOLD; attempt++) {
			this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);
		}
		Instant afterUnlock = NOW.plus(LOCKOUT).plusSeconds(1);
		assertThat(this.credential.isLoginLocked(afterUnlock)).isFalse();

		this.credential.recordFailedLogin(afterUnlock, THRESHOLD, LOCKOUT);

		assertThat(this.credential.isLoginLocked(afterUnlock))
				.as("풀린 직후 한 번 틀렸다고 다시 잠기면 사실상 영구 잠금이다")
				.isFalse();
	}

	@Test
	@DisplayName("완료 기준 — 성공하면 세던 것이 0 으로 돌아간다")
	void successResetsTheCounter() {
		this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);
		this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);
		assertThat(this.credential.getFailedLoginAttempts()).isEqualTo(2);

		this.credential.recordSuccessfulLogin();

		assertThat(this.credential.getFailedLoginAttempts()).isZero();
		assertThat(this.credential.getLoginLockedUntil()).isNull();
	}

	@Test
	@DisplayName("🔴 비밀번호를 바꾸면 잠금도 풀린다 — 새 비밀번호를 아는데 기다리게 하지 않는다")
	void changingPasswordClearsTheLock() {
		for (int attempt = 0; attempt < THRESHOLD; attempt++) {
			this.credential.recordFailedLogin(NOW, THRESHOLD, LOCKOUT);
		}
		assertThat(this.credential.isLoginLocked(NOW)).isTrue();

		this.credential.changePassword("new-hash", NOW);

		assertThat(this.credential.isLoginLocked(NOW)).isFalse();
		assertThat(this.credential.getFailedLoginAttempts()).isZero();
	}
}
