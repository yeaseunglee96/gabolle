package com.gabolle.backend.batch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.batch.application.TasteVectorBatchService;
import com.gabolle.backend.batch.application.TasteVectorDailyFoldProperties;
import com.gabolle.backend.batch.application.TasteVectorDailyFoldScheduler;
import com.gabolle.backend.batch.support.BatchPostgresTest;
import com.gabolle.backend.batch.support.TasteVectorFixtures;
import com.gabolle.backend.preference.domain.UserTasteVector;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새벽 접기를 백엔드가 직접 돌려도 Airflow DAG 와 같은 결과인가 (S15P21E201-1516).
 *
 * <p>DAG 가 부르던 {@code /stale} → {@code /rebuild} 를 HTTP 없이 부른다. 진짜 PostgreSQL 에서
 * 돈다 — 「뒤처진 사람」 판정이 {@code TIMESTAMPTZ} 비교와 조건부 색인에 달려 있다.
 */
class TasteVectorDailyFoldSchedulerIntegrationTest extends BatchPostgresTest {

	@Autowired
	private TasteVectorDailyFoldScheduler scheduler;

	@Autowired
	private TasteVectorDailyFoldProperties properties;

	@Autowired
	private UserTasteVectorRepository vectors;

	@Autowired
	private UserTasteWeightRepository weights;

	@Autowired
	private JdbcTemplate jdbc;

	private TasteVectorFixtures fixtures;

	private int originalLimit;

	@BeforeEach
	void setUp() {
		this.fixtures = new TasteVectorFixtures(this.jdbc);
		// 같은 DB 에 다른 시험이 만든 사람도 뒤처져 있다. 기본 상한(500)에 걸려 이 시험의 사람이
		// 다음 쪽으로 밀리면, 스케줄러가 아니라 시험 순서가 결과를 정하게 된다.
		this.originalLimit = this.properties.getLimit();
		this.properties.setLimit(TasteVectorBatchService.MAX_BATCH);
	}

	@AfterEach
	void restore() {
		this.properties.setLimit(this.originalLimit);
	}

	@Test
	@DisplayName("설문만 있고 판이 없는 사람에게 판을 만든다 — DAG 가 하던 일")
	void foldsAUserWhoHasNoVectorYet() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId,
				OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE_HEALING", "FOOD");
		assertThat(this.vectors.findByUserIdAndSupersededAtIsNull(userId)).as("돌기 전").isEmpty();

		this.scheduler.runOnce();

		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		assertThat(this.weights.findByIdTasteVectorId(current.getTasteVectorId()))
			.extracting((w) -> w.getId().getCode())
			.containsExactlyInAnyOrder("CAFE_HEALING", "FOOD");
	}

	@Test
	@DisplayName("두 번 돌려도 판은 하나다 — 두 번째는 이미 본 구간이라 새로 안 만든다")
	void runningTwiceKeepsOneCurrentVector() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId,
				OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "FOOD");

		this.scheduler.runOnce();
		int firstVersion = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow().getVersion();
		this.scheduler.runOnce();

		assertThat(this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow().getVersion())
			.as("설문도 행동도 안 바뀌었으면 판 번호가 안 오른다")
			.isEqualTo(firstVersion);
	}
}
