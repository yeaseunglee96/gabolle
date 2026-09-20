package com.gabolle.backend.auth;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로그인 연속 실패 차단.
 *
 * <p>핵심 위험은 로직이 아니라 트랜잭션이다. {@code LocalAuthService.login} 은
 * {@code @Transactional} 이고 비밀번호가 틀리면 예외를 던지므로, 실패 횟수를 그 안에서 올리면
 * 올린 것까지 되돌려져 영영 한도에 닿지 않는다. 응답은 401 로 정상이라 저장소를 흉내 낸
 * 검사는 전부 통과한다.
 *
 * <p>그래서 실패시킨 뒤 DB 를 직접 읽어 값이 남았는지 본다. 엔티티로 읽으면 같은 트랜잭션의
 * 캐시를 볼 수 있어 증거가 되지 않는다.
 */
class LoginAttemptLimitIntegrationTest extends AuthPostgresIntegrationTest {

	private static final String PASSWORD = "RightRoute!2026";

	private static final String WRONG_PASSWORD = "WrongRoute!2026";

	@Autowired
	private LocalAuthService localAuthService;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private String email;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.email = "lock-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		this.userId = this.transactionTemplate.execute(status -> {
			AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
					PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
			LocalCredential credential = LocalCredential.create(user, this.email,
					this.passwordEncoder.encode(PASSWORD));
			credential.markEmailVerified(Instant.now());
			this.credentialRepository.save(credential);
			return user.getUserId();
		});
	}

	@AfterEach
	void tearDown() {
		// 표를 비우지 않고 내가 만든 사용자만 지운다. local_credential 은 ON DELETE CASCADE 라
		// 사용자를 지우면 함께 사라진다.
		this.jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", this.userId);
		this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", this.userId);
	}

	@Test
	@DisplayName("🔴 완료 기준 — 다섯 번 틀린 것이 DB 에 실제로 남는다 (트랜잭션이 되돌려져도)")
	void failuresSurviveTheRolledBackLoginTransaction() {
		assertThat(lockedUntilInDatabase()).as("시작할 때는 안 잠겨 있다").isNull();

		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD))
					.as("%d번째 시도", attempt)
					.isInstanceOf(AuthException.class);
		}

		// 여기가 이 테스트의 전부다. 세는 코드가 login 의 트랜잭션 안에 있었다면 이 값이 NULL 이다.
		assertThat(lockedUntilInDatabase())
				.as("다섯 번 틀렸으면 잠금 시각이 DB 에 남아 있어야 한다")
				.isNotNull();
	}

	@Test
	@DisplayName("완료 기준 — 여섯 번째 시도는 거부되고, 코드가 비밀번호 틀림과 다르다")
	void sixthAttemptIsRejectedWithADistinctCode() {
		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		// 비밀번호가 맞아도 거부된다. 맞는지 알려 주는 것 자체가 공격자에게 정보다.
		assertThatThrownBy(() -> login(PASSWORD))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
	}

	@Test
	@DisplayName("완료 기준 — 중간에 성공하면 세던 것이 0 으로 돌아간다")
	void successResetsTheCounter() {
		assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		assertThat(failedAttemptsInDatabase()).isEqualTo(2);

		assertThatCode(() -> login(PASSWORD)).doesNotThrowAnyException();

		assertThat(failedAttemptsInDatabase()).isZero();
		assertThat(lockedUntilInDatabase()).isNull();
	}

	@Test
	@DisplayName("네 번 틀린 것만으로는 잠기지 않는다 — 다섯 번째에 잠근다")
	void fourFailuresDoNotLock() {
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThatThrownBy(() -> login(WRONG_PASSWORD)).isInstanceOf(AuthException.class);
		}

		assertThat(lockedUntilInDatabase()).isNull();
		assertThatCode(() -> login(PASSWORD)).doesNotThrowAnyException();
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private void login(String password) {
		this.localAuthService.login(new AuthCommands.Login(this.email, password, "device-test"));
	}

	/** 엔티티가 아니라 DB 를 직접 읽는다. 엔티티로 읽으면 캐시를 보게 되어 증거가 되지 않는다. */
	private Instant lockedUntilInDatabase() {
		return this.jdbcTemplate.queryForObject(
				"SELECT login_locked_until FROM local_credential WHERE email = ?", Instant.class, this.email);
	}

	private Integer failedAttemptsInDatabase() {
		return this.jdbcTemplate.queryForObject(
				"SELECT failed_login_attempts FROM local_credential WHERE email = ?", Integer.class, this.email);
	}
}
