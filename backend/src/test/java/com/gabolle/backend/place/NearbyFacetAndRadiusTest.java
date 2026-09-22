package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import com.gabolle.backend.place.api.NearbyPlaceResponse;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.service.NearbyPlaceService;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 근처 조회를 갈래({@link InterestTagCode})와 반경으로 좁히는 길. 갈래로 실제로 좁혀지는가,
 * 모르는 갈래를 조용히 넘기지 않는가, 목적과 갈래를 함께 보내면 거부하는가, 호출자가 반경을
 * 정했을 때 그 반경과 두 배까지만 찾는가를 고정한다.
 *
 * <p>{@code ZZT_} 접두사는 실제 설정과 겹치지 않는 테스트 전용 이름이라는 표시다.
 */
@TestPropertySource(properties = {
		"gabolle.place.purposes[ZZT_ANY].categories[0]=ZZT_ANY_CATEGORY"
})
class NearbyFacetAndRadiusTest extends PlacePostgresIntegrationTest {

	/** {@code GeoDistance} 의 지구 반지름과 같은 값 — 북쪽으로 옮긴 좌표의 실제 거리를 맞추려면 같아야 한다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private static final double ORIGIN_LAT = 35.0;

	private static final double ORIGIN_LNG = 129.0;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private NearbyPlaceService nearbyPlaceService;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("🔴 facetKey 로 기념품샵만 나온다 — 같은 반경의 다른 갈래는 빠진다")
	void facetKeyNarrowsToThatTag() {
		UUID shop = placeAt("가까운 기념품샵", 300);
		this.fixture.insertTagFeature(shop, InterestTagCode.FEATURE_TYPE,
				InterestTagCode.SOUVENIR_SHOP.name(), "VERIFIED", "{\"present\":true}");

		UUID market = placeAt("더 가까운 전통시장", 100);
		this.fixture.insertTagFeature(market, InterestTagCode.FEATURE_TYPE,
				InterestTagCode.TRADITIONAL_MARKET.name(), "VERIFIED", "{\"present\":true}");

		// 표식이 아예 없는 장소. 갈래 필터가 도는지 확인하려면 이것도 빠져야 한다
		placeAt("표식 없는 장소", 50);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, InterestTagCode.SOUVENIR_SHOP.name(), 1000, 20);

		assertThat(response.facetKeyApplied()).isTrue();
		assertThat(response.purposeApplied()).isFalse();
		assertThat(response.items()).extracting("placeId").containsExactly(shop);
	}

	@Test
	@DisplayName("소문자 갈래도 같은 갈래로 읽는다")
	void facetKeyIsCaseInsensitive() {
		UUID shop = placeAt("기념품샵", 200);
		this.fixture.insertTagFeature(shop, InterestTagCode.FEATURE_TYPE,
				InterestTagCode.SOUVENIR_SHOP.name(), "VERIFIED", "{\"present\":true}");

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, "souvenir_shop", 1000, 20);

		assertThat(response.items()).extracting("placeId").containsExactly(shop);
	}

	@Test
	@DisplayName("🔴 모르는 갈래는 400 이다 — 오타를 '필터 없음' 으로 넘기지 않는다")
	void unknownFacetKeyIsRejected() {
		placeAt("아무 장소", 100);

		assertThatThrownBy(() -> this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, "SOUVENIER_SHOP", 1000, 20))
				.isInstanceOf(PlaceRequestException.class)
				.hasMessageContaining("갈래");
	}

	@Test
	@DisplayName("목적과 갈래를 함께 보내면 400 이다 — 어느 쪽이 이겼는지 모르게 두지 않는다")
	void purposeAndFacetKeyTogetherIsRejected() {
		assertThatThrownBy(() -> this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				"ZZT_ANY", InterestTagCode.SOUVENIR_SHOP.name(), 1000, 20))
				.isInstanceOf(PlaceRequestException.class);
	}

	@Test
	@DisplayName("반경을 정하면 그 반경에서 찾고, 넓히지 않았음을 알린다")
	void requestedRadiusIsHonoured() {
		placeAt("반경 안", 400);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, null, 500, 20);

		assertThat(response.requestedRadiusM()).isEqualTo(500);
		assertThat(response.effectiveRadiusM()).isEqualTo(500);
		assertThat(response.radiusExpanded()).isFalse();
		assertThat(response.items()).hasSize(1);
	}

	@Test
	@DisplayName("🔴 반경 안에 없으면 두 배까지 넓히고, 넓혔다고 알린다")
	void radiusDoublesWhenNothingIsFound() {
		placeAt("두 배 안에 있는 장소", 700);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, null, 500, 20);

		assertThat(response.requestedRadiusM()).isEqualTo(500);
		assertThat(response.effectiveRadiusM()).isEqualTo(1000);
		assertThat(response.radiusExpanded()).isTrue();
		assertThat(response.expansionSteps()).isEqualTo(1);
		assertThat(response.items()).hasSize(1);
	}

	@Test
	@DisplayName("두 배로도 없으면 빈 목록이다 — 설정 사다리로 더 넓히지 않는다")
	void doublingIsTheLastStep() {
		// 설정 사다리(1km·2km·5km)를 쓰면 3km 장소가 나온다. 반경을 명시했으므로 나오면 안 된다
		placeAt("3km 밖 장소", 3000);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, null, 500, 20);

		assertThat(response.effectiveRadiusM()).isEqualTo(1000);
		assertThat(response.items()).isEmpty();
	}

	@Test
	@DisplayName("반경 상한과 하한을 벗어나면 400 이다")
	void radiusOutOfRangeIsRejected() {
		assertThatThrownBy(() -> this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, null, 20, 20))
				.isInstanceOf(PlaceRequestException.class);
		assertThatThrownBy(() -> this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG,
				null, null, 20001, 20))
				.isInstanceOf(PlaceRequestException.class);
	}

	@Test
	@DisplayName("갈래도 반경도 안 주면 예전과 똑같이 동작한다 — 옛 호출자를 깨지 않았다")
	void oldSignatureStillWorks() {
		placeAt("아무 장소", 300);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG, null, 20);

		assertThat(response.facetKeyApplied()).isFalse();
		assertThat(response.purposeApplied()).isFalse();
		assertThat(response.requestedRadiusM()).isEqualTo(1000);
		assertThat(response.items()).hasSize(1);
	}

	/** 원점에서 정북으로 {@code metersNorth} 만큼 옮긴 좌표에 장소를 넣는다. */
	private UUID placeAt(String label, double metersNorth) {
		double latOffset = Math.toDegrees(metersNorth / EARTH_RADIUS_METERS);
		return this.fixture.insertPlace(this.fixture.prefix() + label, null, null,
				ORIGIN_LAT + latOffset, ORIGIN_LNG);
	}
}
