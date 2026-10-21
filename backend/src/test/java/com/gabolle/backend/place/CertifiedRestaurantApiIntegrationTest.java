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
import com.gabolle.backend.place.domain.CertifiedRestaurant.Kind;
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

	// ── 카페 (S15P21E201-2059) ─────────────────────────────────────────────

	@Test
	@DisplayName("🔴 kind 가 맛집과 카페를 가른다 — 같은 구·동이어도 구 목록·동 목록·장소 목록이 섞이지 않고, kind 를 안 주면 맛집이다")
	void kindSeparatesRestaurantsFromCafes() {
		loadAll(row("1", "맛집하나", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT));
		insertCafe("카페하나", this.gu, "ZZ가동", 35.32, 129.32, "KTO_CAFE");
		insertCafe("카페둘", this.gu, "ZZ나동", 35.33, 129.33, "REDTABLE_FEATURED");
		insertCafe("다른구카페", this.otherGu, "ZZ가동", 35.34, 129.34, "KTO_CAFE");

		GuItem restaurantGu = guItem(this.queryService.areas(), this.gu);
		assertThat(restaurantGu.placeCount()).isEqualTo(1);
		assertThat(restaurantGu.dongs()).extracting("dong", "placeCount").containsExactly(tuple("ZZ가동", 1L));
		assertThat(this.queryService.areas().areas()).extracting(GuItem::gu).doesNotContain(this.otherGu);

		RestaurantAreaResponse cafes = this.queryService.areas(Kind.CAFE, null, null);
		GuItem cafeGu = guItem(cafes, this.gu);
		assertThat(cafeGu.placeCount()).isEqualTo(2);
		assertThat(cafeGu.dongs()).extracting("dong", "placeCount").containsExactlyInAnyOrder(tuple("ZZ가동", 1L), tuple("ZZ나동", 1L));
		assertThat(guItem(cafes, this.otherGu).placeCount()).isEqualTo(1);
		// 좌표를 안 주면 가장 가까운 구도 없다.
		assertThat(cafes.nearestGu()).isNull();

		assertThat(this.queryService.restaurants(this.gu, "ZZ가동", null).items()).extracting(item -> item.place().nameKo())
				.containsExactly(this.fixture.prefix() + "맛집하나");
		assertThat(this.queryService.restaurants(Kind.CAFE, this.gu, "ZZ가동", null).items())
				.extracting(item -> item.place().nameKo()).containsExactly(this.fixture.prefix() + "카페하나");
		assertThat(this.queryService.restaurants(Kind.CAFE, this.gu, null, null).items()).extracting(item -> item.place().nameKo())
				.containsExactlyInAnyOrder(this.fixture.prefix() + "카페하나", this.fixture.prefix() + "카페둘");
	}

	@Test
	@DisplayName("🔴 카드의 소개 목록은 선언 순서(관광공사 → 레드테이블 → 현지인 추천)이고, 소개한 목록이 많은 카페가 먼저이며, 레드테이블의 방송·수상 표시는 그 표식에서만 읽는다")
	void cafeItemsCarrySourcesInOrderAndTheBroadcastFlag() {
		UUID many = insertCafe("여럿이소개", this.gu, "ZZ가동", 35.31, 129.31, "LOCAL_SURVEY", "REDTABLE_FEATURED:media", "KTO_CAFE");
		insertCafe("레드테이블만", this.gu, "ZZ가동", 35.32, 129.32, "REDTABLE_FEATURED");
		insertCafe("관광공사만", this.gu, "ZZ가동", 35.33, 129.33, "KTO_CAFE");
		insertCafe("아무표식없음", this.gu, "ZZ가동", 35.34, 129.34);

		RestaurantPageResponse page = this.queryService.restaurants(Kind.CAFE, this.gu, "ZZ가동", null);

		assertThat(page.items()).extracting(item -> item.place().nameKo()).containsExactly(
				this.fixture.prefix() + "여럿이소개", this.fixture.prefix() + "관광공사만", this.fixture.prefix() + "레드테이블만",
				this.fixture.prefix() + "아무표식없음");
		RestaurantItem first = page.items().get(0);
		assertThat(first.place().placeId()).isEqualTo(many);
		assertThat(first.certifiedSources()).containsExactly("KTO_CAFE", "REDTABLE_FEATURED", "LOCAL_SURVEY");
		assertThat(first.broadcastOrAward()).isTrue();
		// 방송·수상 표시가 없으면 칸이 비어 있다(거짓을 내보내지 않는다).
		assertThat(page.items().get(2).certifiedSources()).containsExactly("REDTABLE_FEATURED");
		assertThat(page.items().get(2).broadcastOrAward()).isNull();
		assertThat(page.items().get(1).broadcastOrAward()).isNull();
		assertThat(page.items().get(3).certifiedSources()).isEmpty();
	}

	@Test
	@DisplayName("🔴 /facets 에 카페가 건수·이름표와 함께 맛집 바로 뒤에 붙고, 기존 여덟은 순서·이름 그대로다 — GET /places?facetKey=LOCAL_CAFE 로도 나온다")
	void facetsListTheCafeKeyRightAfterTheRestaurantKey() {
		PlaceFacetResponse.FacetItem before = interestFacet(this.facetService.facets());
		long cafesBefore = keyCount(before, "LOCAL_CAFE");

		loadAll(row("1", "맛집하나", this.gu, "ZZ가동", 35.31, 129.31, Source.MODEL_RESTAURANT));
		insertCafe("카페하나", this.gu, "ZZ가동", 35.32, 129.32, "KTO_CAFE");
		insertCafe("카페둘", this.gu, "ZZ나동", 35.33, 129.33, "REDTABLE_FEATURED");

		PlaceFacetResponse.FacetItem after = interestFacet(this.facetService.facets());
		assertThat(keyCount(after, "LOCAL_CAFE")).isEqualTo(cafesBefore + 2);
		PlaceFacetResponse.FacetKeyCount cafe = after.keys().stream().filter(key -> key.featureKey().equals("LOCAL_CAFE")).findFirst()
				.orElseThrow();
		assertThat(cafe.labelKo()).isEqualTo("카페");

		List<String> keys = after.keys().stream().map(PlaceFacetResponse.FacetKeyCount::featureKey).toList();
		assertThat(keys.subList(0, 8)).containsExactlyElementsOf(InterestTagCode.displayOrder().stream().map(Enum::name).toList());
		// 글자순이면 LOCAL_CAFE 가 앞이다 — 선언 순서라 맛집이 먼저이고 카페가 바로 뒤다.
		assertThat(keys.indexOf("LOCAL_CAFE")).isEqualTo(keys.indexOf("LOCAL_RESTAURANT") + 1);

		PlacePageResponse page = this.searchService.searchByFacet(InterestTagCode.FEATURE_TYPE, "LOCAL_CAFE", 50);
		assertThat(page.items()).extracting(PlaceSummaryResponse::nameKo)
				.contains(this.fixture.prefix() + "카페하나", this.fixture.prefix() + "카페둘")
				.doesNotContain(this.fixture.prefix() + "맛집하나");
	}

	// ── 내 위치의 구 (S15P21E201-2059) ──────────────────────────────────────

	@Test
	@DisplayName("🔴 좌표를 주면 그 갈래의 장소 가운데 가장 가까운 곳의 구가 nearestGu 다 — 숨긴 곳은 세지 않고, 맛집과 카페는 따로 잰다")
	void nearestGuIsTheGuOfTheClosestPlaceOfThatKind() {
		String near = "ZZ가까운구" + this.fixture.token();
		String far = "ZZ먼구" + this.fixture.token();
		String hiddenGu = "ZZ숨은구" + this.fixture.token();
		insertCafe("가까운카페", near, "ZZ가동", 35.1631, 129.1636, "KTO_CAFE");
		insertCafe("먼카페", far, "ZZ나동", 35.1700, 129.1700, "KTO_CAFE");
		// 숨긴 곳이 조회 좌표와 정확히 같은 자리(거리 0)다 — 숨긴 곳을 세면 이 구가 답이 된다.
		UUID hidden = insertCafe("숨은카페", hiddenGu, "ZZ다동", 35.16312, 129.16362, "KTO_CAFE");
		this.jdbcTemplate.update("UPDATE place SET curation_status = 'HIDDEN' WHERE place_id = ?", hidden);

		// 가장 가까운 곳은 「가까운카페」(수 m)다. 숨긴 곳이 거리 0 으로 더 가까워도 세지 않는다.
		assertThat(this.queryService.areas(Kind.CAFE, 35.16312, 129.16362).nearestGu()).isEqualTo(near);
		// 먼카페 쪽으로 옮겨 가면 그쪽이다.
		assertThat(this.queryService.areas(Kind.CAFE, 35.1699, 129.1699).nearestGu()).isEqualTo(far);
		// 맛집은 이 둘레에 없다 — 카페 때문에 맛집의 구가 정해지지 않는다.
		assertThat(this.queryService.areas(Kind.RESTAURANT, 35.16312, 129.16362).nearestGu()).isNull();
		// 구 목록은 그대로 온다 — 좌표는 nearestGu 만 더한다.
		assertThat(this.queryService.areas(Kind.CAFE, 35.16312, 129.16362).areas()).extracting(GuItem::gu).contains(near, far)
				.doesNotContain(hiddenGu);
	}

	@Test
	@DisplayName("🔴 가장 가까운 곳이 5km 를 넘으면 nearestGu 는 null 이다 — 부산 밖에서 엉뚱한 구를 짐작하지 않는다")
	void nothingWithinFiveKilometresIsNull() {
		String inside = "ZZ안쪽구" + this.fixture.token();
		String outside = "ZZ바깥구" + this.fixture.token();
		// 위도 1° 는 약 111.2km 다 — 같은 경도에서 0.0440° 는 약 4.89km 이고, 0.0460° 는 약 5.11km 다.
		UUID insideCafe = insertCafe("안쪽카페", inside, "ZZ가동", 35.1440, 129.0000, "KTO_CAFE");
		insertCafe("바깥카페", outside, "ZZ나동", 35.1460, 129.0000, "KTO_CAFE");
		// 경계상자(5km 반폭 사각형)의 모서리 — 상자 안이지만 직선거리는 약 6.9km 다. 실제 거리로 다시 거르지 않으면 이 구가 답이 된다.
		String corner = "ZZ모서리구" + this.fixture.token();
		insertCafe("모서리카페", corner, "ZZ다동", 35.1440, 129.0540, "KTO_CAFE");

		assertThat(this.queryService.areas(Kind.CAFE, 35.1000, 129.0000).nearestGu()).isEqualTo(inside);
		// 안쪽 카페를 닫은 곳으로 만들면 5km 를 넘는 카페(상자 밖 하나, 상자 모서리 하나)만 남는다 — 가장 가까워도 5km 밖이라 null.
		this.jdbcTemplate.update("UPDATE place SET closed_on = DATE '2026-01-01' WHERE place_id = ?", insideCafe);
		assertThat(this.queryService.areas(Kind.CAFE, 35.1000, 129.0000).nearestGu()).isNull();
		// 서울에서 물으면 당연히 null.
		assertThat(this.queryService.areas(Kind.CAFE, 37.5665, 126.9780).nearestGu()).isNull();
	}

	@Test
	@DisplayName("🔴 좌표가 하나만 오거나 범위 밖이거나 숫자가 아니면 400 이다 — 앱이 좌표를 잘못 보내도 조용히 무시하지 않는다")
	void badCoordinatesAreRejected() {
		assertThatThrownBy(() -> this.queryService.areas(Kind.CAFE, 35.1, null)).isInstanceOf(PlaceRequestException.class)
				.hasMessageContaining("함께");
		assertThatThrownBy(() -> this.queryService.areas(Kind.CAFE, null, 129.1)).isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.queryService.areas(Kind.CAFE, 91.0, 129.1)).isInstanceOf(PlaceRequestException.class)
				.hasMessageContaining("위도");
		assertThatThrownBy(() -> this.queryService.areas(Kind.CAFE, 35.1, 181.0)).isInstanceOf(PlaceRequestException.class)
				.hasMessageContaining("경도");
		assertThatThrownBy(() -> this.queryService.areas(Kind.CAFE, Double.NaN, 129.1)).isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.queryService.areas(Kind.CAFE, 35.1, Double.POSITIVE_INFINITY))
				.isInstanceOf(PlaceRequestException.class);
	}

	@Test
	@DisplayName("kind 글자는 앞뒤 공백·대소문자를 눈감고, 안 주면 맛집이며, 모르는 값은 400 이다")
	void kindParameterIsParsedStrictlyButKindly() {
		assertThat(this.queryService.parseKind(null)).isEqualTo(Kind.RESTAURANT);
		assertThat(this.queryService.parseKind("  ")).isEqualTo(Kind.RESTAURANT);
		assertThat(this.queryService.parseKind("cafe")).isEqualTo(Kind.CAFE);
		assertThat(this.queryService.parseKind(" RESTAURANT ")).isEqualTo(Kind.RESTAURANT);
		assertThatThrownBy(() -> this.queryService.parseKind("BAR")).isInstanceOf(PlaceRequestException.class)
				.hasMessageContaining("RESTAURANT");
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

	/**
	 * 카페 갈래 장소 하나 — 적재기 없이 표식을 직접 단다(카페 적재기는 따로 시험한다). {@code certKeys} 는 인증 표식의 열쇠이고,
	 * {@code "REDTABLE_FEATURED:media"} 는 방송·수상 표시가 붙은 레드테이블 표식이다.
	 */
	private UUID insertCafe(String name, String gu, String dong, double lat, double lng, String... certKeys) {
		UUID id = this.fixture.insertPlace(name, null, "CAFE_HEALING", lat, lng);
		this.fixture.insertTagFeature(id, "INTEREST_TAG", "LOCAL_CAFE", "VERIFIED", "true");
		this.fixture.insertTagFeature(id, "ADMIN_AREA_TAG", gu + "/" + dong, "ESTIMATED",
				"{\"gu\":\"" + gu + "\",\"dong\":\"" + dong + "\"}");
		for (String certKey : certKeys) {
			boolean media = certKey.endsWith(":media");
			String key = media ? certKey.substring(0, certKey.indexOf(':')) : certKey;
			this.fixture.insertTagFeature(id, "CERTIFICATION_TAG", key, "VERIFIED",
					media ? "{\"source\":\"레드테이블\",\"broadcastOrAward\":true}" : "{\"source\":\"" + key + "\"}");
		}
		return id;
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
