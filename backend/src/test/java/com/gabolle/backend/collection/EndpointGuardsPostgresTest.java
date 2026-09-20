package com.gabolle.backend.collection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.collection.domain.Collection;
import com.gabolle.backend.collection.domain.CollectionItem;
import com.gabolle.backend.collection.repository.CollectionItemRepository;
import com.gabolle.backend.collection.repository.CollectionRepository;
import com.gabolle.backend.place.repository.SavedPlaceRepository;
import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;
import com.gabolle.backend.recommendation.repository.RecommendationPlaceActionRepository;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ReliabilitySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마이그레이션에 적어 둔 제약이 진짜 PostgreSQL 위에서 실제로 막는지 본다. 컨트롤러를 홀로
 * 세우는 시험으로는 상태 코드까지만 확인되고 제약은 한 번도 돌지 않는다.
 *
 * <p>보는 것은 둘이다 — 같은 것을 두 번 넣어도 성공이고 행은 하나인가(멱등), 그리고 종류에
 * 맞지 않는 행을 DB 가 거부하는가.
 *
 * <p>도커도 {@code GABOLLE_TEST_DB_URL} 도 없으면 조용히 통과하지 않고 건너뜀으로 표시된다
 * ({@link PostgresAvailableCondition}).
 */
@SpringBootTest(classes = ReliabilitySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class EndpointGuardsPostgresTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T09:00:00Z");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CollectionRepository collections;

	@Autowired
	private CollectionItemRepository items;

	@Autowired
	private SavedPlaceRepository savedPlaces;

	@Autowired
	private RecommendationPlaceActionRepository actions;

	private UUID userId;

	private UUID placeId;

	private UUID tripId;

	private UUID collectionId;

	/**
	 * 부모 행을 직접 넣는다. {@code app_user}·{@code place}·{@code trip} 은 이 슬라이스가 훑지
	 * 않는 표라 엔티티가 없지만, Flyway 가 만들어 둔 표는 그대로 있다.
	 */
	@BeforeEach
	void seed() {
		this.userId = UUID.randomUUID();
		this.placeId = UUID.randomUUID();
		this.tripId = UUID.randomUUID();
		this.collectionId = UUID.randomUUID();

		this.jdbc.update("""
				INSERT INTO app_user (user_id, display_name, language, personalization_mode, status,
						created_at, updated_at)
				VALUES (?, '시험 사용자', 'ko', 'OFF', 'ACTIVE', ?, ?)
				""", this.userId, NOW, NOW);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '시험 장소', ?)",
				this.placeId, NOW);
		this.jdbc.update("""
				INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, created_at, updated_at)
				VALUES (?, ?, DATE '2026-10-01', DATE '2026-10-03', ?, ?)
				""", this.tripId, this.userId, NOW, NOW);

		this.collections.save(Collection.of(this.collectionId, this.userId, "부산 카페", null, NOW));
	}

	// ── 멱등 ─────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("완료 기준 — 같은 하트를 두 번 넣어도 예외가 아니고 행은 하나다")
	void savingTheSamePlaceTwiceIsNotAnError() {
		assertThat(this.savedPlaces.insertIfAbsent(UUID.randomUUID(), this.userId, this.placeId, NOW))
				.as("처음이라 한 행이 들어가야 한다")
				.isEqualTo(1);

		assertThatCode(() -> this.savedPlaces.insertIfAbsent(UUID.randomUUID(), this.userId, this.placeId, NOW))
				.as("그전에는 여기서 유일 제약 위반이 올라와 500 이 됐다")
				.doesNotThrowAnyException();

		assertThat(this.savedPlaces.findByUserIdOrderByCreatedAtDesc(this.userId, PageRequest.of(0, 10)))
				.as("두 번 눌렀다고 목록에 같은 장소가 두 번 뜨면 안 된다")
				.hasSize(1);
	}

	@Test
	@DisplayName("완료 기준 — 같은 장소를 한 컬렉션에 두 번 담아도 항목은 하나다")
	void addingTheSamePlaceToACollectionTwiceKeepsOneItem() {
		this.items.insertPlaceItemIfAbsent(UUID.randomUUID(), this.collectionId, this.placeId, null, 0, NOW);

		assertThatCode(() -> this.items.insertPlaceItemIfAbsent(UUID.randomUUID(), this.collectionId,
				this.placeId, "다시 담기", 1, NOW))
				.doesNotThrowAnyException();

		List<CollectionItem> found = this.items
				.findByCollectionIdOrderByPositionAscCreatedAtAsc(this.collectionId, PageRequest.of(0, 10));
		assertThat(found).hasSize(1);
		assertThat(found.get(0).getKind()).isEqualTo(CollectionItem.Kind.PLACE);
	}

	@Test
	@DisplayName("완료 기준 — 같은 후보의 판단을 두 번 적으면 덮어쓰고 행은 하나다")
	void decidingTheSameCandidateTwiceOverwrites() {
		UUID companion = this.userId;
		this.actions.upsert(UUID.randomUUID(), this.tripId, this.placeId, "SAVED", companion, NOW);
		this.actions.upsert(UUID.randomUUID(), this.tripId, this.placeId, "EXCLUDED", companion,
				NOW.plusMinutes(1));

		List<RecommendationPlaceAction> found = this.actions.findByTripId(this.tripId, PageRequest.of(0, 10));
		assertThat(found).as("동행자 둘이 동시에 눌러도 그 여행의 판단은 장소마다 하나다").hasSize(1);
		assertThat(found.get(0).getAction())
				.as("마지막에 누른 사람의 판단이 지금의 판단이다")
				.isEqualTo(RecommendationPlaceAction.Action.EXCLUDED);
	}

	// ── 제약 ─────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("완료 기준 — 장소를 가리킨다면서 place_id 가 빈 행은 DB 가 거부한다")
	@Transactional
	void placeItemWithoutAPlaceIsRejectedByTheDatabase() {
		assertThatThrownBy(() -> this.jdbc.update("""
				INSERT INTO collection_item (collection_item_id, collection_id, kind, "position",
						created_at, updated_at)
				VALUES (?, ?, 'PLACE', 0, ?, ?)
				""", UUID.randomUUID(), this.collectionId, NOW, NOW))
				.as("ck_collection_item_place_shape 가 없으면 화면이 못 그리는 행이 조용히 생긴다")
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("완료 기준 — 직접 적은 항목인데 이름이 공백이면 DB 가 거부한다")
	@Transactional
	void customItemWithoutANameIsRejectedByTheDatabase() {
		assertThatThrownBy(() -> this.jdbc.update("""
				INSERT INTO collection_item (collection_item_id, collection_id, kind, name, "position",
						created_at, updated_at)
				VALUES (?, ?, 'CUSTOM', '   ', 0, ?, ?)
				""", UUID.randomUUID(), this.collectionId, NOW, NOW))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 상한 ─────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("완료 기준 — 목록 조회가 요청한 개수만 돌려준다 (상한이 실제로 먹는다)")
	void listQueriesRespectTheRequestedCeiling() {
		for (int i = 0; i < 5; i++) {
			UUID other = UUID.randomUUID();
			this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, ?, ?)",
					other, "시험 장소 " + i, NOW);
			this.savedPlaces.insertIfAbsent(UUID.randomUUID(), this.userId, other, NOW.plusSeconds(i));
		}

		assertThat(this.savedPlaces.findByUserIdOrderByCreatedAtDesc(this.userId, PageRequest.of(0, 3)))
				.as("상한을 안 주던 시절에는 이 조회가 계정에 쌓인 전부를 읽었다")
				.hasSize(3);
	}
}
