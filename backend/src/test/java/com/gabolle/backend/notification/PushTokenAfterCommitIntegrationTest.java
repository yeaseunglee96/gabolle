package com.gabolle.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.notification.infra.PushTokenJpaRepository;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.NotificationSliceApplication;

/**
 * 「커밋이 끝난 뒤에 부른 {@code forget()} 이 정말 지웠는가」 — S15P21E201-1484.
 *
 * <p>🔴 <b>이 하나는 목으로 못 본다.</b> {@code TripPushNotifierTest} 는 «지우라고 불렀는가»
 * 까지만 본다. 결함은 그 다음 칸에 있었다 — 불리기는 불렸고 DELETE 도 나갔고 예외도 안 났는데,
 * {@code AFTER_COMMIT} 안에서 기본 전파({@code REQUIRED} — 「도는 것이 있으면 합류한다」)로
 * 들어가는 바람에 <b>이미 커밋이 끝난 트랜잭션에 합류해 다시 커밋될 일이 없었다.</b>
 * 그래서 진짜 표에 대고 「행이 사라졌나」를 묻는다.
 *
 * <p>PostgreSQL 이 없는 PC 에서는 <b>통과가 아니라 건너뜀</b>으로 표시된다
 * ({@link PostgresAvailableCondition}) — 도커 없는 곳의 초록을 보고 이 계약이 지켜졌다고
 * 읽으면 안 된다.
 */
// 안쪽 @TestConfiguration 은 classes 를 «직접 적으면» 저절로 딸려오지 않는다. 손으로 적는다 —
// 안 적으면 리스너가 아예 안 서고, 시험은 「안 지워졌다」로 빨개져 결함처럼 보인다.
@SpringBootTest(classes = { NotificationSliceApplication.class,
		PushTokenAfterCommitIntegrationTest.AfterCommitListener.class }, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class PushTokenAfterCommitIntegrationTest {

	private static final String TOKEN = "ExponentPushToken[after-commit-1484]";

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	/** {@code TripPushNotifier} 가 실제로 서 있는 자리를 그대로 세운다 — 커밋이 끝난 «뒤». */
	record ForgetTheseTokens(List<String> tokens) {
	}

	/**
	 * 커밋이 끝난 뒤에 {@code forget()} 을 부르는 자리. <b>몇 번 불렸는지를 센다</b> —
	 * 「안 불렸다」와 「불렸는데 안 지워졌다」는 겉으로 똑같이 보이는데, 고쳐야 할 것이 완전히
	 * 다르다. 세지 않으면 시험이 빨개졌을 때 어느 쪽인지 알 수 없다.
	 */
	static class Forgetter {

		private final PushTokenService pushTokens;

		private volatile int calls;

		Forgetter(PushTokenService pushTokens) {
			this.pushTokens = pushTokens;
		}

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		public void on(ForgetTheseTokens event) {
			this.calls++;
			this.pushTokens.forget(event.tokens());
		}
	}

	@TestConfiguration
	static class AfterCommitListener {

		@Bean
		Forgetter forgetter(PushTokenService pushTokens) {
			return new Forgetter(pushTokens);
		}
	}

	@Autowired
	private PushTokenService pushTokens;

	@Autowired
	private PushTokenJpaRepository repository;

	@Autowired
	private ApplicationEventPublisher events;

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Forgetter forgetter;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.repository.findByToken(TOKEN).ifPresent(this.repository::delete);
		this.userId = UUID.randomUUID();
		// push_token.user_id 는 app_user 를 가리킨다. 사람 한 줄만 있으면 된다.
		this.jdbc.update("""
				INSERT INTO app_user (user_id, display_name, language, personalization_mode, status,
				                      created_at, updated_at)
				VALUES (?, '수민', 'ko', 'OFF', 'ACTIVE', ?, ?)
				""", this.userId, OffsetDateTime.now(), OffsetDateTime.now());
	}

	@Test
	@DisplayName("🔴 커밋이 끝난 뒤에 지운 토큰은 표에서 정말 사라진다 — 안 사라지면 죽은 기기로 영영 헛발송한다")
	void forgettingAfterCommitActuallyRemovesTheRow() {
		this.transactions.executeWithoutResult((status) -> {
			this.pushTokens.register(this.userId, TOKEN, "ios");
			// 이 사건은 «커밋된 다음에» 배달된다 — TripPushNotifier 가 서 있는 자리와 같다.
			this.events.publishEvent(new ForgetTheseTokens(List.of(TOKEN)));
		});

		assertThat(this.forgetter.calls)
				.as("커밋 뒤 리스너가 불리기는 했는가 — 여기가 0 이면 결함이 아니라 시험이 잘못 선 것이다")
				.isEqualTo(1);
		assertThat(this.repository.findByToken(TOKEN))
				.as("Expo 가 「없는 기기」라고 한 토큰은 표에 남아 있으면 안 된다")
				.isEmpty();
	}

	@Test
	@DisplayName("커밋이 끝난 뒤에도 보낼 기기를 읽을 수 있다 — 읽기도 같은 자리에서 돈다")
	void readingTokensAfterCommitStillWorks() {
		this.transactions.executeWithoutResult((status) -> this.pushTokens.register(this.userId, TOKEN, "ios"));

		assertThat(this.pushTokens.tokensOf(List.of(this.userId.toString()))).containsExactly(TOKEN);
	}
}
