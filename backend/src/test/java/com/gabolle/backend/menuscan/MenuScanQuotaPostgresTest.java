package com.gabolle.backend.menuscan;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.menuscan.application.MenuScanRateLimiter;
import com.gabolle.backend.menuscan.application.MenuScanUsageSweeper;
import com.gabolle.backend.menuscan.config.MenuScanProperties;
import com.gabolle.backend.menuscan.repository.MenuScanUsageRepository;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.MenuScanSliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메뉴판 한도가 프로세스 밖에 남는가. 가짜 리포지토리는 어차피 이 프로세스 안에 있어 이 성질을 못
 * 재므로 진짜 PostgreSQL 위에서 본다. «재시작»은 새 {@link MenuScanRateLimiter} 를 하나 더 만들어
 * 흉내 낸다.
 *
 * <p>한도를 넘었을 때 트랜잭션이 되돌아가는 거동은 여기서 확인하지 않는다 — 시계를 갈아 끼우려고
 * {@code new} 로 만들어 쓰므로 프록시가 없다. 운영 경로에서는 걸린다.
 *
 * <p>도커도 없고 {@code GABOLLE_TEST_DB_URL} 도 없으면 건너뜀으로 표시된다.
 */
@SpringBootTest(classes = MenuScanSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class MenuScanQuotaPostgresTest {

	private static final Instant NOW = Instant.parse("2026-09-16T09:00:00Z");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MenuScanUsageRepository usage;

	private UUID userId;

	private MenuScanProperties properties;

	@BeforeEach
	void seed() {
		this.userId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO app_user (user_id, display_name, language, personalization_mode, status,
						created_at, updated_at)
				VALUES (?, '시험 사용자', 'ko', 'OFF', 'ACTIVE', ?, ?)
				""", this.userId, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
				OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));

		this.properties = new MenuScanProperties();
		this.properties.setPerMinuteLimit(2);
		this.properties.setDailyLimit(3);
	}

	private MenuScanRateLimiter limiterAt(Instant at) {
		return new MenuScanRateLimiter(this.properties, this.usage, Clock.fixed(at, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("완료 기준 — 서버를 내렸다 올려도 그날 쓴 횟수가 이어진다")
	void theCountSurvivesANewInstance() {
		MenuScanRateLimiter before = limiterAt(NOW);
		before.takeOrThrow(this.userId);
		before.takeOrThrow(this.userId);

		// 「재시작」 — 앞선 인스턴스가 들고 있던 것은 아무것도 넘겨받지 않는다.
		MenuScanRateLimiter after = limiterAt(NOW.plusSeconds(10));

		assertThatThrownBy(() -> after.takeOrThrow(this.userId))
				.as("집계가 메모리에 있었다면 이 인스턴스는 0 부터 세어 통과시켰을 것이다")
				.isInstanceOf(MenuScanRateLimiter.TooManyScansException.class);
	}

	@Test
	@DisplayName("분 창이 지나면 다시 부를 수 있다 — 하루 한도 안에서")
	void theMinuteWindowSlides() {
		limiterAt(NOW).takeOrThrow(this.userId);
		limiterAt(NOW).takeOrThrow(this.userId);

		assertThatCode(() -> limiterAt(NOW.plusSeconds(120)).takeOrThrow(this.userId))
				.as("분 한도는 최근 60초만 본다")
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("하루 한도는 분 창이 지나도 남는다 — 두 한도가 같은 자료를 다른 창으로 읽는다")
	void theDailyCeilingOutlivesTheMinuteWindow() {
		limiterAt(NOW).takeOrThrow(this.userId);
		limiterAt(NOW).takeOrThrow(this.userId);
		limiterAt(NOW.plusSeconds(120)).takeOrThrow(this.userId);

		assertThatThrownBy(() -> limiterAt(NOW.plusSeconds(240)).takeOrThrow(this.userId))
				.isInstanceOf(MenuScanRateLimiter.TooManyScansException.class);
	}

	@Test
	@DisplayName("완료 기준 — 한 번 쓰고 안 돌아온 사람의 기록도 청소가 지운다")
	void theSweeperRemovesRowsOfUsersWhoNeverCameBack() {
		limiterAt(NOW).takeOrThrow(this.userId);
		assertThat(rowCount()).isEqualTo(1);

		// 그 사람은 다시 안 부른다. 부를 때 치우는 것만으로는 이 행이 영영 남는다.
		MenuScanUsageSweeper sweeper = new MenuScanUsageSweeper(this.usage,
				Clock.fixed(NOW.plusSeconds(25 * 60 * 60), ZoneOffset.UTC));

		int removed = sweeper.sweep();

		// 청소기는 사용자를 가리지 않는다. 앞선 시험이 남긴 행까지 함께 지우므로 «몇 행을
		// 지웠나» 로는 판정할 수 없다 — 이 시험의 사용자 것이 사라졌는지로 본다.
		assertThat(rowCount()).as("이 사용자의 지난 기록이 남아 있으면 안 된다").isZero();
		assertThat(removed).as("적어도 이 사용자의 한 행은 지웠어야 한다").isGreaterThanOrEqualTo(1);
	}

	@Test
	@DisplayName("청소는 창 안의 기록을 건드리지 않는다 — 지우고 나서 한도가 풀리면 안 된다")
	void theSweeperLeavesRowsInsideTheWindowAlone() {
		limiterAt(NOW).takeOrThrow(this.userId);

		MenuScanUsageSweeper sweeper = new MenuScanUsageSweeper(this.usage,
				Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
		sweeper.sweep();

		// 위와 같은 이유로 지운 행 수가 아니라 이 사용자의 행이 남았는지로 본다.
		assertThat(rowCount()).as("창 안의 기록을 지우면 한도가 풀린다").isEqualTo(1);
	}

	private int rowCount() {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM menu_scan_usage WHERE user_id = ?", Integer.class, this.userId);
		return (count == null) ? 0 : count;
	}
}
