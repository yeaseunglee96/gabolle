package com.gabolle.backend.auth;

import java.time.Instant;
import java.time.LocalDate;
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
import com.gabolle.backend.auth.service.AccountDeletionService;
import com.gabolle.backend.auth.service.AuthCommands;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.auth.service.LocalAuthService;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 계정·데이터 일괄 삭제 — S15P21E201-425 (-183 포함).
 *
 * <h2>🔴 왜 진짜 DB 가 필요한가</h2>
 *
 * 이 기능의 위험은 두 가지이고 둘 다 실제 DB 에서만 드러난다.
 *
 * <p>하나는 <b>지우는 순서</b>다. 자식 행을 먼저 지우지 않으면 외래키에 걸려 통째로 실패한다.
 * 순서가 틀렸는지는 실제 제약이 걸린 표에서만 알 수 있다.
 *
 * <p>다른 하나는 <b>JPQL 이 가리키는 엔티티 이름</b>이다. 다른 담당자의 코드를 고치지 않으려고
 * 문자열로 썼기 때문에, 그쪽이 이름을 바꾸면 컴파일이 아니라 실행에서 터진다. 이 테스트가 삭제
 * 경로를 통째로 돌려서 그것을 잡는다.
 */
class AccountDeletionIntegrationTest extends AuthPostgresIntegrationTest {

	private static final String PASSWORD = "DeleteMe!2026";

	@Autowired
	private AccountDeletionService accountDeletionService;

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

	private UUID userId;

	private String email;

	private UUID tripId;

	private UUID otherUserId;

	private UUID otherTripId;

	@BeforeEach
	void setUp() {
		this.email = "erase-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		this.userId = createUser(this.email);
		this.tripId = createTrip(this.userId);

		this.otherUserId = createUser("keep-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
		this.otherTripId = createTrip(this.otherUserId);
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다.
		for (UUID trip : new UUID[] {this.tripId, this.otherTripId}) {
			this.jdbcTemplate.update("DELETE FROM trip WHERE trip_id = ?", trip);
		}
		for (UUID user : new UUID[] {this.userId, this.otherUserId}) {
			this.jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", user);
			this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", user);
		}
	}

	@Test
	@DisplayName("완료 기준 — 올바른 비밀번호로 부르면 로그인 수단이 사라지고 같은 이메일로 로그인이 안 된다")
	void deletesLoginMeansAndBlocksLogin() {
		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isPresent();

		this.accountDeletionService.delete(this.userId, PASSWORD);

		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isEmpty();
		assertThat(countByUser("local_credential", this.userId)).isZero();
		assertThat(countByUser("auth_session", this.userId)).isZero();
		assertThat(countByUser("user_consent", this.userId)).isZero();

		assertThatThrownBy(() -> this.localAuthService.login(
				new AuthCommands.Login(this.email, PASSWORD, "device")))
				.isInstanceOf(AuthException.class);
	}

	@Test
	@DisplayName("🔴 계정 행은 남지만 개인을 알아볼 값이 없다 — 남의 일정 이력이 이 행을 가리키기 때문")
	void accountRowRemainsButCarriesNoPersonalData() {
		this.accountDeletionService.delete(this.userId, PASSWORD);

		AppUser remaining = this.userRepository.findById(this.userId).orElseThrow();
		assertThat(remaining.getStatus()).isEqualTo(UserStatus.DELETED);
		assertThat(remaining.getDeletedAt()).isNotNull();
		assertThat(remaining.getDisplayName()).isEqualTo("탈퇴한 사용자");
		assertThat(remaining.getAgeVerifiedAt()).isNull();
	}

	@Test
	@DisplayName("완료 기준 — 본인 여행이 사라진다 (지우는 순서와 JPQL 엔티티 이름이 맞는지가 여기서 드러난다)")
	void deletesOwnTrips() {
		assertThat(tripExists(this.tripId)).isTrue();

		this.accountDeletionService.delete(this.userId, PASSWORD);

		assertThat(tripExists(this.tripId)).isFalse();
	}

	@Test
	@DisplayName("완료 기준 — 틀린 비밀번호는 거부되고 아무것도 지워지지 않는다")
	void wrongPasswordDeletesNothing() {
		assertThatThrownBy(() -> this.accountDeletionService.delete(this.userId, "NotThePassword!1"))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("INVALID_CREDENTIALS");

		assertThat(this.credentialRepository.findByUserUserId(this.userId)).isPresent();
		assertThat(tripExists(this.tripId)).isTrue();
		assertThat(this.userRepository.findById(this.userId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("🔴 남의 계정과 여행은 손대지 않는다")
	void otherPeopleAreUntouched() {
		this.accountDeletionService.delete(this.userId, PASSWORD);

		assertThat(tripExists(this.otherTripId)).isTrue();
		assertThat(this.credentialRepository.findByUserUserId(this.otherUserId)).isPresent();
		assertThat(this.userRepository.findById(this.otherUserId).orElseThrow().getStatus())
				.isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("이미 지운 계정을 다시 지우려 하면 비밀번호 수단이 없다고 거절한다")
	void deletingTwiceIsRejected() {
		this.accountDeletionService.delete(this.userId, PASSWORD);

		assertThatThrownBy(() -> this.accountDeletionService.delete(this.userId, PASSWORD))
				.isInstanceOf(AuthException.class)
				.extracting(exception -> ((AuthException) exception).getCode())
				.isEqualTo("LOCAL_CREDENTIAL_REQUIRED");
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private UUID createUser(String address) {
		return this.transactionTemplate.execute(status -> {
			AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
					PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
			LocalCredential credential = LocalCredential.create(user, address,
					this.passwordEncoder.encode(PASSWORD));
			credential.markEmailVerified(Instant.now());
			this.credentialRepository.save(credential);
			return user.getUserId();
		});
	}

	private UUID createTrip(UUID owner) {
		UUID trip = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, created_at, updated_at)
				VALUES (?, ?, ?, ?, now(), now())
				""", trip, owner, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		return trip;
	}

	private boolean tripExists(UUID trip) {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM trip WHERE trip_id = ?", Integer.class, trip);
		return count != null && count > 0;
	}

	private Integer countByUser(String table, UUID user) {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, user);
	}
}
