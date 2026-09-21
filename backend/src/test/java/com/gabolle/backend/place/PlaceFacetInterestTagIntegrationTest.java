package com.gabolle.backend.place;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceFacetResponse;
import com.gabolle.backend.place.api.PlaceFacetResponse.FacetItem;
import com.gabolle.backend.place.api.PlaceFacetResponse.FacetKeyCount;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.service.PlaceFacetService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 8갈래는 데이터가 없어도 항상 여덟 개다. 다른 통합 테스트가 같은 표에 자유 코드 행을 남기므로
 * "keys 가 정확히 8개" 가 아니라 "여덟 개가 정해진 순서로 앞에 온다" 로 확인한다.
 */
class PlaceFacetInterestTagIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceFacetService placeFacetService;

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
	@DisplayName("🔴 데이터가 하나도 없어도 INTEREST_TAG 갈래는 여덟 개가 나온다")
	void allEightInterestTagsAppearEvenWithoutAnyData() {
		PlaceFacetResponse response = this.placeFacetService.facets();

		FacetItem category = interestTagFacet(response);
		List<String> firstEight = category.keys().stream().limit(8).map(FacetKeyCount::featureKey).toList();

		assertThat(firstEight).containsExactlyElementsOf(
				InterestTagCode.displayOrder().stream().map(Enum::name).toList());
	}

	@Test
	@DisplayName("순서는 InterestTagCode.displayOrder() 와 같고, labelKo 가 함께 온다")
	void orderMatchesDisplayOrderAndCarriesLabelKo() {
		PlaceFacetResponse response = this.placeFacetService.facets();

		FacetItem category = interestTagFacet(response);
		List<InterestTagCode> expectedOrder = InterestTagCode.displayOrder();

		for (int index = 0; index < expectedOrder.size(); index++) {
			InterestTagCode tag = expectedOrder.get(index);
			FacetKeyCount keyCount = category.keys().get(index);
			assertThat(keyCount.featureKey()).isEqualTo(tag.name());
			assertThat(keyCount.labelKo()).isEqualTo(tag.labelKo());
		}
	}

	@Test
	@DisplayName("데이터가 없는 갈래는 count 0, 있는 갈래는 실제 건수다")
	void zeroCountForAbsentTagAndRealCountForPresentTag() {
		UUID placeId = this.fixture.insertPlace("축제장소", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(placeId, "INTEREST_TAG", "FESTIVAL", "VERIFIED", "{\"present\": true}");

		PlaceFacetResponse response = this.placeFacetService.facets();
		FacetItem category = interestTagFacet(response);

		FacetKeyCount festival = keyFor(category, "FESTIVAL");
		FacetKeyCount souvenirShop = keyFor(category, "SOUVENIR_SHOP");

		assertThat(festival.placeCount()).isEqualTo(1);
		assertThat(souvenirShop.placeCount()).isZero();
	}

	@Test
	@DisplayName("🔴 다른 표식 종류(ATMOSPHERE_TAG 등)는 예전처럼 데이터가 있는 키만 준다")
	void otherFacetTypesAreUnaffected() {
		PlaceFacetResponse response = this.placeFacetService.facets();

		boolean hasNonInterestTagFacet = response.facets().stream()
				.anyMatch(item -> !InterestTagCode.FEATURE_TYPE.equals(item.placeFeatureType()));
		assertThat(hasNonInterestTagFacet)
				.as("대조표에 INTEREST_TAG 말고 다른 갈래도 있어야 이 테스트가 의미가 있다")
				.isTrue();

		// 다른 갈래는 여덟 개 강제 규칙이 없어 labelKo 가 모두 null 이다.
		response.facets().stream()
				.filter(item -> !InterestTagCode.FEATURE_TYPE.equals(item.placeFeatureType()))
				.forEach(item -> assertThat(item.keys()).allSatisfy(key -> assertThat(key.labelKo()).isNull()));
	}

	private FacetItem interestTagFacet(PlaceFacetResponse response) {
		return response.facets().stream()
				.filter(item -> InterestTagCode.FEATURE_TYPE.equals(item.placeFeatureType()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("INTEREST_TAG 갈래가 응답에 없다"));
	}

	private FacetKeyCount keyFor(FacetItem item, String featureKey) {
		return item.keys().stream()
				.filter(key -> featureKey.equals(key.featureKey()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("키가 없다: " + featureKey));
	}
}
