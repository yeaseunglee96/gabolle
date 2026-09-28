package com.gabolle.backend.batch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.batch.application.TasteVectorFoldService;
import com.gabolle.backend.batch.support.BatchPostgresTest;
import com.gabolle.backend.batch.support.TasteVectorFixtures;
import com.gabolle.backend.preference.application.TasteAttributionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 이벤트 <b>하나</b>로 취향을 증분 갱신하는 길 (S15P21E201-1500).
 *
 * <h2>이 시험이 지키는 약속</h2>
 *
 * <b>배치가 만든 값과 소비자가 만든 값이 같아야 한다.</b> 같은 하트 두 개를 배치로 접든
 * 소비자로 하나씩 넣든 {@code raw=2.0 · weight=0.4 · support=2} 가 나와야 한다. 여기가
 * 어긋나면 배치를 걷어내는 날(S15P21E201-1501) 추천이 조용히 달라진다.
 *
 * <p>{@link TasteAttributionService} 를 빈으로 안 받고 직접 만든다 — {@code JdbcTemplate}
 * 하나만 있으면 되는 클래스라, 슬라이스가 그 패키지를 훑는지에 시험이 기대지 않게 한다.
 */
class TasteAttributionIntegrationTest extends BatchPostgresTest {

	private static final OffsetDateTime DAY1 = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final OffsetDateTime DAY2 = OffsetDateTime.of(2026, 8, 2, 0, 0, 0, 0, ZoneOffset.UTC);

	/** 앱 어휘 여섯 중 하나. 조회표가 외래키로 강제하므로 지어낼 수 없다. */
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
	@DisplayName("🔴 하트 둘을 하나씩 넣어도 배치와 같은 값이 된다 — raw 2.0 · weight 0.4 · support 2")
	void twoLikesOneByOneMatchTheBatch() {
		UUID user = userWithVector();

		this.attribution.apply(user, "place_like", taggedPlace(), DAY2);
		this.attribution.apply(user, "place_like", taggedPlace(), DAY2);

		Map<String, Object> row = interactionRow(user);
		assertThat((Double) row.get("raw")).isCloseTo(2.0, within(1e-9));
		// raw = 2.0, K = 3 → 2/(2+3). BehaviorFoldIntegrationTest 의 같은 상황과 같은 값이다.
		assertThat((Double) row.get("weight")).isCloseTo(0.4, within(1e-9));
		assertThat(row.get("support")).isEqualTo(2);
	}

	/**
	 * 🔴 하트 한 번이 이벤트 <b>두 건</b>으로 온다 — 저장 API 가 서버에서, 앱이 분석 이벤트로
	 * 한 번 더. {@code eventId} 가 달라 멱등 장부로는 안 막힌다 (S15P21E201-1485). 상태로 보면
	 * 「이미 하트인데 또 하트」라 바뀌는 것이 없어 저절로 한 번만 세어진다.
	 */
	@Test
	@DisplayName("🔴 같은 장소의 하트가 두 번 와도 한 번만 센다")
	void theSameLikeTwiceCountsOnce() {
		UUID user = userWithVector();
		UUID cafe = taggedPlace();

		this.attribution.apply(user, "place_like", cafe, DAY2);
		this.attribution.apply(user, "place_like", cafe, DAY2);
		this.attribution.apply(user, "place_like", taggedPlace(), DAY2);

		Map<String, Object> row = interactionRow(user);
		assertThat(row.get("support")).as("두 번째 건이 세어지면 손가락 빠른 사람의 취향이 세진다").isEqualTo(2);
		assertThat((Double) row.get("raw")).isCloseTo(2.0, within(1e-9));
	}

	@Test
	@DisplayName("🔴 하트를 끄면 그 몫이 빠진다 — 마지막 하나가 남으면 성분은 남는다")
	void turningOneHeartOffRemovesItsShare() {
		UUID user = userWithVector();
		UUID cafeA = taggedPlace();
		UUID cafeB = taggedPlace();

		this.attribution.apply(user, "place_like", cafeA, DAY2);
		this.attribution.apply(user, "place_like", cafeB, DAY2);
		this.attribution.apply(user, "place_like_removed", cafeA, DAY2);

		Map<String, Object> row = interactionRow(user);
		assertThat(row.get("support")).isEqualTo(1);
		assertThat((Double) row.get("raw")).isCloseTo(1.0, within(1e-9));
	}

	/**
	 * 🔴 뒷받침이 0 이 되면 <b>행을 지운다.</b> {@code ck_user_taste_weight_interaction_has_support}
	 * 가 「행동에서 나왔다면서 관측이 없는」 행을 DB 에서 막으므로, 0 으로 남기려 하면 갱신
	 * 자체가 실패한다. 뜻으로도 맞다 — 반영할 관측이 없으면 그 성분은 없는 것이다.
	 */
	@Test
	@DisplayName("🔴 하트를 전부 끄면 성분이 사라진다 — 아예 안 누른 것과 같다")
	void turningEveryHeartOffRemovesTheComponent() {
		UUID user = userWithVector();
		UUID cafeA = taggedPlace();
		UUID cafeB = taggedPlace();

		this.attribution.apply(user, "place_like", cafeA, DAY2);
		this.attribution.apply(user, "place_like", cafeB, DAY2);
		this.attribution.apply(user, "place_like_removed", cafeA, DAY2);
		this.attribution.apply(user, "place_like_removed", cafeB, DAY2);

		assertThat(interactionRows(user)).as("끈 하트가 남으면 「이제 관심 없다」를 계속 취향으로 읽는다").isEmpty();
	}

	@Test
	@DisplayName("아직 접힌 적 없는 사람은 그냥 넘어간다 — 판을 만드는 것은 배치의 몫이다")
	void usersWithoutAVectorAreSkipped() {
		UUID user = this.fixtures.newUser();

		this.attribution.apply(user, "place_like", taggedPlace(), DAY2);

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM user_taste_vector WHERE user_id = ?", Integer.class,
				user)).isZero();
	}

	/** 설문만 접어 판을 하나 만든다. 소비자는 판을 만들지 않으므로 먼저 있어야 한다. */
	private UUID userWithVector() {
		UUID user = this.fixtures.newUser();
		UUID snapshot = this.fixtures.newUserScopeSnapshot(user, DAY1);
		this.fixtures.selectedCodes(snapshot, "CATEGORY", CAFE);
		this.foldService.fold(user, DAY1);
		return user;
	}

	private UUID taggedPlace() {
		UUID placeId = this.fixtures.newPlace(CAFE);
		this.fixtures.placeTag(placeId, "CATEGORY_TAG", CAFE, "VERIFIED");
		return placeId;
	}

	private Map<String, Object> interactionRow(UUID userId) {
		List<Map<String, Object>> rows = interactionRows(userId);
		assertThat(rows).as("행동 성분이 하나 있어야 한다").hasSize(1);
		return rows.get(0);
	}

	private List<Map<String, Object>> interactionRows(UUID userId) {
		return this.jdbc.queryForList("""
				SELECT w.raw, w.weight, w.support
				  FROM user_taste_weight w
				  JOIN user_taste_vector v ON v.taste_vector_id = w.taste_vector_id
				 WHERE v.user_id = ? AND v.superseded_at IS NULL AND w.evidence = 'INTERACTION'
				""", userId);
	}

}
