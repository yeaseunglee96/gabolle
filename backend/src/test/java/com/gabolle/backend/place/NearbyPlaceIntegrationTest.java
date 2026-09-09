package com.gabolle.backend.place;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.gabolle.backend.place.api.NearbyPlaceItem;
import com.gabolle.backend.place.api.NearbyPlaceResponse;
import com.gabolle.backend.place.service.NearbyPlaceService;
import com.gabolle.backend.place.service.PlaceRequestException;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 근처 장소 거리순 조회(S15P21E201-469) 완료 기준 셋을 각각 테스트 하나로 잰다.
 *
 * <p>{@code PlaceProperties.purposes} 는 {@code application*.properties} 를 고치지 않기로 한
 * 경계 때문에 여기서 {@link TestPropertySource} 로 채운다. 테스트 전용 목적 코드
 * {@code ZZT_SOUVENIR} 를 {@code category = "ZZT_SOUVENIR_SHOP"} 로 매핑해 둔다 — 표식(feature)
 * 경로는 {@code PlaceRepository.findWithinBoundingBoxHavingFeature} 자체가 이미
 * {@code PlaceMappingIntegrationTest} 에서 검증하므로, 여기서는 반경 사다리와 정렬 로직만 본다.
 *
 * <p>🔴 표를 비우지 않는다 — {@code PlaceFeatureCodeMapTest} 가 같은 표에 행을 남기고 정리하지
 * 않는다. {@link #tearDown()} 에서 {@code fixture.cleanUp()} 만 부른다.
 */
@TestPropertySource(properties = {
		"gabolle.place.purposes[ZZT_SOUVENIR].categories[0]=ZZT_SOUVENIR_SHOP"
})
class NearbyPlaceIntegrationTest extends PlacePostgresIntegrationTest {

	/** {@link com.gabolle.backend.place.service.GeoDistance} 의 지구 반지름과 같은 값 — 북쪽으로
	 * 옮긴 좌표의 실제 거리가 의도한 미터 수와 정확히 맞아떨어지게 하기 위해서다. */
	private static final double EARTH_RADIUS_METERS = 6_371_008.8;

	private static final double ORIGIN_LAT = 35.0;

	private static final double ORIGIN_LNG = 129.0;

	private static final String PURPOSE = "ZZT_SOUVENIR";

	private static final String CATEGORY = "ZZT_SOUVENIR_SHOP";

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
	@DisplayName("결과는 거리 오름차순이고 distanceM 이 실린다")
	void resultsAreSortedByDistanceAscending() {
		UUID d100 = insertAt("100m", 100);
		UUID d300 = insertAt("300m", 300);
		UUID d500 = insertAt("500m", 500);
		UUID d700 = insertAt("700m", 700);
		UUID d900 = insertAt("900m", 900);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG, PURPOSE, 20);

		// 다섯이 최소 개수(기본 5)를 정확히 채우므로 반경을 넓힐 필요가 없다.
		assertThat(response.radiusExpanded()).isFalse();
		assertThat(response.effectiveRadiusM()).isEqualTo(1000);
		assertThat(response.items()).extracting(NearbyPlaceItem::placeId)
				.containsExactly(d100, d300, d500, d700, d900);
		assertThat(response.items().get(0).distanceM()).isBetween(98L, 102L);
	}

	@Test
	@DisplayName("최소 개수를 못 채우면 반경을 넓히고 그 사실을 응답에 싣는다")
	void expandsRadiusWhenMinimumCountIsNotMet() {
		// 1000m 안에는 셋뿐 — 기본 최소 개수(5)에 못 미친다.
		insertAt("가까이1", 100);
		insertAt("가까이2", 300);
		insertAt("가까이3", 600);
		// 1000~2000m 사이에 셋을 더 둬서 2000m 반경에서는 여섯이 되게 한다.
		insertAt("확장1", 1200);
		insertAt("확장2", 1500);
		insertAt("확장3", 1800);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG, PURPOSE, 20);

		assertThat(response.radiusExpanded()).isTrue();
		assertThat(response.requestedRadiusM()).isEqualTo(1000);
		assertThat(response.effectiveRadiusM()).isEqualTo(2000);
		assertThat(response.expansionSteps()).isEqualTo(1);
		assertThat(response.items()).hasSize(6);
	}

	@Test
	@DisplayName("🔴 결과 순서는 DB 삽입 순서와 무관하게 거리순이다")
	void sortIsIndependentOfInsertionOrder() {
		// 🔴 예전 테스트("인기 점수를 거리 순서와 반대로 넣어도...")는 이 경로의 PurposeSpec 에
		// featureType 이 없어(카테고리로만 목적을 판별) 서비스가 place_feature 를 아예 읽지 않고,
		// 응답 DTO 에도 점수 칼럼이 없어서 비교자가 그 값을 참조할 통로가 없었다 — 즉 무엇을
		// 넣든 실패할 수 없는 테스트였다. 대신 여기서는 실제로 관찰 가능한 것, 즉 "삽입 순서가
		// 결과 순서에 새지 않는가" 를 잰다 — 거리와 반대 순서로 넣고 결과가 거리순인지 본다.
		UUID far = insertAt("삽입역순멀리", 900);
		UUID mid = insertAt("삽입역순중간", 500);
		UUID near = insertAt("삽입역순가까이", 100);

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG, PURPOSE, 20);

		List<UUID> order = response.items().stream().map(NearbyPlaceItem::placeId).toList();
		assertThat(order).containsExactly(near, mid, far);
	}

	@Test
	@DisplayName("완료 기준 — purpose 없이 호출해도 반경 안 장소가 거리순으로 나오고 카테고리로 걸러지지 않는다")
	void worksWithoutPurposeAndAppliesNoCategoryFilter() {
		// 🔴 purposes 설정이 비어 있는 상태(application*.properties 를 안 고치기로 한 이 티켓의
		// 경계)에서 purpose 가 필수였다면 이 호출 자체가 항상 UNKNOWN_PURPOSE 로 막혔다.
		UUID matchingCategory = insertAt("목적없음A", 100);
		UUID otherCategory = insertAt("목적없음B", 300, "ZZT_다른카테고리");

		NearbyPlaceResponse response = this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG, null, 20);

		assertThat(response.purposeApplied()).isFalse();
		assertThat(response.items()).extracting(NearbyPlaceItem::placeId)
				.containsExactly(matchingCategory, otherCategory);
	}

	@Test
	@DisplayName("purpose 를 보냈는데 설정에 없으면 여전히 UNKNOWN_PURPOSE 로 거부된다")
	void unknownPurposeIsStillRejected() {
		assertThatThrownBy(() -> this.nearbyPlaceService.findNearby(ORIGIN_LAT, ORIGIN_LNG, "ZZT_NOT_CONFIGURED", 20))
				.isInstanceOf(PlaceRequestException.class)
				.satisfies(ex -> assertThat(((PlaceRequestException) ex).getCode()).isEqualTo("UNKNOWN_PURPOSE"));
	}

	/** 원점에서 정북(正北)으로 {@code meters} 만큼 떨어진, 기본 목적 카테고리의 장소를 만든다. */
	private UUID insertAt(String name, double meters) {
		return insertAt(name, meters, CATEGORY);
	}

	/** 원점에서 정북으로 {@code meters} 만큼 떨어진 장소를, 지정한 카테고리로 만든다. */
	private UUID insertAt(String name, double meters, String category) {
		double lat = ORIGIN_LAT + Math.toDegrees(meters / EARTH_RADIUS_METERS);
		return this.fixture.insertPlace(name, null, category, lat, ORIGIN_LNG);
	}
}
