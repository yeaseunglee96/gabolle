package com.gabolle.backend.auth.config;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.domain.AuthIdentity;
import com.gabolle.backend.auth.domain.AuthProvider;
import com.gabolle.backend.auth.domain.LocalCredential;
import com.gabolle.backend.auth.repository.AuthIdentityRepository;
import com.gabolle.backend.auth.repository.LocalCredentialRepository;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.common.privacy.EmailMasker;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배포 설정으로만 운영자가 되는 것을 진짜 PostgreSQL 위에서 확인한다.
 *
 * <p>결과물은 응답이 아니라 {@code app_user.role} 에 남은 값이다. 권한 변경은
 * {@code @PostConstruct} 안에서 직접 만든 트랜잭션에 들어 있어, 트랜잭션이 안 걸렸거나
 * 되돌려지면 서버는 정상 기동하고 로그도 올렸다고 남는데 DB 는 그대로다.
 *
 * <p>그래서 확인은 항상 {@link JdbcTemplate} 로 표를 직접 읽어서 한다. 엔티티로 읽으면 같은
 * 영속성 컨텍스트의 캐시를 보게 되어 증거가 되지 않는다.
 *
 * <p>설정을 바꿔 가며 보려면 원래 컨텍스트를 여러 개 띄워야 하는데, 이 저장소는 그렇게
 * PostgreSQL 연결 자리가 말라 실패한 적이 있다({@code TestDatabase} 주석). 그래서
 * {@code synchronize(List)} 가 목록을 인자로 받고, 여기서는 컨텍스트 하나로 여러 설정을 본다.
 * "그 목록이 실제로 기동에 연결돼 있는가" 는 {@link AdminRoleStartupBootTest} 가 본다.
 */
class AdminRoleStartupSynchronizerIntegrationTest extends AuthPostgresIntegrationTest {

	@Autowired
	private AdminRoleStartupSynchronizer synchronizer;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AuthIdentityRepository identityRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private String ownerEmail;

	private UUID ownerUserId;

	private String bystanderEmail;

	private UUID bystanderUserId;

	private String socialEmail;

	private UUID socialUserId;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		this.ownerEmail = "adm225-owner-" + tag + "@example.com";
		this.bystanderEmail = "adm225-bystander-" + tag + "@example.com";
		this.socialEmail = "adm225-social-" + tag + "@example.com";
		this.transactionTemplate.executeWithoutResult(status -> {
			this.ownerUserId = withLocalCredential(this.ownerEmail);
			this.bystanderUserId = withLocalCredential(this.bystanderEmail);
			this.socialUserId = withSocialIdentityOnly(this.socialEmail);
		});
	}

	@AfterEach
	void tearDown() {
		// 표를 비우지 않는다. 내가 만든 계정만 지운다 — local_credential 과 auth_identity 는
		// FK 가 ON DELETE CASCADE 라 계정을 지우면 함께 사라진다.
		for (UUID userId : List.of(this.ownerUserId, this.bystanderUserId, this.socialUserId)) {
			this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", userId);
		}
	}

	@Test
	@DisplayName("완료 기준 — 설정에 적은 이메일의 계정이 ADMIN 으로 올라간다")
	void promotesTheConfiguredAccount() {
		AdminRoleStartupSynchronizer.Result result = this.synchronizer.synchronize(List.of(this.ownerEmail));

		assertThat(result.promotedUserIds())
				.as("올린 계정이 하나도 없다면 아래 확인은 아무것도 보지 않은 것이다")
				.isNotEmpty()
				.contains(this.ownerUserId);
		assertThat(roleInDatabase(this.ownerUserId)).isEqualTo("ADMIN");
		assertThat(roleInDatabase(this.bystanderUserId))
				.as("설정에 없는 계정은 건드리지 않는다")
				.isEqualTo("USER");
	}

	@Test
	@DisplayName("설정에 적은 값의 대소문자·공백은 가입할 때 쓴 주소와 같게 취급한다")
	void configurationIsNormalizedTheSameWayAsSignup() {
		String asTypedByAnOperator = "  " + this.ownerEmail.toUpperCase(Locale.ROOT) + "  ";

		AdminRoleStartupSynchronizer.Result result = this.synchronizer.synchronize(List.of(asTypedByAnOperator));

		assertThat(result.promotedUserIds()).isNotEmpty().contains(this.ownerUserId);
		assertThat(roleInDatabase(this.ownerUserId)).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("소셜 로그인만 쓰는 계정도 이메일로 찾아 올린다 — 그 사람 이메일은 local_credential 에 없다")
	void resolvesAnAccountThatOnlyHasASocialIdentity() {
		AdminRoleStartupSynchronizer.Result result = this.synchronizer.synchronize(List.of(this.socialEmail));

		assertThat(result.promotedUserIds()).isNotEmpty().contains(this.socialUserId);
		assertThat(roleInDatabase(this.socialUserId)).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("🔴 완료 기준 — 설정에서 빠진 이전 ADMIN 은 USER 로 내려간다")
	void demotesAnAdminThatIsNoLongerConfigured() {
		makeAdminInDatabase(this.bystanderUserId);
		assertThat(roleInDatabase(this.bystanderUserId))
				.as("내리는 것을 보려면 먼저 올라가 있어야 한다")
				.isEqualTo("ADMIN");

		AdminRoleStartupSynchronizer.Result result = this.synchronizer.synchronize(List.of(this.ownerEmail));

		assertThat(result.demotedUserIds()).isNotEmpty().contains(this.bystanderUserId);
		assertThat(roleInDatabase(this.bystanderUserId)).isEqualTo("USER");
		assertThat(roleInDatabase(this.ownerUserId))
				.as("같은 실행에서 올리는 일도 함께 끝난다")
				.isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("완료 기준 — 설정이 비어 있으면 아무도 관리자가 아니고, 기동을 막지 않는다")
	void emptyConfigurationLeavesNobodyAsAdminAndDoesNotFail() {
		makeAdminInDatabase(this.ownerUserId);

		AdminRoleStartupSynchronizer.Result result = this.synchronizer.synchronize(List.of());

		assertThat(result.configuredCount()).isZero();
		// 설정이 유일한 사실이므로, 목록이 비면 남아 있던 ADMIN 도 내려간다. 값을 지우는
		// 것만으로 권한이 회수돼야 하기 때문이다.
		assertThat(result.demotedUserIds()).isNotEmpty().contains(this.ownerUserId);
		assertThat(roleInDatabase(this.ownerUserId)).isEqualTo("USER");
	}

	@Test
	@DisplayName("🔴 완료 기준 — 적었는데 계정이 없으면 예외가 나고, 메시지가 어느 설정의 몇 번째인지 말한다")
	void failsAndNamesTheConfigurationWhenTheAccountDoesNotExist() {
		String absent = "zebra-" + UUID.randomUUID() + "@example.test";
		String absentLocalPart = absent.substring(0, absent.indexOf('@'));

		assertThatThrownBy(() -> this.synchronizer.synchronize(List.of(this.ownerEmail, absent)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(AdminRoleStartupSynchronizer.PROPERTY)
				.hasMessageContaining(AdminRoleStartupSynchronizer.PROPERTY + "[1]")
				.hasMessageContaining(EmailMasker.mask(absent))
				// 가린 값이 있다는 것만 보면 원문이 함께 실려 있어도 통과하므로,
				// 원문이 없다는 것을 따로 단정한다.
				.hasMessageNotContaining(absent)
				.hasMessageNotContaining(absentLocalPart);

		assertThat(roleInDatabase(this.ownerUserId))
				.as("하나가 잘못되면 나머지도 적용하지 않는다 — 절반만 반영된 권한 상태를 남기지 않는다")
				.isEqualTo("USER");
	}

	@Test
	@DisplayName("계정은 있지만 로그인할 수 없는 상태면 기동을 멈춘다 — 올려도 못 들어간다")
	void failsWhenTheConfiguredAccountCannotSignIn() {
		this.jdbcTemplate.update("UPDATE app_user SET status = 'DELETED' WHERE user_id = ?", this.ownerUserId);

		assertThatThrownBy(() -> this.synchronizer.synchronize(List.of(this.ownerEmail)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(AdminRoleStartupSynchronizer.PROPERTY + "[0]")
				.hasMessageContaining("DELETED");
	}

	@Test
	@DisplayName("설정에 쉼표를 하나 더 찍은 것은 그냥 넘기지 않는다")
	void rejectsABlankEntry() {
		assertThatThrownBy(() -> this.synchronizer.synchronize(List.of(this.ownerEmail, "  ")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(AdminRoleStartupSynchronizer.PROPERTY + "[1]");
	}

	@Test
	@DisplayName("이메일이 아닌 값은 원문을 로그에 남기지 않고 거절한다")
	void rejectsAValueThatIsNotAnEmailWithoutEchoingIt() {
		String notAnEmail = "root";

		assertThatThrownBy(() -> this.synchronizer.synchronize(List.of(notAnEmail)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(AdminRoleStartupSynchronizer.PROPERTY + "[0]")
				.hasMessageNotContaining(notAnEmail);
	}

	@Test
	@DisplayName("같은 설정을 두 번 적용해도 결과가 같다 — 재배포마다 권한이 흔들리지 않는다")
	void applyingTheSameConfigurationTwiceChangesNothingTheSecondTime() {
		this.synchronizer.synchronize(List.of(this.ownerEmail));

		AdminRoleStartupSynchronizer.Result again = this.synchronizer.synchronize(List.of(this.ownerEmail));

		assertThat(again.promotedUserIds()).isEmpty();
		assertThat(again.demotedUserIds()).isEmpty();
		assertThat(again.configuredCount()).isOne();
		assertThat(roleInDatabase(this.ownerUserId)).isEqualTo("ADMIN");
	}

	@Test
	@DisplayName("설정이 비어 있고 ADMIN 도 없으면 조용히 지나간다")
	void doesNothingWhenNothingIsConfiguredAndNobodyIsAdmin() {
		assertThatCode(() -> this.synchronizer.synchronize(List.of())).doesNotThrowAnyException();

		assertThat(roleInDatabase(this.ownerUserId)).isEqualTo("USER");
		assertThat(roleInDatabase(this.bystanderUserId)).isEqualTo("USER");
	}

	private UUID withLocalCredential(String email) {
		AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
		LocalCredential credential = LocalCredential.create(user, email,
				this.passwordEncoder.encode("RightRoute!2026"));
		credential.markEmailVerified(Instant.now());
		this.credentialRepository.save(credential);
		return user.getUserId();
	}

	private UUID withSocialIdentityOnly(String email) {
		AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
				PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
		this.identityRepository.save(AuthIdentity.link(user, AuthProvider.GOOGLE,
				"google-subject-" + UUID.randomUUID(), email));
		return user.getUserId();
	}

	private void makeAdminInDatabase(UUID userId) {
		this.jdbcTemplate.update("UPDATE app_user SET role = 'ADMIN' WHERE user_id = ?", userId);
	}

	private String roleInDatabase(UUID userId) {
		return this.jdbcTemplate.queryForObject("SELECT role FROM app_user WHERE user_id = ?", String.class, userId);
	}
}
