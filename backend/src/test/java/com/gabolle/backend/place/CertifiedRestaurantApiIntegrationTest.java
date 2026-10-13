package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceFacetResponse;
import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.api.RestaurantAreaResponse;
import com.gabolle.backend.place.api.RestaurantAreaResponse.GuItem;
import com.gabolle.backend.place.api.RestaurantPageResponse;
import com.gabolle.backend.place.api.RestaurantPageResponse.RestaurantItem;
import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.loader.CertifiedRestaurantLoader;
import com.gabolle.backend.place.loader.CertifiedRestaurantRow;
import com.gabolle.backend.place.loader.SamePlaceReport;
import com.gabolle.backend.place.service.CertifiedRestaurantQueryService;
import com.gabolle.backend.place.service.PlaceFacetService;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 「맛집」 갈래의 구·동 목록과 맛집 목록 (S15P21E201-2051) — 적재기가 넣은 표식을 진짜 PostgreSQL 에서 JPQL 로 거르고 센다.
 *
 * <p>구·동 이름에 이 실행만의 표를 붙인다. 같은 DB 에 다른 시험이나 실제 자료가 있어도 이 시험이 넣은 구·동만 골라 본다.
 */
class CertifiedRestaurantApiIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "certified-api-test";

	private static final String ID_PREFIX = "ZZ2051API";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CertifiedRestaurantLoader loader;

	@Autowired
	private CertifiedRestaurantQueryService queryService;

	@Autowired
	private PlaceFacetService facetService;

	@Autowired
	private PlaceSearchService searchService;

	private PlaceFixture fixture;

	private String gu;

	private String otherGu;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
		this.gu = "ZZ구" + this.fixture.token();
		this.otherGu = "ZZ다른구" + this.fixture.token();
		deleteWhatTheLoaderWrote();
	}

	@AfterEach
	void tearDown() {
		deleteWhatTheLoaderWrote();
		this.fixture.cleanUp();
	}

	// ── 구·동 목록 ────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 구마다 맛집 수를 세고 그 안의 동도 센다 — 식당이 많은 곳이 먼저, 같은 동 이름이 다른 구에 있어도 섞이지 않는다")
	void areasCountPlacesPerGuAndDong() {
		loadAll(
				row("1", "가식당", this.gu, "ZZ가동", 35.31, 129.31, Source.BUSAN_FOOD_INFO),
				row("2", "나식당", this.gu, "ZZ가동", 35.32, 129.32, Source.MODEL_RESTAURANT),
				row("3", "다식당", this.gu, "ZZ나동", 35.33, 129.33, Source.TAXI_DRIVER_PICK),
				// 같은 동 이름이 다른 구에도 있다 — 구와 함께 읽어야 한다.
				row("4", "라식당", this.otherGu, "ZZ가동", 35.34, 129.34, Source.MODEL_RESTAURANT));

		RestaurantAreaResponse areas = this.queryService.areas();

		GuItem mine = guItem(areas, this.gu);
		assertThat(mine.placeCount()).isEqualTo(3);
		assertThat(mine.dongs()).extracting("dong", "placeCount").containsExactly(tuple("ZZ가동", 2L), tuple("ZZ나동", 1L));
		GuItem other = guItem(areas, this.otherGu);
		assertThat(other.placeCount()).isEqualTo(1);
		assertThat(other.dongs()).extracting("dong", "placeCount").containsExactly(tuple("ZZ가동", 1L));

		// 식당이 많은 구가 앞이다.
		assertThat(areas.areas().indexOf(mine)).isLessThan(areas.areas().indexOf(other));
		assertThat(areas.generatedAt()).isNotNull();
	}

	@Test
	@DisplayName("🔴 폐업일자가 있는 곳·숨긴 곳·사용자가 고른 곳은 세지도 보여 주지도 않는다 — 인증 맛집으로 내보내면 안 된다")
	void closedHiddenAndUserSubmittedPlacesAreLeftOut() {
		loadAll(
				row("1", "열린식당", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT),
				row("2", "닫힌식당", this.gu, "ZZ가동", 35.32, 129.32, Source.MODEL_RESTAURANT),
				row("3", "숨긴식당", this.gu, "ZZ가동", 35.33, 129.33, Source.MODEL_RESTAURANT),
				row("4", "고른식당", this.gu, "ZZ가동", 35.34, 129.34, Source.MODEL_RESTAURANT));
		this.jdbcTemplate.update("UPDATE place SET closed_on = DATE '2026-01-01' WHERE place_id = ?",
				CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "2"));
		this.jdbcTemplate.update("UPDATE place SET curation_status = 'HIDDEN' WHERE place_id = ?",
				CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "3"));
		this.jdbcTemplate.update("UPDATE place SET curation_status = 'USER_SUBMITTED' WHERE place_id = ?",
				CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "4"));

		assertThat(guItem(this.queryService.areas(), this.gu).placeCount()).isEqualTo(1);
		RestaurantPageResponse page = this.queryService.restaurants(this.gu, "ZZ가동", null);
		assertThat(page.items()).extracting(item -> item.place().nameKo())
				.containsExactly(this.fixture.prefix() + "열린식당");
	}

	@Test
	@DisplayName("맛집 자료가 하나도 없는 구는 구 목록에 나오지 않는다")
	void guWithoutRestaurantsIsAbsent() {
		assertThat(this.queryService.areas().areas()).extracting(GuItem::gu).doesNotContain(this.gu);
	}

	// ── 맛집 목록 ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 동으로 거르고, 인증한 목록이 많은 집이 먼저, 같으면 이름 순이다 — 각 줄에 구·동과 인증 목록이 실린다")
	void restaurantsOfADongAreSortedByCertificationCountThenName() {
		loadAll(
				row("1", "나나식당", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT),
				row("2", "가가식당", this.gu, "ZZ가동", 35.32, 129.32, Source.MODEL_RESTAURANT),
				row("3", "셋인증식당", this.gu, "ZZ가동", 35.33, 129.33, Source.TAXI_DRIVER_PICK, Source.BUSAN_FOOD_INFO,
						Source.MODEL_RESTAURANT),
				row("4", "둘인증식당", this.gu, "ZZ가동", 35.34, 129.34, Source.MODEL_RESTAURANT, Source.TAXI_DRIVER_PICK),
				row("5", "다른동식당", this.gu, "ZZ나동", 35.35, 129.35, Source.MODEL_RESTAURANT));

		RestaurantPageResponse page = this.queryService.restaurants(this.gu, "ZZ가동", null);

		assertThat(page.items()).extracting(item -> item.place().nameKo()).containsExactly(
				this.fixture.prefix() + "셋인증식당", this.fixture.prefix() + "둘인증식당", this.fixture.prefix() + "가가식당",
				this.fixture.prefix() + "나나식당");
		assertThat(page.hasNext()).isFalse();
		assertThat(page.limit()).isEqualTo(100);

		RestaurantItem three = page.items().get(0);
		assertThat(three.gu()).isEqualTo(this.gu);
		assertThat(three.dong()).isEqualTo("ZZ가동");
		// 인증 목록은 선언 순서로 늘 같은 순서다.
		assertThat(three.certifiedSources()).containsExactly("BUSAN_FOOD_INFO", "MODEL_RESTAURANT", "TAXI_DRIVER_PICK");
		assertThat(page.items().get(1).certifiedSources()).containsExactly("MODEL_RESTAURANT", "TAXI_DRIVER_PICK");
		// 기존 목록 카드가 쓰는 칸이 그대로 실린다.
		assertThat(three.place().placeId()).isEqualTo(CertifiedRestaurantLoader.placeIdOf(ID_PREFIX + "3"));
		assertThat(three.place().category()).isEqualTo("FOOD");
		assertThat(three.place().lat()).isEqualTo(35.33);
	}

	@Test
	@DisplayName("동을 빼면 구 전체다 — 구 이름의 % 와 _ 는 글자 그대로 읽는다(남의 구까지 걸리지 않는다)")
	void guWithoutDongReturnsTheWholeGuAndEscapesWildcards() {
		loadAll(
				row("1", "가식당", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT),
				row("2", "나식당", this.gu, "ZZ나동", 35.32, 129.32, Source.MODEL_RESTAURANT),
				row("3", "다식당", this.otherGu, "ZZ가동", 35.33, 129.33, Source.MODEL_RESTAURANT));

		assertThat(this.queryService.restaurants(this.gu, null, null).items()).extracting(item -> item.place().nameKo())
				.containsExactlyInAnyOrder(this.fixture.prefix() + "가식당", this.fixture.prefix() + "나식당");
		assertThat(this.queryService.restaurants(this.gu, "  ", null).items()).hasSize(2);

		// 「ZZ%」는 모든 ZZ… 구가 아니라 이름이 정확히 「ZZ%」인 구다 — 없으니 비어야 한다.
		assertThat(this.queryService.restaurants("ZZ%", null, null).items()).isEmpty();
		assertThat(this.queryService.restaurants("ZZ_", null, null).items()).isEmpty();
		assertThat(this.queryService.restaurants("없는구", "없는동", null).items()).isEmpty();
	}

	@Test
	@DisplayName("limit 이 목록을 자르고 hasNext 로 알린다")
	void limitCutsAndFlagsHasNext() {
		loadAll(
				row("1", "가식당", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT),
				row("2", "나식당", this.gu, "ZZ가동", 35.32, 129.32, Source.MODEL_RESTAURANT),
				row("3", "다식당", this.gu, "ZZ가동", 35.33, 129.33, Source.MODEL_RESTAURANT));

		RestaurantPageResponse page = this.queryService.restaurants(this.gu, "ZZ가동", 2);
		assertThat(page.items()).hasSize(2);
		assertThat(page.hasNext()).isTrue();
		assertThat(page.limit()).isEqualTo(2);

		RestaurantPageResponse all = this.queryService.restaurants(this.gu, "ZZ가동", 3);
		assertThat(all.items()).hasSize(3);
		assertThat(all.hasNext()).isFalse();
	}

	@Test
	@DisplayName("🔴 요청이 계약과 다르면 400 이다 — 구가 없거나, limit 이 범위 밖이거나, 이름이 너무 길다")
	void badRequestsAreRejected() {
		assertThatThrownBy(() -> this.queryService.restaurants(null, null, null)).isInstanceOf(PlaceRequestException.class)
				.hasMessageContaining("구");
		assertThatThrownBy(() -> this.queryService.restaurants("  ", "ZZ가동", null))
				.isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.queryService.restaurants(this.gu, null, 0)).isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.queryService.restaurants(this.gu, null, 201)).isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.queryService.restaurants("가".repeat(31), null, null))
				.isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.queryService.restaurants(this.gu, "나".repeat(31), null))
				.isInstanceOf(PlaceRequestException.class);
	}

	// ── 기존 /facets ───────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 /facets 의 탐색 갈래 끝에 「맛집」이 건수와 이름표와 함께 붙고, 기존 여덟은 순서·이름 그대로다")
	void facetsListTheRestaurantKeyAfterTheEight() {
		PlaceFacetResponse.FacetItem before = interestFacet(this.facetService.facets());
		long countBefore = keyCount(before, "LOCAL_RESTAURANT");

		loadAll(
				row("1", "가식당", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT),
				row("2", "나식당", this.gu, "ZZ나동", 35.32, 129.32, Source.TAXI_DRIVER_PICK));

		PlaceFacetResponse.FacetItem after = interestFacet(this.facetService.facets());
		assertThat(keyCount(after, "LOCAL_RESTAURANT")).isEqualTo(countBefore + 2);
		PlaceFacetResponse.FacetKeyCount restaurant = after.keys().stream()
				.filter(key -> key.featureKey().equals("LOCAL_RESTAURANT")).findFirst().orElseThrow();
		// 이름표가 없으면 앱이 이름 없는 갈래를 목록에서 거른다 — 있는데 안 보이는 일이 없게.
		assertThat(restaurant.labelKo()).isEqualTo("맛집");

		// 기존 여덟은 자료가 있든 없든 앞에 그대로 — 맛집은 그 뒤다.
		List<String> expectedEight = InterestTagCode.displayOrder().stream().map(Enum::name).toList();
		assertThat(after.keys().subList(0, 8)).extracting(PlaceFacetResponse.FacetKeyCount::featureKey)
				.containsExactlyElementsOf(expectedEight);
		assertThat(after.keys().subList(0, 8)).extracting(PlaceFacetResponse.FacetKeyCount::labelKo)
				.containsExactlyElementsOf(InterestTagCode.displayOrder().stream().map(InterestTagCode::labelKo).toList());
		assertThat(after.keys().indexOf(restaurant)).isGreaterThanOrEqualTo(8);
		// 전에 있던 여덟 갈래의 건수는 이 적재가 안 움직인다.
		for (String key : expectedEight) {
			assertThat(keyCount(after, key)).as(key).isEqualTo(keyCount(before, key));
		}
	}

	@Test
	@DisplayName("그 낱말로 기존 갈래 필터 목록(GET /places?facetType=INTEREST_TAG&facetKey=LOCAL_RESTAURANT)에서도 나온다")
	void theExistingFacetListFindsThemToo() {
		loadAll(row("1", "가식당", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT));

		PlacePageResponse page = this.searchService.searchByFacet(InterestTagCode.FEATURE_TYPE, "LOCAL_RESTAURANT", 50);

		assertThat(page.items()).extracting(PlaceSummaryResponse::nameKo).contains(this.fixture.prefix() + "가식당");
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private static GuItem guItem(RestaurantAreaResponse areas, String gu) {
		Optional<GuItem> found = areas.areas().stream().filter(item -> item.gu().equals(gu)).findFirst();
		assertThat(found).as(gu + " 가 구 목록에 있다").isPresent();
		return found.orElseThrow();
	}

	private static PlaceFacetResponse.FacetItem interestFacet(PlaceFacetResponse response) {
		return response.facets().stream().filter(item -> InterestTagCode.FEATURE_TYPE.equals(item.placeFeatureType()))
				.findFirst().orElseThrow();
	}

	private static long keyCount(PlaceFacetResponse.FacetItem item, String key) {
		return item.keys().stream().filter(entry -> entry.featureKey().equals(key)).mapToLong(
				PlaceFacetResponse.FacetKeyCount::placeCount).findFirst().orElse(0L);
	}

	private void loadAll(CertifiedRestaurantRow... rows) {
		this.loader.saveChunk(List.of(rows), DATASET, OffsetDateTime.now(), new SamePlaceReport());
	}

	private CertifiedRestaurantRow row(String suffix, String name, String gu, String dong, double lat, double lng,
			Source... sources) {
		return new CertifiedRestaurantRow(ID_PREFIX + suffix, this.fixture.prefix() + name, lat, lng, "부산광역시 시험로 " + suffix,
				null, gu, dong, List.of(sources), null, (UUID) null, null, null);
	}

	private void deleteWhatTheLoaderWrote() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?",
				ID_PREFIX + "%");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'BUSAN_CERTIFIED_FOOD' AND source_id LIKE ?",
				ID_PREFIX + "%");
	}
}
