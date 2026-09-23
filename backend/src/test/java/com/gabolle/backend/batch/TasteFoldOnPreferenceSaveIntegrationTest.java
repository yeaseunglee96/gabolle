package com.gabolle.backend.batch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.batch.support.BatchPostgresTest;
import com.gabolle.backend.batch.support.TasteVectorFixtures;
import com.gabolle.backend.preference.domain.UserTasteVector;
import com.gabolle.backend.preference.repository.UserTasteVectorRepository;
import com.gabolle.backend.preference.repository.UserTasteWeightRepository;
import com.gabolle.backend.trip.domain.UserPreferenceDefaultsSaved;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설문을 저장하면 새벽 배치를 기다리지 않고 판이 생기는가 (S15P21E201-1515).
 *
 * <p>진짜 PostgreSQL 에서 돈다. 지키려는 것이 커밋의 경계이기 때문이다 — 「커밋 뒤에 돈다」와
 * 「새 트랜잭션으로 쓴다」는 메모리 가짜로는 둘 다 저절로 맞아 보인다. 특히 두 번째가 틀리면
 * 판이 <b>오류 없이 사라지므로</b>, 판을 읽을 때는 트랜잭션 밖의 새 연결로 읽는다.
 */
class TasteFoldOnPreferenceSaveIntegrationTest extends BatchPostgresTest {

	@Autowired
	private ApplicationEventPublisher events;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private UserTasteVectorRepository vectors;

	@Autowired
	private UserTasteWeightRepository weights;

	@Autowired
	private JdbcTemplate jdbc;

	private TasteVectorFixtures fixtures;

	private TransactionTemplate transaction;

	@BeforeEach
	void setUp() {
		this.fixtures = new TasteVectorFixtures(this.jdbc);
		this.transaction = new TransactionTemplate(this.transactionManager);
	}

	@Test
	@DisplayName("설문을 저장하고 커밋하면 그 자리에서 판이 생긴다 — 커밋 전에는 없다")
	void foldsRightAfterCommit() {
		UUID userId = this.fixtures.newUser();

		this.transaction.executeWithoutResult((status) -> {
			UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, aMinuteAgo());
			this.fixtures.selectedCodes(snapshot, "CATEGORY", "CAFE_HEALING", "FOOD");
			this.events.publishEvent(new UserPreferenceDefaultsSaved(userId));

			// 커밋 전에 접으면 롤백될 설문으로 판을 만든다. 여기서는 아직 없어야 한다.
			assertThat(currentVectors(userId)).as("커밋 전").isZero();
		});

		assertThat(currentVectors(userId)).as("커밋 뒤 — 트랜잭션 밖의 새 연결로 센다").isEqualTo(1);
		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		assertThat(this.weights.findByIdTasteVectorId(current.getTasteVectorId()))
			.extracting((w) -> w.getId().getCode())
			.containsExactlyInAnyOrder("CAFE_HEALING", "FOOD");
	}

	@Test
	@DisplayName("롤백된 설문으로는 판을 만들지 않는다")
	void rolledBackSaveMakesNoVector() {
		UUID userId = this.fixtures.newUser();

		this.transaction.executeWithoutResult((status) -> {
			UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, aMinuteAgo());
			this.fixtures.selectedCodes(snapshot, "CATEGORY", "FOOD");
			this.events.publishEvent(new UserPreferenceDefaultsSaved(userId));
			status.setRollbackOnly();
		});

		assertThat(currentVectors(userId)).isZero();
	}

	@Test
	@DisplayName("트랜잭션 밖에서 저장돼도 판이 생긴다 — 이벤트가 조용히 버려지지 않는다")
	void foldsEvenWithoutSurroundingTransaction() {
		UUID userId = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(userId, aMinuteAgo());
		this.fixtures.selectedCodes(snapshot, "CATEGORY", "SEA_BEACH");

		this.events.publishEvent(new UserPreferenceDefaultsSaved(userId));

		assertThat(currentVectors(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("설문을 고쳐 다시 저장하면 새 판이 현재가 되고 옛 판은 내려간다")
	void resaveReplacesCurrentVector() {
		UUID userId = this.fixtures.newUser();
		this.transaction.executeWithoutResult((status) -> {
			UUID first = this.fixtures.newUserScopeSnapshot(userId, aMinuteAgo().minusMinutes(1));
			this.fixtures.selectedCodes(first, "CATEGORY", "FOOD");
			this.events.publishEvent(new UserPreferenceDefaultsSaved(userId));
		});
		int firstVersion = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow().getVersion();

		this.transaction.executeWithoutResult((status) -> {
			UUID second = this.fixtures.newUserScopeSnapshot(userId, aMinuteAgo());
			this.fixtures.selectedCodes(second, "CATEGORY", "NATURE_WALK");
			this.events.publishEvent(new UserPreferenceDefaultsSaved(userId));
		});

		UserTasteVector current = this.vectors.findByUserIdAndSupersededAtIsNull(userId).orElseThrow();
		assertThat(current.getVersion()).isEqualTo(firstVersion + 1);
		assertThat(currentVectors(userId)).as("현재 판은 하나").isEqualTo(1);
		assertThat(this.weights.findByIdTasteVectorId(current.getTasteVectorId()))
			.extracting((w) -> w.getId().getCode())
			.containsExactly("NATURE_WALK");
	}

	private int currentVectors(UUID userId) {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM user_taste_vector WHERE user_id = ? AND superseded_at IS NULL",
				Integer.class, userId);
		return (count == null) ? 0 : count;
	}

	/** 접기는 「지금까지 저장된 것」만 본다. 설문 시각이 지금보다 뒤면 안 읽힌다. */
	private static OffsetDateTime aMinuteAgo() {
		return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
	}
}
