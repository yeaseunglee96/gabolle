package com.gabolle.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import com.gabolle.backend.auth.service.OperatorAccountDeletionService;
import com.gabolle.backend.auth.service.OperatorAccountDeletionService.Outcome;
import com.gabolle.backend.auth.service.OperatorAccountDeletionService.Result;
import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 운영자 대리 탈퇴 (S15P21E201-1647) — 앱에 로그인할 수 없는 사람의 이메일 삭제 요청을 운영자가 대신 처리한다.
 *
 * <p>지우는 일 자체는 {@code AccountDeletionIntegrationTest} 가 본다. 여기서는 <b>이 경로가 맞는 사람을 찾는지, 애매하면
 * 멈추는지, 기록을 남기는지, 이메일 원문을 남기지 않는지</b>를 본다.
 */
class OperatorAccountDeletionIntegrationTest extends AuthPostgresIntegrationTest {

	private static final String REF = "2026-09-26-mail-1";

	private static final String OPERATOR = "테스트운영자";

	@Autowired
	private OperatorAccountDeletionService service;

	@Autowired
	private LocalCredentialRepository credentialRepository;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate tx;

	private final List<UUID> users = new ArrayList<>();

	private final List<String> hashes = new ArrayList<>();

	private String email;

	@BeforeEach
	void setUp() {
		this.email = "operator-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	@AfterEach
	void tearDown() {
		for (String hash : this.hashes) {
			this.jdbc.update("DELETE FROM operator_account_deletion_log WHERE email_sha256 = ?", hash);
		}
		for (UUID user : this.users) {
			this.jdbc.update("DELETE FROM auth_identity WHERE user_id = ?", user);
			this.jdbc.update("DELETE FROM auth_session WHERE user_id = ?", user);
			this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", user);
		}
	}

	private UUID emailUser(String address) {
		UUID id = this.tx.execute(status -> {
			AppUser user = this.userRepository.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01",
					PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE));
			LocalCredential credential = LocalCredential.create(user, address, this.passwordEncoder.encode("Pw!2026abc"));
			credential.markEmailVerified(Instant.now());
			this.credentialRepository.save(credential);
			return user.getUserId();
		});
		this.users.add(id);
		return id;
	}

	private UUID socialUser(String providerEmail, String provider) {
		UUID id = this.tx.execute(status -> this.userRepository.save(AppUser.register("소셜 여행자", "KO", Instant.now(),
				"2026-01", PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE)).getUserId());
		this.jdbc.update("""
				INSERT INTO auth_identity (identity_id, user_id, provider, provider_subject, provider_email, linked_at)
				VALUES (?, ?, ?, ?, ?, now())
				""", UUID.randomUUID(), id, provider, "sub-" + UUID.randomUUID(), providerEmail);
		this.users.add(id);
		return id;
	}

	private String hashOf(String normalized) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) {
				sb.append(String.format("%02x", b));
			}
			String hash = sb.toString();
			this.hashes.add(hash);
			return hash;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private String statusOf(UUID user) {
		return this.jdbc.queryForObject("SELECT status FROM app_user WHERE user_id = ?", String.class, user);
	}

	private int logRows(String hash) {
		return this.jdbc.queryForObject("SELECT count(*) FROM operator_account_deletion_log WHERE email_sha256 = ?",
				Integer.class, hash);
	}

	@Test
	@DisplayName("🔴 미리보기는 아무것도 바꾸지 않고 기록도 안 남긴다 — 무엇이 지워질지만 알려 준다")
	void dryRunChangesNothing() {
		UUID user = emailUser(this.email);
		String hash = hashOf(this.email);

		Result result = this.service.process(this.email, null, null, false);

		assertThat(result.outcome()).isEqualTo(Outcome.DRY_RUN);
		assertThat(result.userId()).isEqualTo(user);
		assertThat(result.loginMeans()).containsExactly("LOCAL");
		assertThat(result.preview()).isNotNull();
		assertThat(statusOf(user)).isEqualTo("ACTIVE");
		assertThat(this.credentialRepository.findByUserUserId(user)).isPresent();
		assertThat(logRows(hash)).isZero();
	}

	@Test
	@DisplayName("🔴 실행하면 계정이 지워지고(로그인 수단 삭제·익명화) 처리 기록이 한 줄 남는다")
	void executeDeletesAndRecords() {
		UUID user = emailUser(this.email);
		String hash = hashOf(this.email);

		Result result = this.service.process(this.email, REF, OPERATOR, true);

		assertThat(result.outcome()).isEqualTo(Outcome.DELETED);
		assertThat(statusOf(user)).isEqualTo("DELETED");
		assertThat(this.credentialRepository.findByUserUserId(user)).isEmpty();
		// 같은 이메일로 다시 찾으면 이제 없다 — 이메일이 풀렸다.
		assertThat(this.service.process(this.email, null, null, false).outcome()).isEqualTo(Outcome.NOT_FOUND);

		assertThat(logRows(hash)).isEqualTo(1);
		var row = this.jdbc.queryForMap(
				"SELECT request_ref, operator_name, deleted_user_id, outcome FROM operator_account_deletion_log WHERE email_sha256 = ?",
				hash);
		assertThat(row.get("request_ref")).isEqualTo(REF);
		assertThat(row.get("operator_name")).isEqualTo(OPERATOR);
		assertThat(row.get("deleted_user_id")).isEqualTo(user);
		assertThat(row.get("outcome")).isEqualTo("DELETED");
	}

	@Test
	@DisplayName("🔴 기록에 이메일 원문이 어느 칸에도 없다 — 해시만")
	void logNeverContainsTheEmail() {
		emailUser(this.email);
		String hash = hashOf(this.email);
		this.service.process(this.email, REF, OPERATOR, true);

		String everything = this.jdbc.queryForObject("""
				SELECT request_ref || '|' || operator_name || '|' || email_sha256 || '|' || outcome
				  FROM operator_account_deletion_log WHERE email_sha256 = ?
				""", String.class, hash);
		assertThat(everything).doesNotContain(this.email).doesNotContain("@");
		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("대소문자·앞뒤 공백이 달라도 같은 계정을 찾는다 — 가입 때와 같은 정규화")
	void findsAccountRegardlessOfCaseAndWhitespace() {
		UUID user = emailUser(this.email);
		hashOf(this.email);

		Result result = this.service.process("  " + this.email.toUpperCase() + " ", null, null, false);

		assertThat(result.outcome()).isEqualTo(Outcome.DRY_RUN);
		assertThat(result.userId()).isEqualTo(user);
	}

	@Test
	@DisplayName("소셜로만 가입한 사람도 제공자 이메일로 찾아 지운다")
	void findsSocialOnlyUserByProviderEmail() {
		UUID user = socialUser(this.email, "GOOGLE");
		hashOf(this.email);

		Result result = this.service.process(this.email, REF, OPERATOR, true);

		assertThat(result.outcome()).isEqualTo(Outcome.DELETED);
		assertThat(result.loginMeans()).containsExactly("GOOGLE");
		assertThat(statusOf(user)).isEqualTo("DELETED");
	}

	@Test
	@DisplayName("🔴 한 이메일이 서로 다른 계정 둘을 가리키면 아무것도 지우지 않고 AMBIGUOUS 를 기록한다")
	void ambiguousEmailDeletesNothing() {
		UUID viaEmail = emailUser(this.email);
		UUID viaSocial = socialUser(this.email, "KAKAO");
		String hash = hashOf(this.email);

		Result result = this.service.process(this.email, REF, OPERATOR, true);

		assertThat(result.outcome()).isEqualTo(Outcome.AMBIGUOUS);
		assertThat(result.matchedAccounts()).isEqualTo(2);
		assertThat(statusOf(viaEmail)).isEqualTo("ACTIVE");
		assertThat(statusOf(viaSocial)).isEqualTo("ACTIVE");
		assertThat(this.jdbc.queryForObject(
				"SELECT outcome FROM operator_account_deletion_log WHERE email_sha256 = ?", String.class, hash))
				.isEqualTo("AMBIGUOUS");
	}

	@Test
	@DisplayName("같은 계정이 이메일 가입과 소셜 연결 둘 다로 잡혀도 한 계정으로 센다")
	void sameAccountViaTwoPathsCountsOnce() {
		UUID user = emailUser(this.email);
		this.jdbc.update("""
				INSERT INTO auth_identity (identity_id, user_id, provider, provider_subject, provider_email, linked_at)
				VALUES (?, ?, 'NAVER', ?, ?, now())
				""", UUID.randomUUID(), user, "sub-" + UUID.randomUUID(), this.email);
		hashOf(this.email);

		Result result = this.service.process(this.email, REF, OPERATOR, true);

		assertThat(result.outcome()).isEqualTo(Outcome.DELETED);
		assertThat(result.matchedAccounts()).isEqualTo(1);
		assertThat(result.loginMeans()).containsExactlyInAnyOrder("LOCAL", "NAVER");
	}

	@Test
	@DisplayName("없는 이메일이면 NOT_FOUND — 실행하면 그 사실도 기록한다")
	void unknownEmailIsNotFoundAndRecorded() {
		String hash = hashOf(this.email);

		Result result = this.service.process(this.email, REF, OPERATOR, true);

		assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
		assertThat(result.userId()).isNull();
		assertThat(this.jdbc.queryForObject(
				"SELECT outcome FROM operator_account_deletion_log WHERE email_sha256 = ?", String.class, hash))
				.isEqualTo("NOT_FOUND");
	}

	@Test
	@DisplayName("🔴 요청 식별이나 운영자가 비어 있으면 지우기 전에 멈춘다 — 기록이 비는 삭제는 없다")
	void executeRequiresRefAndOperatorBeforeDeleting() {
		UUID user = emailUser(this.email);
		hashOf(this.email);

		assertThatThrownBy(() -> this.service.process(this.email, " ", OPERATOR, true))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.service.process(this.email, REF, null, true))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(statusOf(user)).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("🔴 요청 식별에 이메일 주소를 적으면 거절한다 — 기록에 원문이 새는 길을 막는다")
	void refMustNotContainAnEmailAddress() {
		UUID user = emailUser(this.email);
		hashOf(this.email);

		assertThatThrownBy(() -> this.service.process(this.email, this.email, OPERATOR, true))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이메일");

		assertThat(statusOf(user)).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("이미 지운 계정은 다시 찾지 않는다")
	void alreadyDeletedAccountIsNotFound() {
		UUID user = emailUser(this.email);
		hashOf(this.email);
		this.service.process(this.email, REF, OPERATOR, true);
		assertThat(statusOf(user)).isEqualTo("DELETED");

		assertThat(this.service.process(this.email, REF, OPERATOR, true).outcome()).isEqualTo(Outcome.NOT_FOUND);
	}

}
