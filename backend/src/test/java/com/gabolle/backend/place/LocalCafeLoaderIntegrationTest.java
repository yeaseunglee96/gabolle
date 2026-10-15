package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.LocalCafeLoader;
import com.gabolle.backend.place.loader.LocalCafeRow;
import com.gabolle.backend.place.loader.LocalCafeRow.Origin;
import com.gabolle.backend.place.loader.LocalPickLoader;
import com.gabolle.backend.place.loader.RedtablePlaceLoader;
import com.gabolle.backend.place.loader.SamePlaceReport;
import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 카페가 실제 DB 에 들어가는지 (S15P21E201-2059). 진짜 PostgreSQL 이라 유일 색인({@code uq_place_source} ·
 * {@code uq_place_feature_keyed})·외래키가 그대로 적용된다.
 *
 * <p>맛집 적재기와 같은 엔진({@code LocalPickLoader})이라 흐름의 세부(숨긴 장소·합쳐진 장소·구/동 하나·같은 곳 검사)는
 * {@code CertifiedRestaurantLoaderIntegrationTest} 가 본다. 여기서는 <b>카페만의 것</b>을 본다 — 출처별 장소 번호, 장소 갈래를 안 바꾸는 것,
 * 레드테이블의 방송·수상 표시, 사진, 다른 적재기와의 번호 일치.
 */
class LocalCafeLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "cafes-test";

	/** 이 시험이 넣는 원천 번호(콘텐츠 번호·식당 번호)의 머리 — 실제 번호와 안 겹치게 아주 큰 수로 시작한다. */
	private static final String ID_PREFIX = "992059";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private LocalCafeLoader loader;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
		deleteWhatTheLoaderWrote();
	}

	@AfterEach
	void tearDown() {
		deleteWhatTheLoaderWrote();
		this.fixture.cleanUp();
	}

	// ── 새 카페 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 관광공사 카페는 TOURAPI 장소로, 레드테이블 카페는 REDTABLE 장소로 들어가고 두 번 돌려도 행이 안 는다")
	void createsPlacesWithTheirOwnSourceAndIsIdempotent() {
		List<LocalCafeRow> rows = List.of(
				row("KTO", "1", "관광카페", 35.4101, 129.4101, "ZZ카페구", "ZZ가동", null, "http://tong.visitkorea.or.kr/cms/resource/1/a.jpg",
						"한국관광공사 공공누리 제3유형", Origin.KTO),
				row("RT", "2", "레드카페", 35.4201, 129.4201, "ZZ카페구", "ZZ나동", null, null, null, Origin.REDTABLE));

		LocalPickLoader.Result first = load(rows);
		assertThat(first.created()).isEqualTo(2);
		assertThat(first.withPhoto()).isEqualTo(1);

		// 장소 번호는 그 원천의 적재기(TourApiPlaceLoader · RedtablePlaceLoader)가 쓰는 식이다 — 같은 가게를 서로 모르고 두 번 넣지 않는다.
		UUID kto = TourApiPlaceLoader.placeIdOf(ID_PREFIX + "1");
		UUID redtable = RedtablePlaceLoader.placeIdOf(ID_PREFIX + "2");
		Map<String, Object> ktoPlace = this.jdbcTemplate.queryForMap(
				"SELECT place_id, category, source_type, source_id, dataset_version, photo_url, photo_source, curation_status FROM place WHERE place_id = ?",
				kto);
		assertThat(ktoPlace.get("category")).isEqualTo("CAFE_HEALING");
		assertThat(ktoPlace.get("source_type")).isEqualTo("TOURAPI");
		assertThat(ktoPlace.get("source_id")).isEqualTo(ID_PREFIX + "1");
		assertThat(ktoPlace.get("dataset_version")).isEqualTo(DATASET);
		assertThat(ktoPlace.get("curation_status")).isEqualTo("CURATED");
		// 평문 http 는 앱이 못 연다 — 아는 호스트(관광공사)는 https 로 바꿔 넣는다.
		assertThat(ktoPlace.get("photo_url")).isEqualTo("https://tong.visitkorea.or.kr/cms/resource/1/a.jpg");
		assertThat(ktoPlace.get("photo_source")).isEqualTo("한국관광공사 공공누리 제3유형");

		Map<String, Object> redtablePlace = this.jdbcTemplate
				.queryForMap("SELECT category, source_type, source_id, photo_url, photo_source FROM place WHERE place_id = ?", redtable);
		assertThat(redtablePlace.get("category")).isEqualTo("CAFE_HEALING");
		assertThat(redtablePlace.get("source_type")).isEqualTo("REDTABLE");
		assertThat(redtablePlace.get("source_id")).isEqualTo(ID_PREFIX + "2");
		// 레드테이블 카페는 사진이 없다 — 이용 조건을 확인하지 못했다.
		assertThat(redtablePlace.get("photo_url")).isNull();
		assertThat(redtablePlace.get("photo_source")).isNull();

		assertThat(featureKeys(kto)).containsExactlyInAnyOrder("CATEGORY_TAG:CAFE_HEALING", "INTEREST_TAG:LOCAL_CAFE",
				"ADMIN_AREA_TAG:ZZ카페구/ZZ가동", "CERTIFICATION_TAG:KTO_CAFE");
		assertThat(featureKeys(redtable)).containsExactlyInAnyOrder("CATEGORY_TAG:CAFE_HEALING", "INTEREST_TAG:LOCAL_CAFE",
				"ADMIN_AREA_TAG:ZZ카페구/ZZ나동", "CERTIFICATION_TAG:REDTABLE_FEATURED");

		long placesAfterFirst = placeCount();
		long featuresAfterFirst = featureCount();
		LocalPickLoader.Result second = load(rows);
		assertThat(second.created()).isZero();
		assertThat(second.alreadyThere()).isEqualTo(2);
		assertThat(second.featuresAdded()).isZero();
		assertThat(placeCount()).isEqualTo(placesAfterFirst);
		assertThat(featureCount()).isEqualTo(featuresAfterFirst);
	}

	@Test
	@DisplayName("표식의 값은 읽을 수 있는 JSON 이다 — 출처 이름은 한국어로, 방송·수상 표시는 레드테이블(방송·수상)에만 붙는다")
	void featureValuesCarryTheSourceAndTheBroadcastFlag() {
		load(List.of(
				row("RT", "1", "평범카페", 35.4301, 129.4301, "ZZ카페구", "ZZ다동", null, null, null, Origin.REDTABLE),
				row("RT", "2", "방송카페", 35.4401, 129.4401, "ZZ카페구", "ZZ라동", null, null, null, Origin.REDTABLE_MEDIA),
				row("KTO", "3", "겹침카페", 35.4501, 129.4501, "ZZ카페구", "ZZ마동", null, null, null, Origin.KTO, Origin.REDTABLE_MEDIA)));

		assertThat(certValue(RedtablePlaceLoader.placeIdOf(ID_PREFIX + "1"), "REDTABLE_FEATURED")).isEqualTo("{\"source\": \"레드테이블\"}");
		assertThat(certValue(RedtablePlaceLoader.placeIdOf(ID_PREFIX + "2"), "REDTABLE_FEATURED"))
				.isEqualTo("{\"source\": \"레드테이블\", \"broadcastOrAward\": true}");
		UUID both = TourApiPlaceLoader.placeIdOf(ID_PREFIX + "3");
		assertThat(featureKeys(both)).contains("CERTIFICATION_TAG:KTO_CAFE", "CERTIFICATION_TAG:REDTABLE_FEATURED");
		assertThat(certValue(both, "KTO_CAFE")).isEqualTo("{\"source\": \"한국관광공사\"}");
		assertThat(certValue(both, "REDTABLE_FEATURED")).isEqualTo("{\"source\": \"레드테이블\", \"broadcastOrAward\": true}");
		// 갈래 표식은 목록이 카페로 소개한 사실(확인됨), 구·동은 좌표에서 계산한 값(추정).
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT evidence_status FROM place_feature
				 WHERE place_id = ? AND feature_type = 'INTEREST_TAG' AND feature_key = 'LOCAL_CAFE'
				""", String.class, both)).isEqualTo("VERIFIED");
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT evidence_status FROM place_feature
				 WHERE place_id = ? AND feature_type = 'ADMIN_AREA_TAG'
				""", String.class, both)).isEqualTo("ESTIMATED");
	}

	// ── 이미 있는 장소 ─────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 이미 있는 음식 장소에는 카페 표식만 더한다 — 장소 갈래(FOOD)도, 사진도, 이름도, 이미 단 맛집 표식도 그대로다")
	void anExistingFoodPlaceKeepsEverythingButGainsTheCafeMarks() {
		UUID existing = this.fixture.insertPlace("음식카페", null, "FOOD", 35.4601, 129.4601);
		this.jdbcTemplate.update("UPDATE place SET photo_url = 'https://example.invalid/own.jpg', photo_source = '원래 출처' WHERE place_id = ?",
				existing);
		this.fixture.insertTagFeature(existing, "INTEREST_TAG", "LOCAL_RESTAURANT", "VERIFIED", "true");

		LocalPickLoader.Result result = load(List.of(row("KTO", "4", "음식카페(파일 이름)", 35.4601, 129.4601, "ZZ카페구", "ZZ바동", existing,
				"http://tong.visitkorea.or.kr/cms/resource/1/b.jpg", "한국관광공사 공공누리 제3유형", Origin.KTO)));

		assertThat(result.created()).isZero();
		assertThat(result.markedExisting()).isEqualTo(1);
		assertThat(result.withPhoto()).as("이미 있는 장소의 사진은 건드리지 않는다").isZero();
		assertThat(this.jdbcTemplate.queryForMap("SELECT category, name_ko, photo_url, photo_source FROM place WHERE place_id = ?", existing))
				.containsEntry("category", "FOOD").containsEntry("name_ko", this.fixture.prefix() + "음식카페")
				.containsEntry("photo_url", "https://example.invalid/own.jpg").containsEntry("photo_source", "원래 출처");
		// 이 적재기가 단 것 + 미리 있던 맛집 표식(지우지 않는다).
		assertThat(allFeatureKeys(existing)).containsExactlyInAnyOrder("INTEREST_TAG:LOCAL_RESTAURANT", "INTEREST_TAG:LOCAL_CAFE",
				"ADMIN_AREA_TAG:ZZ카페구/ZZ바동", "CERTIFICATION_TAG:KTO_CAFE");
	}

	@Test
	@DisplayName("🔴 다른 적재기가 이미 만든 같은 번호의 장소는 새로 만들지 않고 그 장소에 표식을 단다 — 번호 식이 같아서 알아본다")
	void aPlaceTheOtherLoaderAlreadyMadeIsRecognisedByItsNumber() {
		UUID made = TourApiPlaceLoader.placeIdOf(ID_PREFIX + "5");
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, address, lat, lng, created_at, source_type, source_id, collected_at, dataset_version)
				VALUES (?, ?, 'FOOD', '부산광역시 시험로 5', 35.4701, 129.4701, now(), 'TOURAPI', ?, now(), 'tourapi-before')
				""", made, this.fixture.prefix() + "먼저온카페", ID_PREFIX + "5");
		try {
			LocalPickLoader.Result result = load(List.of(row("KTO", "5", "먼저온카페", 35.4701, 129.4701, "ZZ카페구", "ZZ사동", null, null,
					null, Origin.KTO)));

			assertThat(result.created()).isZero();
			assertThat(result.alreadyThere()).isEqualTo(1);
			assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place WHERE source_type = 'TOURAPI' AND source_id = ?",
					Long.class, ID_PREFIX + "5")).isEqualTo(1L);
			assertThat(featureKeys(made)).containsExactlyInAnyOrder("INTEREST_TAG:LOCAL_CAFE", "ADMIN_AREA_TAG:ZZ카페구/ZZ사동",
					"CERTIFICATION_TAG:KTO_CAFE");
			// 그 장소의 갈래도 안 바꾼다(FOOD 그대로).
			assertThat(this.jdbcTemplate.queryForObject("SELECT category FROM place WHERE place_id = ?", String.class, made))
					.isEqualTo("FOOD");
		}
		finally {
			// 시험이 중간에 실패해도 남기지 않는다 — (TOURAPI, 번호) 는 유일해서 남으면 다음 실행이 걸린다.
			this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id = ?", made);
			this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", made);
		}
	}

	@Test
	@DisplayName("🔴 existingPlaceId 의 장소가 이 DB 에 없으면 아무것도 안 쓰고 센다 — 숨긴 장소에는 표식을 안 단다")
	void missingAndHiddenExistingPlacesAreNotMarked() {
		UUID hidden = this.fixture.insertPlace("숨은카페", null, "CAFE_HEALING", 35.4801, 129.4801);
		this.jdbcTemplate.update("UPDATE place SET curation_status = 'HIDDEN' WHERE place_id = ?", hidden);

		LocalPickLoader.Result result = load(List.of(
				row("RT", "6", "없는카페", 35.4802, 129.4802, "ZZ카페구", "ZZ아동", UUID.randomUUID(), null, null, Origin.REDTABLE),
				row("RT", "7", "숨은카페", 35.4801, 129.4801, "ZZ카페구", "ZZ자동", hidden, null, null, Origin.REDTABLE)));

		assertThat(result.missingExisting()).isEqualTo(1);
		assertThat(result.missingExistingSourceIds()).containsExactly("RT" + ID_PREFIX + "6");
		assertThat(result.notUsable()).isEqualTo(1);
		assertThat(result.created()).isZero();
		assertThat(allFeatureKeys(hidden)).isEmpty();
	}

	// ── 같은 곳 검사 ───────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 번호가 달라도 같은 이름이 150m 안에 이미 있으면 새로 만들지 않고 그 장소에 표식을 단다 — 같은 덩어리의 쌍둥이도 하나로 합친다")
	void sameNameNearbyIsNotDuplicated() {
		UUID nearby = this.fixture.insertPlace("이웃카페", null, "FOOD", 35.4901, 129.4901);

		SamePlaceReport report = new SamePlaceReport();
		LocalPickLoader.Result result = this.loader.saveChunk(List.of(
				row("KTO", "8", this.fixture.prefix() + "이웃카페", 35.49011, 129.49011, "ZZ카페구", "ZZ차동", null, null, null, Origin.KTO),
				row("RT", "9", "쌍둥이카페", 35.4951, 129.4951, "ZZ카페구", "ZZ카동", null, null, null, Origin.REDTABLE),
				row("KTO", "10", "쌍둥이카페", 35.49511, 129.49511, "ZZ카페구", "ZZ카동", null, null, null, Origin.KTO)),
				DATASET, OffsetDateTime.now(), report);

		assertThat(result.created()).as("쌍둥이 둘은 장소 하나").isEqualTo(1);
		assertThat(result.markedSamePlace()).isEqualTo(2);
		assertThat(report.reviews()).isEmpty();
		assertThat(featureKeys(nearby)).contains("INTEREST_TAG:LOCAL_CAFE", "CERTIFICATION_TAG:KTO_CAFE");
		UUID twin = RedtablePlaceLoader.placeIdOf(ID_PREFIX + "9");
		assertThat(featureKeys(twin)).contains("CERTIFICATION_TAG:REDTABLE_FEATURED", "CERTIFICATION_TAG:KTO_CAFE");
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private LocalPickLoader.Result load(List<LocalCafeRow> rows) {
		return this.loader.saveChunk(rows, DATASET, OffsetDateTime.now(), new SamePlaceReport());
	}

	/** 이 적재기가 단 표식만. */
	private List<String> featureKeys(UUID placeId) {
		return this.jdbcTemplate.queryForList("""
				SELECT feature_type || ':' || feature_key FROM place_feature
				 WHERE place_id = ? AND source_type = 'BUSAN_LOCAL_CAFE'
				""", String.class, placeId);
	}

	/** 출처를 가리지 않고 장소에 붙은 표식 전부. */
	private List<String> allFeatureKeys(UUID placeId) {
		return this.jdbcTemplate.queryForList("SELECT feature_type || ':' || feature_key FROM place_feature WHERE place_id = ?", String.class,
				placeId);
	}

	private String certValue(UUID placeId, String key) {
		return this.jdbcTemplate.queryForObject("""
				SELECT value::text FROM place_feature
				 WHERE place_id = ? AND feature_type = 'CERTIFICATION_TAG' AND feature_key = ?
				""", String.class, placeId, key);
	}

	private long placeCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM place WHERE dataset_version = ?", Long.class, DATASET);
	}

	private long featureCount() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = ?",
				Long.class, DATASET);
	}

	/** 이 시험이 적재기로 넣은 것만 지운다 — 표식을 먼저, 장소를 나중에. */
	private void deleteWhatTheLoaderWrote() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'BUSAN_LOCAL_CAFE' AND source_version = ?", DATASET);
		this.jdbcTemplate.update("DELETE FROM place WHERE dataset_version = ? AND source_type IN ('TOURAPI', 'REDTABLE')", DATASET);
	}

	/**
	 * 시험 줄 하나. 이름 앞에 이 실행만의 표({@link PlaceFixture#prefix()})를 붙인다 — 같은 DB 에 다른 시험·실제 자료가 있어도 같은 곳
	 * 검사(이름이 같고 150m 안)에 우연히 걸리지 않게. 이미 붙어 있으면 다시 붙이지 않는다.
	 */
	private LocalCafeRow row(String prefix, String suffix, String name, double lat, double lng, String gu, String dong,
			UUID existingPlaceId, String photoUrl, String photoSource, Origin... origins) {
		String prefixed = name.startsWith(this.fixture.prefix()) ? name : this.fixture.prefix() + name;
		return new LocalCafeRow(prefix + ID_PREFIX + suffix, prefixed, lat, lng, "부산광역시 시험로 " + suffix, null, gu, dong,
				List.of(origins), null, existingPlaceId, existingPlaceId == null ? null : "FOOD", photoUrl, photoSource);
	}
}
