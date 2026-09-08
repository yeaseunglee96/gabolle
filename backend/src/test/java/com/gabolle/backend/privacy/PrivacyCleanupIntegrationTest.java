package com.gabolle.backend.privacy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.domain.AuthRefreshToken;
import com.gabolle.backend.auth.domain.AuthSession;
import com.gabolle.backend.auth.repository.AuthRefreshTokenRepository;
import com.gabolle.backend.auth.repository.AuthSessionRepository;
import com.gabolle.backend.privacy.application.PrivacyCleanupScheduler;
import com.gabolle.backend.privacy.application.PrivacyCleanupService;
import com.gabolle.backend.privacy.domain.PrivacyCleanupResult;
import com.gabolle.backend.privacy.domain.PrivacyCleanupRun;
import com.gabolle.backend.privacy.domain.PrivacyCleanupStatus;
import com.gabolle.backend.privacy.support.PrivacyPostgresIntegrationTest;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개인정보 자동 정리 배치 — S15P21E201-357 · -166.
 *
 * <h2>🔴 왜 진짜 DB 가 필요한가</h2>
 *
 * {@link PrivacyCleanupService#cleanup()} 이 지운다고 믿는 것 중 하나(auth_session 이 지워지면
 * auth_refresh_token 이 함께 지워진다)는 애플리케이션 코드가 아니라
 * {@code fk_auth_refresh_token_session ... ON DELETE CASCADE}(DB 제약)이 하는 일이다. Mockito
 * 로는 그 상호작용 자체가 존재하지 않는 것처럼 보인다 — 이 사실이 실제로 성립하는지는 진짜
 * PostgreSQL 에서만 확인된다.
 */
class PrivacyCleanupIntegrationTest extends PrivacyPostgresIntegrationTest {

	@Autowired
	private PrivacyCleanupService cleanupService;

	@Autowired
	private PrivacyCleanupScheduler cleanupScheduler;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private AuthSessionRepository sessionRepository;

	@Autowired
	private AuthRefreshTokenRepository refreshTokenRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.userId = this.transactionTemplate.execute(status -> this.userRepository
				.save(AppUser.register("여행자", "KO", Instant.now(), "2026-01", PersonalizationMode.EXPLICIT_ONLY,
						UserStatus.ACTIVE))
				.getUserId());
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 이 테스트가 만든 것만 지운다 — AccountDeletionIntegrationTest 와 같은 규칙.
		this.jdbcTemplate.update("DELETE FROM privacy_cleanup_run");
		this.jdbcTemplate.update("DELETE FROM event_outbox WHERE aggregate_id = ?", this.userId);
		this.jdbcTemplate.update("DELETE FROM auth_refresh_token WHERE session_id IN "
				+ "(SELECT session_id FROM auth_session WHERE user_id = ?)", this.userId);
		this.jdbcTemplate.update("DELETE FROM auth_session WHERE user_id = ?", this.userId);
		this.jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", this.userId);
	}

	@Test
	@DisplayName("완료 기준 — 만료된 지 유예기간이 지난 세션을 지우면 그 세션의 리프레시 토큰도 CASCADE 로 함께 사라진다")
	void cleanup_deletesExpiredSession_cascadesItsRefreshToken() {
		Instant now = Instant.now();

		// 유예기간(테스트 설정 1일)을 훌쩍 넘겨 만료된 세션 — 지워져야 한다.
		AuthSession expiredSession = this.sessionRepository.save(AuthSession.issue(user(), UUID.randomUUID(),
				"rt-hash-" + UUID.randomUUID(), "device-old", now.minusSeconds(10 * 24 * 3600)));
		// 이 세션에 딸린 리프레시 토큰은 그 자체로는 아직 안 만료됐다 — 그래도 세션이 지워지면 CASCADE 로 함께 사라져야 한다.
		String tokenHashOnExpiredSession = "token-hash-" + UUID.randomUUID();
		this.refreshTokenRepository.save(AuthRefreshToken.issue(expiredSession, tokenHashOnExpiredSession,
				now.plusSeconds(10 * 24 * 3600)));

		// 아직 살아 있는 세션 — 남아야 한다.
		AuthSession activeSession = this.sessionRepository.save(AuthSession.issue(user(), UUID.randomUUID(),
				"rt-hash-" + UUID.randomUUID(), "device-new", now.plusSeconds(10 * 24 * 3600)));
		// 세션은 살아 있지만 이 토큰 자신은 유예기간을 넘겨 만료됐다 — 세션과 무관하게 직접 지워져야 한다.
		String expiredTokenHashOnActiveSession = "token-hash-" + UUID.randomUUID();
		this.refreshTokenRepository.save(AuthRefreshToken.issue(activeSession, expiredTokenHashOnActiveSession,
				now.minusSeconds(10 * 24 * 3600)));

		PrivacyCleanupResult result = this.cleanupService.cleanup();

		assertThat(result.sessionsDeleted()).isGreaterThanOrEqualTo(1);
		// 🔴 이 건수는 AuthRefreshToken 을 직접 지운 JPQL 문 하나가 지운 행 수다. tokenOnExpiredSession
		// 은 자기 자신은 안 만료됐고 위의 세션 삭제가 발생시킨 DB 레벨 CASCADE 로 사라지므로, 그 삭제는
		// 이 카운트에 안 잡힌다(아래에서 행 자체가 사라졌는지로 따로 확인한다) — 그래서 여기서는
		// expiredTokenOnActiveSession 하나만 보장한다.
		assertThat(result.refreshTokensDeleted()).isGreaterThanOrEqualTo(1);

		// 🔴 리포지토리의 findByTokenHash 는 @Lock(PESSIMISTIC_WRITE) 이라 트랜잭션 밖에서는 못
		// 부른다. JdbcTemplate 으로 행 존재만 직접 확인한다.
		assertThat(this.sessionRepository.findById(expiredSession.getSessionId())).isEmpty();
		assertThat(countRefreshTokenByHash(tokenHashOnExpiredSession)).isZero();
		assertThat(countRefreshTokenByHash(expiredTokenHashOnActiveSession)).isZero();

		assertThat(this.sessionRepository.findById(activeSession.getSessionId())).isPresent();
	}

	@Test
	@DisplayName("완료 기준 — 발행이 끝난 오래된 이벤트는 지우고, 아직 안 보낸 이벤트는 아무리 오래돼도 지우지 않는다")
	void cleanup_deletesPublishedOldEvents_neverDeletesUnpublishedEvents() {
		Instant now = Instant.now();
		Instant longAgo = now.minusSeconds(10 * 24 * 3600);
		Instant recently = now.minusSeconds(3600);

		UUID publishedOldEventId = insertEvent(longAgo, true);
		UUID publishedRecentEventId = insertEvent(recently, true);
		UUID unpublishedOldEventId = insertEvent(longAgo, false);

		this.cleanupService.cleanup();

		assertThat(countEvent(publishedOldEventId)).isZero();
		assertThat(countEvent(publishedRecentEventId)).isEqualTo(1);
		assertThat(countEvent(unpublishedOldEventId)).isEqualTo(1);
	}

	@Test
	@DisplayName("완료 기준 — 실행마다 privacy_cleanup_run 에 성공/삭제 건수가 기록으로 남는다")
	void run_recordsSucceededRunWithDeletionCounts() {
		Instant now = Instant.now();
		this.sessionRepository.save(AuthSession.issue(user(), UUID.randomUUID(), "rt-hash-" + UUID.randomUUID(),
				"device", now.minusSeconds(10 * 24 * 3600)));

		PrivacyCleanupRun run = this.cleanupScheduler.run();

		assertThat(run.getRunId()).isNotNull();
		assertThat(run.getStatus()).isEqualTo(PrivacyCleanupStatus.SUCCEEDED);
		assertThat(run.getFinishedAt()).isNotNull();
		assertThat(run.getExpiredSessionsDeleted()).isGreaterThanOrEqualTo(1);
	}

	private AppUser user() {
		return this.userRepository.getReferenceById(this.userId);
	}

	private UUID insertEvent(Instant occurredAt, boolean published) {
		UUID eventId = UUID.randomUUID();
		OffsetDateTime occurredAtOffset = occurredAt.atOffset(ZoneOffset.UTC);
		this.jdbcTemplate.update("""
				INSERT INTO event_outbox
					(event_id, event_type, event_version, aggregate_type, aggregate_id, partition_key,
					 payload, occurred_at, received_at, publish_status, published_at)
				VALUES (?, 'TEST_EVENT', 1, 'TEST', ?, ?, '{}'::jsonb, ?, ?, ?, ?)
				""", eventId, this.userId, this.userId.toString(), occurredAtOffset, occurredAtOffset,
				published ? "PUBLISHED" : "PENDING", published ? occurredAtOffset : null);
		return eventId;
	}

	private int countRefreshTokenByHash(String tokenHash) {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM auth_refresh_token WHERE token_hash = ?", Integer.class, tokenHash);
		return count == null ? 0 : count;
	}

	private int countEvent(UUID eventId) {
		Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM event_outbox WHERE event_id = ?",
				Integer.class, eventId);
		return count == null ? 0 : count;
	}
}
