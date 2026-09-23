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
import com.gabolle.backend.event.application.EventConsumptionService;
import com.gabolle.backend.event.application.EventConsumptionService.ConsumedEvent;
import com.gabolle.backend.preference.application.TasteAttributionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 같은 이벤트가 두 번 와도 <b>한 번만</b> 반영된다 (S15P21E201-1501).
 *
 * <h2>이 시험이 생긴 이유</h2>
 *
 * 예전 소비자는 JPA {@code saveAndFlush} 가 중복에서 기본키 위반을 던지기를 기대했다. 그런데
 * 번호를 직접 넣는 엔티티라 Spring Data 가 {@code merge} 로 저장했고, merge 는 이미 있는 행을
 * 만나면 <b>조용히 넘어갔다.</b> 중복을 잡는 {@code catch} 는 한 번도 안 돌았고, 재전송된
 * 이벤트가 취향에 <b>한 번 더</b> 반영됐다.
 *
 * <p>상태 이벤트(하트·끔)는 상태 표가 「이미 하트인데 또 하트」를 막아 줘서 멀쩡했다.
 * <b>보기·방문</b>이 문제였다 — 반복이 뜻을 가지는 이벤트라 올 때마다 더하게 돼 있어서, 재전송이
 * 그대로 두 번 세어졌다. 그래서 이 시험은 {@code place_view} 로 본다.
 *
 * <p>{@link EventConsumptionService} 를 빈으로 안 받고 직접 만든다 — {@code JdbcTemplate} 과
 * 귀속 서비스만 있으면 되고, 슬라이스가 그 패키지를 훑는지에 시험이 기대지 않게 한다.
 */
class LedgerAndTasteOneTransactionIntegrationTest extends BatchPostgresTest {

	private static final OffsetDateTime DAY1 = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final OffsetDateTime DAY2 = OffsetDateTime.of(2026, 8, 2, 0, 0, 0, 0, ZoneOffset.UTC);

	private static final String CAFE = "CAFE_HEALING";

	@Autowired
	private TasteVectorFoldService foldService;

	@Autowired
	private JdbcTemplate jdbc;

	private TasteVectorFixtures fixtures;

	private EventConsumptionService consumption;

	@BeforeEach
	void setUp() {
		this.fixtures = new TasteVectorFixtures(this.jdbc);
		this.consumption = new EventConsumptionService(this.jdbc, new TasteAttributionService(this.jdbc));
	}

	@Test
	@DisplayName("🔴 같은 보기 이벤트가 두 번 와도 취향에는 한 번만 더해진다 — 재전송이 두 번 세어지지 않는다")
	void aRedeliveredViewIsCountedOnce() {
		UUID user = userWithVector();
		UUID cafe = taggedPlace();
		ConsumedEvent view = view(UUID.randomUUID(), user, cafe);

		assertThat(this.consumption.recordFirstTime(view)).as("처음 온 것은 반영한다").isTrue();
		assertThat(this.consumption.recordFirstTime(view)).as("같은 번호가 또 오면 반영하지 않는다").isFalse();

		Map<String, Object> row = interactionRow(user);
		// place_view 하나 = 0.1. 두 번 세어졌다면 0.2 다.
		assertThat((Double) row.get("raw")).as("재전송이 한 번 더 더해지면 0.2 가 된다").isCloseTo(0.1, within(1e-9));
		assertThat(row.get("support")).isEqualTo(1);
		assertThat(ledgerRows(view.eventId())).as("장부도 한 줄이다").isEqualTo(1);
	}

	/** 번호가 다르면 같은 장소를 두 번 본 것이다 — 보기는 반복이 뜻을 가진다. */
	@Test
	@DisplayName("번호가 다른 보기 둘은 둘 다 더해진다 — 막는 것은 «같은 이벤트» 이지 «같은 장소» 가 아니다")
	void twoDistinctViewsAreBothCounted() {
		UUID user = userWithVector();
		UUID cafe = taggedPlace();

		this.consumption.recordFirstTime(view(UUID.randomUUID(), user, cafe));
		this.consumption.recordFirstTime(view(UUID.randomUUID(), user, cafe));

		Map<String, Object> row = interactionRow(user);
		assertThat((Double) row.get("raw")).isCloseTo(0.2, within(1e-9));
		assertThat(row.get("support")).isEqualTo(2);
	}

	/** 취향과 무관한 이벤트도 장부에는 적힌다 — 장부는 「받았다」의 기록이다. */
	@Test
	@DisplayName("사람이나 장소가 없는 이벤트는 장부에만 적고 취향은 안 건드린다")
	void eventsWithoutAPlaceOnlyGoToTheLedger() {
		UUID eventId = UUID.randomUUID();
		ConsumedEvent tripCreated = new ConsumedEvent(eventId, "trip_created", "k", "g", 0, 0L, DAY2, null, null, DAY2);

		assertThat(this.consumption.recordFirstTime(tripCreated)).isTrue();
		assertThat(ledgerRows(eventId)).isEqualTo(1);
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

	private static ConsumedEvent view(UUID eventId, UUID userId, UUID placeId) {
		return new ConsumedEvent(eventId, "place_view", userId.toString(), "test-group", 0, 0L, DAY2, userId, placeId,
				DAY2);
	}

	private Map<String, Object> interactionRow(UUID userId) {
		List<Map<String, Object>> rows = this.jdbc.queryForList("""
				SELECT w.raw, w.support
				  FROM user_taste_weight w
				  JOIN user_taste_vector v ON v.taste_vector_id = w.taste_vector_id
				 WHERE v.user_id = ? AND v.superseded_at IS NULL AND w.evidence = 'INTERACTION'
				""", userId);
		assertThat(rows).as("행동 성분이 하나 있어야 한다").hasSize(1);
		return rows.get(0);
	}

	private Integer ledgerRows(UUID eventId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM event_consumption WHERE event_id = ?", Integer.class,
				eventId);
	}

}
