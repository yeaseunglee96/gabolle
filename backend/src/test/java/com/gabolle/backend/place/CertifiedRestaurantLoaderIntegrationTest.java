package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.loader.CertifiedRestaurantLoader;
import com.gabolle.backend.place.loader.CertifiedRestaurantRow;
import com.gabolle.backend.place.loader.SamePlaceReport;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 공공 인증 식당이 실제 DB 에 들어가는지 (S15P21E201-2051). 가짜 DB 가 아니라 진짜 PostgreSQL 이라 허용 목록(CHECK)·유일 색인·
 * 외래키가 그대로 적용된다 — 마이그레이션이 갈래를 빠뜨리면 여기서 첫 줄부터 거절된다.
 */
class CertifiedRestaurantLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "certified-test";

	/** 이 시험이 넣는 번호의 머리 — 실제 번호(BF·MB·TX 로 시작)와 안 겹친다. */
	private static final String ID_PREFIX = "ZZ2051";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CertifiedRestaurantLoader loader;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
		deleteWhatTheLoaderWrote();
	}

	@AfterEach
	void tearDown() {
		// 적재기가 단 표식을 먼저 지워야 표식이 붙은 시험용 장소를 지울 수 있다(외래키).
		deleteWhatTheLoaderWrote();
		this.fixture.cleanUp();
	}

	// ── 새 장소 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 새 식당은 장소와 표식 넷이 들어가고(갈래·맛집·구동·인증 목록), 두 번 돌려도 행이 안 는다")
	void createsPlaceAndMarksAndIsIdempotent() {
		List<CertifiedRestaurantRow> rows = List.of(
				row("1", "새국밥", 35.3001, 129.3001, "ZZ시험구", "ZZ가동", null, "https://example.invalid/a.jpg",
						"출처 : 시험", Source.BUSAN_FOOD_INFO, Source.MODEL_RESTAURANT),
				row("2", "새횟집", 35.3101, 129.3101, "ZZ시험구", "ZZ나동", null, null, null, Source.TAXI_DRIVER_PICK));

		CertifiedRestaurantLoader.Result first = load(rows);
		assertThat(first.created()).isEqualTo(2);
		assertThat(first.withPhoto()).isEqualTo(1);
		long placesAfterFirst = count("SELECT count(*) FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?");
		long featuresAfterFirst = count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?");
		assertThat(placesAfterFirst).isEqualTo(2);
		// 첫째: 갈래 1 + 맛집 1 + 구동 1 + 인증 목록 2 = 5, 둘째: 1 + 1 + 1 + 1 = 4
		assertThat(featuresAfterFirst).isEqualTo(9);

		CertifiedRestaurantLoader.Result second = load(rows);
		assertThat(second.created()).isZero();
		assertThat(second.alreadyThere()).isEqualTo(2);
		assertThat(second.featuresAdded()).isZero();
		assertThat(count("SELECT count(*) FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?"))
				.isEqualTo(placesAfterFirst);
		assertThat(count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?"))
				.isEqualTo(featuresAfterFirst);

		Map<String, Object> place = this.jdbcTemplate.queryForMap("""
				SELECT place_id, name_ko, category, address, source_type, dataset_version, photo_url, photo_source,
				       curation_status, lat, lng
				  FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id = ?
				""", ID_PREFIX + "1");
		assertThat(place.get("name_ko")).isEqualTo(this.fixture.prefix() + "새국밥");
		assertThat(place.get("category")).isEqualTo("FOOD");
		assertThat(place.get("dataset_version")).isEqualTo(DATASET);
		assertThat(place.get("curation_status")).isEqualTo("CURATED");
		assertThat(place.get("photo_url")).isEqualTo("https://example.invalid/a.jpg");
		assertThat(place.get("photo_source")).isEqualTo("출처 : 시험");

		UUID placeId = (UUID) place.get("place_id");
		assertThat(placeId).isEqualTo(CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "1"));
		assertThat(featureKeys(placeId)).containsExactlyInAnyOrder("CATEGORY_TAG:FOOD", "INTEREST_TAG:LOCAL_RESTAURANT",
				"ADMIN_AREA_TAG:ZZ시험구/ZZ가동", "CERTIFICATION_TAG:BUSAN_FOOD_INFO", "CERTIFICATION_TAG:MODEL_RESTAURANT");

		// 사진이 없는 곳은 사진 칸이 비어 있다 — 지어내지 않는다.
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT photo_url FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id = ?", String.class,
				ID_PREFIX + "2")).isNull();
	}

	@Test
	@DisplayName("표식의 값은 읽을 수 있는 JSON 이다 — 구·동은 부분으로, 인증 목록은 파일에 적힌 한국어 이름으로, 증거 등급은 기관이 확인한 것과 계산한 것을 가른다")
	void featureValuesAndEvidence() {
		load(List.of(row("1", "값식당", 35.3001, 129.3001, "ZZ시험구", "ZZ가동", null, null, null, Source.MODEL_RESTAURANT)));
		UUID placeId = CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "1");

		Map<String, Object> area = this.jdbcTemplate.queryForMap("""
				SELECT value ->> 'gu' AS gu, value ->> 'dong' AS dong, evidence_status, source_version, source_id
				  FROM place_feature WHERE place_id = ? AND feature_type = 'ADMIN_AREA_TAG'
				""", placeId);
		assertThat(area.get("gu")).isEqualTo("ZZ시험구");
		assertThat(area.get("dong")).isEqualTo("ZZ가동");
		// 좌표를 역지오코딩해 계산한 값이다 — 기관이 확인해 준 것이 아니다.
		assertThat(area.get("evidence_status")).isEqualTo("ESTIMATED");
		assertThat(area.get("source_version")).isEqualTo(DATASET);
		assertThat(area.get("source_id")).isEqualTo(ID_PREFIX + "1");

		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT value ->> 'source' FROM place_feature
				 WHERE place_id = ? AND feature_type = 'CERTIFICATION_TAG' AND feature_key = 'MODEL_RESTAURANT'
				""", String.class, placeId)).isEqualTo("모범음식점");
		// 인증 목록에 오른 것은 기관이 확인해 준 사실이다.
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT evidence_status FROM place_feature
				 WHERE place_id = ? AND feature_type = 'INTEREST_TAG' AND feature_key = 'LOCAL_RESTAURANT'
				""", String.class, placeId)).isEqualTo("VERIFIED");
	}

	// ── 이미 있는 장소 ─────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 existingPlaceId 가 있으면 장소를 새로 만들지 않고 표식만 단다 — 사진도 안 건드린다")
	void marksExistingPlaceWithoutCreatingAnother() {
		UUID existing = this.fixture.insertPlace("있던식당", null, "FOOD", 35.3201, 129.3201);

		CertifiedRestaurantLoader.Result result = load(List.of(row("1", "있던식당(파일 이름)", 35.3201, 129.3201, "ZZ시험구", "ZZ다동",
				existing, null, null, Source.BUSAN_FOOD_INFO, Source.TAXI_DRIVER_PICK)));

		assertThat(result.created()).isZero();
		assertThat(result.markedExisting()).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?")).isZero();
		assertThat(featureKeys(existing)).containsExactlyInAnyOrder("INTEREST_TAG:LOCAL_RESTAURANT",
				"ADMIN_AREA_TAG:ZZ시험구/ZZ다동", "CERTIFICATION_TAG:BUSAN_FOOD_INFO", "CERTIFICATION_TAG:TAXI_DRIVER_PICK");
		// 장소의 이름은 파일이 아니라 DB 의 것이 그대로다.
		assertThat(this.jdbcTemplate.queryForObject("SELECT name_ko FROM place WHERE place_id = ?", String.class, existing))
				.isEqualTo(this.fixture.prefix() + "있던식당");

		// 다시 돌려도 표식이 안 는다.
		long before = count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?");
		CertifiedRestaurantLoader.Result again = load(List.of(row("1", "있던식당(파일 이름)", 35.3201, 129.3201, "ZZ시험구", "ZZ다동",
				existing, null, null, Source.BUSAN_FOOD_INFO, Source.TAXI_DRIVER_PICK)));
		assertThat(again.featuresAdded()).isZero();
		assertThat(count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?"))
				.isEqualTo(before);
	}

	@Test
	@DisplayName("🔴 existingPlaceId 의 장소가 이 DB 에 없으면 아무것도 안 쓰고 센다 — 다른 DB 의 번호로 만든 파일일 수 있다")
	void missingExistingPlaceIsCountedNotInvented() {
		CertifiedRestaurantLoader.Result result = load(List.of(row("1", "없는식당", 35.3301, 129.3301, "ZZ시험구", "ZZ라동",
				UUID.randomUUID(), null, null, Source.MODEL_RESTAURANT)));

		assertThat(result.missingExisting()).isEqualTo(1);
		assertThat(result.missingExistingSourceIds()).containsExactly(ID_PREFIX + "1");
		assertThat(result.created()).isZero();
		assertThat(count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?")).isZero();
	}

	@Test
	@DisplayName("🔴 추천에서 뺀(숨김) 장소에는 표식을 안 단다 — 숨긴 곳이 인증 맛집으로 되살아나지 않게")
	void hiddenPlaceIsNotMarked() {
		UUID hidden = this.fixture.insertPlace("숨은식당", null, "FOOD", 35.3401, 129.3401);
		this.jdbcTemplate.update("UPDATE place SET curation_status = 'HIDDEN' WHERE place_id = ?", hidden);

		CertifiedRestaurantLoader.Result result = load(List.of(row("1", "숨은식당", 35.3401, 129.3401, "ZZ시험구", "ZZ마동", hidden,
				null, null, Source.MODEL_RESTAURANT)));

		assertThat(result.notUsable()).isEqualTo(1);
		assertThat(featureKeys(hidden)).isEmpty();
	}

	@Test
	@DisplayName("🔴 합쳐진 줄을 가리키면 남은 줄에 표식을 단다 — 수집 쪽이 짝지은 뒤에 합쳐졌을 수 있다")
	void mergedPlaceFollowsTheSurvivor() {
		UUID survivor = this.fixture.insertPlace("남은식당", null, "FOOD", 35.3501, 129.3501);
		UUID merged = this.fixture.insertPlace("합쳐진식당", null, "FOOD", 35.3501, 129.3502);
		this.jdbcTemplate.update("UPDATE place SET curation_status = 'MERGED', merged_into = ? WHERE place_id = ?", survivor,
				merged);

		CertifiedRestaurantLoader.Result result = load(List.of(row("1", "합쳐진식당", 35.3501, 129.3502, "ZZ시험구", "ZZ바동", merged,
				null, null, Source.BUSAN_FOOD_INFO)));

		assertThat(result.markedExisting()).isEqualTo(1);
		assertThat(featureKeys(survivor)).contains("INTEREST_TAG:LOCAL_RESTAURANT", "ADMIN_AREA_TAG:ZZ시험구/ZZ바동");
		assertThat(featureKeys(merged)).isEmpty();
	}

	@Test
	@DisplayName("🔴 한 장소에는 구·동이 하나뿐이다 — 이미 있으면 다른 줄이 다른 곳을 말해도 먼저 것을 두고 센다")
	void anExistingAreaIsNotReplaced() {
		UUID existing = this.fixture.insertPlace("구동식당", null, "FOOD", 35.3601, 129.3601);
		this.fixture.insertTagFeature(existing, "ADMIN_AREA_TAG", "ZZ원래구/ZZ원래동", "ESTIMATED",
				"{\"gu\":\"ZZ원래구\",\"dong\":\"ZZ원래동\"}");

		CertifiedRestaurantLoader.Result result = load(List.of(row("1", "구동식당", 35.3601, 129.3601, "ZZ시험구", "ZZ사동", existing,
				null, null, Source.MODEL_RESTAURANT)));

		assertThat(result.areaConflicts()).isEqualTo(1);
		List<String> areas = this.jdbcTemplate.queryForList(
				"SELECT feature_key FROM place_feature WHERE place_id = ? AND feature_type = 'ADMIN_AREA_TAG'", String.class,
				existing);
		assertThat(areas).containsExactly("ZZ원래구/ZZ원래동");
		// 구·동 말고 나머지 표식은 그래도 붙는다.
		assertThat(featureKeys(existing)).contains("INTEREST_TAG:LOCAL_RESTAURANT", "CERTIFICATION_TAG:MODEL_RESTAURANT");
	}

	@Test
	@DisplayName("🔴 같은 장소를 가리키는 줄이 둘이면(부산시 맛집정보 + 모범음식점) 인증 목록이 합쳐지고 맛집 표식은 하나다")
	void rowsPointingAtTheSamePlaceMergeTheirSources() {
		UUID existing = this.fixture.insertPlace("두목록식당", null, "FOOD", 35.3701, 129.3701);

		CertifiedRestaurantLoader.Result result = load(List.of(
				row("1", "두목록식당", 35.3701, 129.3701, "ZZ시험구", "ZZ아동", existing, null, null, Source.BUSAN_FOOD_INFO),
				row("2", "두목록식당", 35.3701, 129.3701, "ZZ시험구", "ZZ아동", existing, null, null, Source.MODEL_RESTAURANT)));

		assertThat(result.markedExisting()).isEqualTo(2);
		assertThat(featureKeys(existing)).containsExactlyInAnyOrder("INTEREST_TAG:LOCAL_RESTAURANT",
				"ADMIN_AREA_TAG:ZZ시험구/ZZ아동", "CERTIFICATION_TAG:BUSAN_FOOD_INFO", "CERTIFICATION_TAG:MODEL_RESTAURANT");
	}

	@Test
	@DisplayName("🔴 카페로 옮겨진 장소(갈래 CAFE_HEALING + 카페 표식)는 이 적재기를 다시 돌려도 맛집으로 되살아나지 않는다 — 장소 갈래가 음식이면 둘 다 선다 (S15P21E201-2059)")
	void aPlaceMovedToCafesIsNotBroughtBackToRestaurants() {
		UUID movedCafe = this.fixture.insertPlace("옮긴카페", null, "CAFE_HEALING", 35.4101, 129.4101);
		this.fixture.insertTagFeature(movedCafe, "INTEREST_TAG", "LOCAL_CAFE", "VERIFIED", "true");
		UUID both = this.fixture.insertPlace("겸하는곳", null, "FOOD", 35.4201, 129.4201);
		this.fixture.insertTagFeature(both, "INTEREST_TAG", "LOCAL_CAFE", "VERIFIED", "true");
		UUID untouched = this.fixture.insertPlace("카페표식없는카페", null, "CAFE_HEALING", 35.4301, 129.4301);

		CertifiedRestaurantLoader.Result result = load(List.of(
				row("1", "옮긴카페", 35.4101, 129.4101, "ZZ시험구", "ZZ하동", movedCafe, null, null, Source.MODEL_RESTAURANT),
				row("2", "겸하는곳", 35.4201, 129.4201, "ZZ시험구", "ZZ하동", both, null, null, Source.MODEL_RESTAURANT),
				row("3", "카페표식없는카페", 35.4301, 129.4301, "ZZ시험구", "ZZ하동", untouched, null, null, Source.MODEL_RESTAURANT)));

		assertThat(result.markedExisting()).isEqualTo(3);
		// 카페로 옮겨진 곳 — 맛집 표식은 안 붙고, 구·동과 인증 목록은 그대로 붙는다.
		assertThat(featureKeys(movedCafe)).containsExactlyInAnyOrder("ADMIN_AREA_TAG:ZZ시험구/ZZ하동", "CERTIFICATION_TAG:MODEL_RESTAURANT");
		// 음식점을 겸하는 카페 — 장소 갈래가 음식이라 맛집 표식이 붙는다(카페 표식은 그대로).
		assertThat(featureKeys(both)).contains("INTEREST_TAG:LOCAL_RESTAURANT");
		// 아직 카페 표식이 없는 카페 갈래 장소는 예전처럼 맛집 표식이 붙는다 — 옮기는 일은 운영 SQL 의 몫이다.
		assertThat(featureKeys(untouched)).contains("INTEREST_TAG:LOCAL_RESTAURANT");
	}

	// ── 같은 곳 검사 ───────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 번호가 달라도 같은 이름이 150m 안에 이미 있으면 새로 만들지 않고 그 장소에 표식을 단다")
	void sameNameNearbyGetsMarksInsteadOfADuplicate() {
		UUID nearby = this.fixture.insertPlace("이웃식당", null, "FOOD", 35.3801, 129.3801);

		SamePlaceReport report = new SamePlaceReport();
		CertifiedRestaurantLoader.Result result = this.loader.saveChunk(
				List.of(row("1", this.fixture.prefix() + "이웃식당", 35.38011, 129.38011, "ZZ시험구", "ZZ자동", null, null, null,
						Source.TAXI_DRIVER_PICK)),
				DATASET, OffsetDateTime.now(), report);

		assertThat(result.created()).isZero();
		assertThat(result.markedSamePlace()).isEqualTo(1);
		assertThat(result.sameExamples()).hasSize(1);
		assertThat(report.blocked()).isZero();
		assertThat(featureKeys(nearby)).contains("INTEREST_TAG:LOCAL_RESTAURANT", "CERTIFICATION_TAG:TAXI_DRIVER_PICK");
		assertThat(count("SELECT count(*) FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?")).isZero();
	}

	@Test
	@DisplayName("🔴 해변·산책로처럼 좌표가 멀리 찍히는 같은 이름이 150m~1km 에 있으면 넣지도 달지도 않고 사람 확인으로 남긴다")
	void wideCategoryNeighbourGoesToHumanReview() {
		this.fixture.insertPlace("바닷가식당", null, "SEA_BEACH", 35.3900, 129.3900);

		SamePlaceReport report = new SamePlaceReport();
		CertifiedRestaurantLoader.Result result = this.loader.saveChunk(
				List.of(row("1", this.fixture.prefix() + "바닷가식당", 35.3945, 129.3900, "ZZ시험구", "ZZ차동", null, null, null,
						Source.MODEL_RESTAURANT)),
				DATASET, OffsetDateTime.now(), report);

		assertThat(result.created()).isZero();
		assertThat(result.markedSamePlace()).isZero();
		assertThat(report.reviews()).hasSize(1);
		assertThat(count("SELECT count(*) FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?")).isZero();
	}

	@Test
	@DisplayName("같은 덩어리 안에서 같은 이름이 바로 옆에 또 나오면 앞 줄이 만든 장소에 인증 목록을 합친다 — 장소는 하나다")
	void sameChunkNeighboursBecomeOnePlace() {
		CertifiedRestaurantLoader.Result result = load(List.of(
				row("1", "쌍둥이식당", 35.3951, 129.3951, "ZZ시험구", "ZZ카동", null, null, null, Source.BUSAN_FOOD_INFO),
				row("2", "쌍둥이식당", 35.39511, 129.39511, "ZZ시험구", "ZZ카동", null, null, null, Source.TAXI_DRIVER_PICK)));

		assertThat(result.created()).isEqualTo(1);
		assertThat(result.markedSamePlace()).isEqualTo(1);
		UUID only = CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "1");
		assertThat(featureKeys(only)).containsExactlyInAnyOrder("CATEGORY_TAG:FOOD", "INTEREST_TAG:LOCAL_RESTAURANT",
				"ADMIN_AREA_TAG:ZZ시험구/ZZ카동", "CERTIFICATION_TAG:BUSAN_FOOD_INFO", "CERTIFICATION_TAG:TAXI_DRIVER_PICK");
	}

	// ── 마이그레이션이 연 길 ────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 새 표식 둘은 태그형이다 — 열쇠 없이 넣으면 DB 가 거절하고, 열쇠가 있으면 받는다")
	void newFeatureTypesAreTagShaped() {
		UUID place = this.fixture.insertPlace("태그형시험", null, "FOOD", 35.4001, 129.4001);

		assertThatThrownBy(() -> this.fixture.insertValueFeature(place, "ADMIN_AREA_TAG", "ESTIMATED", "{\"gu\":\"가\"}"))
				.as("구·동 표식은 열쇠로 거르므로 열쇠가 없으면 안 된다")
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.fixture.insertValueFeature(place, "CERTIFICATION_TAG", "VERIFIED", "true"))
				.isInstanceOf(DataIntegrityViolationException.class);

		this.fixture.insertTagFeature(place, "ADMIN_AREA_TAG", "ZZ구/ZZ동", "ESTIMATED", "{\"gu\":\"ZZ구\",\"dong\":\"ZZ동\"}");
		this.fixture.insertTagFeature(place, "CERTIFICATION_TAG", "MODEL_RESTAURANT", "VERIFIED", "true");
		// 같은 장소·종류·열쇠는 둘이 못 들어간다 — 다시 돌릴 때 행이 느는 길이 DB 에서도 막혀 있다.
		assertThatThrownBy(() -> this.fixture.insertTagFeature(place, "CERTIFICATION_TAG", "MODEL_RESTAURANT", "VERIFIED", "true"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private CertifiedRestaurantLoader.Result load(List<CertifiedRestaurantRow> rows) {
		return this.loader.saveChunk(rows, DATASET, OffsetDateTime.now(), new SamePlaceReport());
	}

	private List<String> featureKeys(UUID placeId) {
		return this.jdbcTemplate.queryForList("""
				SELECT feature_type || ':' || feature_key FROM place_feature
				 WHERE place_id = ? AND source_type = 'BUSAN_CERTIFIED_FOOD'
				""", String.class, placeId);
	}

	private long count(String sql) {
		return this.jdbcTemplate.queryForObject(sql, Long.class, ID_PREFIX + "%");
	}

	/** 이 시험이 적재기로 넣은 것만 지운다 — 표식을 먼저, 장소를 나중에. */
	private void deleteWhatTheLoaderWrote() {
		this.jdbcTemplate.update("""
				DELETE FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?
				""", ID_PREFIX + "%");
		this.jdbcTemplate.update("""
				DELETE FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?
				""", ID_PREFIX + "%");
	}

	/**
	 * 시험 줄 하나. 이름 앞에 이 실행만의 표({@link PlaceFixture#prefix()})를 붙인다 — 같은 DB 에 다른 시험·실제 자료가 있어도
	 * 같은 곳 검사(이름이 같고 150m 안)에 우연히 걸리지 않게. 이미 붙어 있으면 다시 붙이지 않는다.
	 */
	private CertifiedRestaurantRow row(String suffix, String name, double lat, double lng, String gu, String dong,
			UUID existingPlaceId, String photoUrl, String photoSource, Source... sources) {
		String prefixed = name.startsWith(this.fixture.prefix()) ? name : this.fixture.prefix() + name;
		return new CertifiedRestaurantRow(ID_PREFIX + suffix, prefixed, lat, lng, "부산광역시 시험로 " + suffix, null, gu, dong,
				List.of(sources), null, existingPlaceId, photoUrl, photoSource);
	}
}
