package com.gabolle.backend.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.batch.application.TasteDecayProperties;
import com.gabolle.backend.batch.application.TasteDecayService;
import com.gabolle.backend.batch.application.TasteVectorFoldService;
import com.gabolle.backend.batch.support.BatchPostgresTest;
import com.gabolle.backend.batch.support.TasteVectorFixtures;
import com.gabolle.backend.preference.application.TasteAttributionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 옛 행동 신호가 실제로 옅어지는가 (S15P21E201-1501).
 *
 * <p>계수를 <b>0.5</b> 로 크게 잡는다. 운영값(0.97)으로는 한 번 돌려서 눈에 띄는 차이가 안
 * 나고, 이 시험이 보려는 것은 「얼마나」가 아니라 <b>「raw 에 곱하고 weight 를 다시 만드는가」</b>
 * 이기 때문이다.
 */
class TasteDecayIntegrationTest extends BatchPostgresTest {

	private static final OffsetDateTime DAY1 = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final OffsetDateTime DAY2 = OffsetDateTime.of(2026, 8, 2, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);

	private static final String CAFE = "CAFE_HEALING";

	@Autowired
	private TasteVectorFoldService foldService;

	@Autowired
	private JdbcTemplate jdbc;

	private TasteVectorFixtures fixtures;

	private TasteAttributionService attribution;

	@BeforeEach
	void setUp() {
		this.fixtures = new TasteVectorFixtures(this.jdbc);
		this.attribution = new TasteAttributionService(this.jdbc);
	}

	@Test
	@DisplayName("🔴 행동 성분의 raw 가 계수만큼 줄고 weight 가 다시 만들어진다")
	void decayShrinksRawAndRebuildsWeight() {
		UUID user = userWithLikes();

		// 줄기 전: raw = 2.0, weight = 2/(2+3) = 0.4
		assertThat((Double) row(user, "INTERACTION").get("raw")).isCloseTo(2.0, within(1e-9));

		decayService(0.5).decayOnce();

		Map<String, Object> after = row(user, "INTERACTION");
		assertThat((Double) after.get("raw")).as("raw 에 곱해야 한다").isCloseTo(1.0, within(1e-9));
		// 🔴 weight 는 raw 에서 다시 만든 값이어야 한다 — 0.4 에 0.5 를 곱한 0.2 가 아니다.
		//    1/(1+3) = 0.25.
		assertThat((Double) after.get("weight")).as("눌러 담은 값에 곱하면 0.2 가 나온다").isCloseTo(0.25, within(1e-9));
	}

	/** 사람이 직접 고른 값은 시간이 지났다고 옅어질 이유가 없다. */
	@Test
	@DisplayName("🔴 설문 성분은 안 건드린다")
	void surveyComponentsAreLeftAlone() {
		UUID user = userWithLikes();
		Map<String, Object> before = row(user, "SURVEY");

		decayService(0.5).decayOnce();

		Map<String, Object> after = row(user, "SURVEY");
		assertThat((Double) after.get("weight")).isEqualTo((Double) before.get("weight"));
		assertThat((Double) after.get("raw")).isEqualTo((Double) before.get("raw"));
	}

	/** 설문 하나와 하트 둘. 행동 성분(raw 2.0)과 설문 성분이 함께 생긴다. */
	private UUID userWithLikes() {
		UUID user = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(user, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", CAFE);
		this.foldService.fold(user, DAY1);

		this.attribution.apply(user, "place_like", taggedPlace(), DAY2);
		this.attribution.apply(user, "place_like", taggedPlace(), DAY2);
		return user;
	}

	private UUID taggedPlace() {
		UUID placeId = this.fixtures.newPlace(CAFE);
		this.fixtures.placeTag(placeId, "CATEGORY_TAG", CAFE, "VERIFIED");
		return placeId;
	}

	private TasteDecayService decayService(double dailyFactor) {
		TasteDecayProperties properties = new TasteDecayProperties();
		properties.setDailyFactor(dailyFactor);
		return new TasteDecayService(this.jdbc, properties, CLOCK);
	}

	private Map<String, Object> row(UUID userId, String evidence) {
		return this.jdbc.queryForMap("""
				SELECT w.raw, w.weight, w.support
				  FROM user_taste_weight w
				  JOIN user_taste_vector v ON v.taste_vector_id = w.taste_vector_id
				 WHERE v.user_id = ? AND v.superseded_at IS NULL AND w.evidence = ?
				""", userId, evidence);
	}

}
