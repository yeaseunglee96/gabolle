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
 * 표식 기준 갈래 조회(S15P21E201-473) 완료 기준을 하나씩 확인한다.
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
		// 🔴 표를 비우지 않는다 — 다른 통합 테스트가 같은 표(place, place_feature)에 행을 남긴다.
		// user_place_code_map 도 마찬가지로 건드리지 않는다 — 마이그레이션이 채우고
		// PlaceFeatureCodeMapTest 가 빠짐을 검사하는 공용 기준 데이터라서, 여기서 행을
		// 넣었다 지웠다 하면 이 테스트가 죽었을 때 그 표가 어중간하게 남아 남의 테스트를 깬다.
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

		// 🔴 탐색 갈래는 취향 CATEGORY 에 얹혀 있지 않다 (S15P21E201-904) — 갈래 이름으로 찾는다.
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

		// 대조표 행은 **하나도 빠짐없이** 나와야 한다 — 자바 쪽에 허용 목록이 있다면 여기서 갈린다.
		//
		// 🔴 항목이 대조표 행보다 하나 많다 (S15P21E201-904). 탐색 아코디언(INTEREST_TAG)은
		//    취향 차원이 아니라서 대조표에 짝이 없고, 자기 사전(InterestTagCode)에서 직접
		//    만들어진다. 전에는 취향 CATEGORY 줄이 그 갈래를 가리켜서 그 줄에 얹혀 나왔고,
		//    그 바람에 온보딩 여섯 낱말과 탐색 여덟 낱말이 한 서랍에 섞였다.
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
		// 🔴 findHavingFeature·findByFeatureTypeIn(옛 countPlacesByFeature)은 evidenceStatus <>
		// UNKNOWN 까지만 걸러서, "확인했는데 없다" 로 판명된 행(VERIFIED + 값 false)도 그대로
		// 통과시킨다. 값(JSONB) 안을 보는 것은 JPQL 이 못 하고 PlaceFeature.indicatesPresence() 가
		// 자바에서 하므로, 그 필터가 실제로 걸리는지를 여기서 확인한다.
		String token = this.fixture.token();
		// 🔴 이 시험은 다른 시험과 안 섞이려고 **매번 다른 낱말**이 필요하다. 그런데 탐색·온보딩
		//    갈래는 사전이 낱말을 강제하므로(S15P21E201-904) 지어낸 낱말을 못 쓴다. 그래서 아직
		//    사전이 없는 갈래(ATMOSPHERE_TAG)로 잰다 — 이 시험이 확인하는 것은 "확인된 부재를
		//    세지 않는가" 이지 어느 갈래인가가 아니다.
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
		// 확인된 부재 행까지 세었다면 2가 나온다 — 1이어야 그 행이 안 세어졌다는 뜻이다.
		assertThat(keyCount.placeCount()).isEqualTo(1);
	}
}
