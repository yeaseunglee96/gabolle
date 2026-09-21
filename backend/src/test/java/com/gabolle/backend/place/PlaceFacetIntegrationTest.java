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
import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.service.PlaceFacetService;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표식 기준 갈래 조회의 완료 기준을 하나씩 확인한다.
 */
class PlaceFacetIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceFacetService placeFacetService;

	@Autowired
	private PlaceSearchService placeSearchService;

	@Autowired
	private UserPlaceCodeMapRepository codeMapRepository;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		// 표를 통째로 비우지 않는다 — place·place_feature·user_place_code_map 은 다른 테스트와
		// 마이그레이션이 함께 쓰는 공용 데이터다. 픽스처가 넣은 행만 되돌린다.
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("완료 기준 — 갈래별 조회에 그 갈래의 표식이 없는 장소가 섞이지 않는다")
	void facetFilteredListExcludesPlacesWithoutTheFeature() {
		String token = this.fixture.token();
		UUID withFeature = this.fixture.insertPlace("표식있음" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(withFeature, "INTEREST_TAG", "NIGHT_MARKET", "VERIFIED", "{\"present\": true}");
		UUID withoutFeature = this.fixture.insertPlace("표식없음" + token, null, "ATTRACTION", 35.1, 129.0);

		PlacePageResponse page = this.placeSearchService.searchByFacet("INTEREST_TAG", "NIGHT_MARKET", null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId)
				.contains(withFeature)
				.doesNotContain(withoutFeature);
	}

	@Test
	@DisplayName("완료 기준 — 응답에 건수가 들어 있다")
	void facetResponseIncludesCounts() {
		String token = this.fixture.token();
		UUID place = this.fixture.insertPlace("건수확인" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(place, "INTEREST_TAG", "NIGHT_MARKET", "VERIFIED", "{\"present\": true}");

		PlaceFacetResponse response = this.placeFacetService.facets();

		// 탐색 갈래는 취향 CATEGORY 에 얹혀 있지 않으므로 갈래 이름으로 찾는다.
		FacetItem category = response.facets().stream()
				.filter(item -> "INTEREST_TAG".equals(item.placeFeatureType()))
				.findFirst().orElseThrow();
		assertThat(category.placeCount()).isGreaterThanOrEqualTo(1);
		assertThat(category.keys()).extracting(FacetKeyCount::featureKey).contains("NIGHT_MARKET");
	}

	@Test
	@DisplayName("🔴 갈래 목록은 대조표를 그대로 반영한다 — 자바에 허용 목록이 없어 표에 있는 행이 다 나온다")
	void everyCodeMapRowIsReflectedWithoutAJavaAllowlist() {
		List<UserPlaceCodeMap> allPreferenceRows =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);

		PlaceFacetResponse response = this.placeFacetService.facets();

		// 대조표 행보다 하나 많다 — 탐색 아코디언(INTEREST_TAG)은 대조표에 짝이 없고
		// 자기 사전(InterestTagCode)에서 직접 만들어진다.
		assertThat(response.facets()).hasSize(allPreferenceRows.size() + 1);
		assertThat(response.facets())
				.filteredOn(item -> InterestTagCode.FEATURE_TYPE.equals(item.placeFeatureType()))
				.as("탐색 갈래는 대조표와 무관하게 언제나 하나 나온다")
				.hasSize(1);
		for (UserPlaceCodeMap row : allPreferenceRows) {
			assertThat(response.facets()).anySatisfy(item -> {
				assertThat(item.userInputCode()).isEqualTo(row.getUserInputCode());
				assertThat(item.placeFeatureType()).isEqualTo(row.getPlaceFeatureType());
			});
		}
	}

	@Test
	@DisplayName("🔴 완료 기준 — 확인된 부재(VERIFIED + 값 false)만 가진 장소는 갈래 목록과 건수에서 빠진다")
	void confirmedAbsenceIsExcludedFromFacetListAndCount() {
		// JPQL 은 evidenceStatus 까지만 거르고 값(JSONB) 안은 못 본다. 부재 판정은 자바의
		// PlaceFeature.indicatesPresence() 가 하므로 그 필터가 실제로 걸리는지를 확인한다.
		String token = this.fixture.token();
		// 갈래는 ATMOSPHERE_TAG 여야 한다 — 탐색·온보딩 갈래는 사전이 낱말을 강제해서
		// 시험마다 다른 키를 지어낼 수 없다.
		String key = "SEA-" + token;
		UUID present = this.fixture.insertPlace("확인있음" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(present, "ATMOSPHERE_TAG", key, "VERIFIED", "{\"present\": true}");
		UUID confirmedAbsent = this.fixture.insertPlace("확인부재" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(confirmedAbsent, "ATMOSPHERE_TAG", key, "VERIFIED", "false");

		PlacePageResponse page = this.placeSearchService.searchByFacet("ATMOSPHERE_TAG", key, null);
		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId)
				.contains(present)
				.doesNotContain(confirmedAbsent);

		PlaceFacetResponse response = this.placeFacetService.facets();
		FacetItem category = response.facets().stream()
				.filter(item -> "ATMOSPHERE".equals(item.userInputCode()))
				.findFirst().orElseThrow();
		FacetKeyCount keyCount = category.keys().stream()
				.filter(k -> key.equals(k.featureKey()))
				.findFirst().orElseThrow();
		// 확인된 부재 행까지 세었다면 2가 나온다.
		assertThat(keyCount.placeCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 점수형 축은 featureKey 가 없어도 합계에 세어진다 (S15P21E201-1149)")
	void scoreTypeFacetCountsPlacesThatHaveNoFeatureKey() {
		// 점수형(SCORE_COMPARE) 표식은 featureKey 가 언제나 null 이라 묶음이 null 키 하나뿐이다.
		// 그 묶음을 버리면 건수가 언제나 0 이 된다.
		String token = this.fixture.token();
		UUID gentle = this.fixture.insertPlace("완만" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertValueFeature(gentle, "SLOPE_PERCENT", "ESTIMATED",
				"{\"score\": 3.1, \"radiusM\": 200.0}");
		UUID steep = this.fixture.insertPlace("가파름" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertValueFeature(steep, "SLOPE_PERCENT", "ESTIMATED",
				"{\"score\": 16.4, \"radiusM\": 200.0}");

		PlaceFacetResponse response = this.placeFacetService.facets();

		FacetItem slope = response.facets().stream()
				.filter(item -> "SLOPE_PREFERENCE".equals(item.userInputCode()))
				.findFirst().orElseThrow();

		// 다른 시험이 남긴 행이 섞일 수 있어 정확한 값 대신 하한으로 잰다.
		assertThat(slope.placeCount()).isGreaterThanOrEqualTo(2);
		assertThat(slope.placeCount())
				.as("두 곳을 넣었는데 0 이면 null 키 묶음이 또 버려진 것이다")
				.isNotZero();

		// 합계에만 더하고 keys 에는 넣지 않는다 — featureKey 가 null 인 칸이 응답에 생기면
		// 화면이 이름 없는 하위 갈래를 그린다.
		assertThat(slope.keys()).extracting(FacetKeyCount::featureKey).doesNotContainNull();
	}

	@Test
	@DisplayName("🔴 태그형 축은 이 고침으로 값이 안 바뀐다 — 회귀가 없다 (S15P21E201-1149)")
	void tagTypeFacetCountIsUnchangedByTheKeylessFix() {
		// 태그형은 featureKey 가 차 있어서 null 묶음이 아예 안 생긴다.
		String token = this.fixture.token();
		String key = "MOOD-" + token;
		UUID place = this.fixture.insertPlace("분위기" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(place, "ATMOSPHERE_TAG", key, "VERIFIED", "{\"present\": true}");

		PlaceFacetResponse response = this.placeFacetService.facets();

		FacetItem atmosphere = response.facets().stream()
				.filter(item -> "ATMOSPHERE".equals(item.userInputCode()))
				.findFirst().orElseThrow();

		long sumOfKeys = atmosphere.keys().stream().mapToLong(FacetKeyCount::placeCount).sum();
		assertThat(atmosphere.placeCount())
				.as("태그형은 합계가 키별 건수의 합과 정확히 같아야 한다")
				.isEqualTo(sumOfKeys);
	}
}
