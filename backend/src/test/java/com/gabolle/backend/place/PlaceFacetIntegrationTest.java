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
		this.fixture.cleanUp();
		// 아래 "여분 매핑" 테스트가 넣었을 수 있는 행을 지운다. 없으면 0건 삭제라 안전하다.
		this.jdbcTemplate.update("""
				DELETE FROM user_place_code_map
				WHERE user_input_kind = 'PREFERENCE' AND user_input_code = 'CATEGORY'
				  AND place_feature_type = 'ATMOSPHERE_TAG'
				""");
	}

	@Test
	@DisplayName("완료 기준 — 갈래별 조회에 그 갈래의 표식이 없는 장소가 섞이지 않는다")
	void facetFilteredListExcludesPlacesWithoutTheFeature() {
		String token = this.fixture.token();
		UUID withFeature = this.fixture.insertPlace("표식있음" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(withFeature, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		UUID withoutFeature = this.fixture.insertPlace("표식없음" + token, null, "ATTRACTION", 35.1, 129.0);

		PlacePageResponse page = this.placeSearchService.searchByFacet("INTEREST_TAG", "SEA", null);

		assertThat(page.items()).extracting(PlaceSummaryResponse::placeId)
				.contains(withFeature)
				.doesNotContain(withoutFeature);
	}

	@Test
	@DisplayName("완료 기준 — 응답에 건수가 들어 있다")
	void facetResponseIncludesCounts() {
		String token = this.fixture.token();
		UUID place = this.fixture.insertPlace("건수확인" + token, null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(place, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceFacetResponse response = this.placeFacetService.facets();

		FacetItem category = response.facets().stream()
				.filter(item -> "CATEGORY".equals(item.userInputCode()))
				.findFirst().orElseThrow();
		assertThat(category.placeFeatureType()).isEqualTo("INTEREST_TAG");
		assertThat(category.placeCount()).isGreaterThanOrEqualTo(1);
		assertThat(category.keys()).extracting(FacetKeyCount::featureKey).contains("SEA");
	}

	@Test
	@DisplayName("🔴 갈래 목록은 대조표를 그대로 반영한다 — 자바에 허용 목록이 없어 표에 있는 행이 다 나온다")
	void everyCodeMapRowIsReflectedWithoutAJavaAllowlist() {
		List<UserPlaceCodeMap> allPreferenceRows =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);

		PlaceFacetResponse response = this.placeFacetService.facets();

		// 대조표 행 수와 응답 항목 수가 같아야 한다 — 자바 쪽에 허용 목록이 있다면 여기서 개수가 갈린다.
		assertThat(response.facets()).hasSize(allPreferenceRows.size());
		for (UserPlaceCodeMap row : allPreferenceRows) {
			assertThat(response.facets()).anySatisfy(item -> {
				assertThat(item.userInputCode()).isEqualTo(row.getUserInputCode());
				assertThat(item.placeFeatureType()).isEqualTo(row.getPlaceFeatureType());
			});
		}
	}

	@Test
	@DisplayName("🔴 대조표에 매핑을 하나 더 추가하면 코드 변경 없이 응답에 나타난다 (완료 기준의 핵심)")
	void newlyAddedCodeMapRowAppearsWithoutCodeChange() {
		// 🔴 ck_user_place_code_map_code 가 이미 정해진 8개 취향 코드만 허용해서, 완전히 새로운
		// 9번째 차원은 이 테스트에서 넣을 수 없다 — 그건 새 마이그레이션의 몫이고 이 작업
		// 범위에서는 마이그레이션·도메인 파일을 건드리지 않기로 했다. 대신 이미 허용된 코드에
		// 지금까지 없던 place_feature_type 짝을 추가해서, "표에 행을 더하면 자바 수정 없이
		// 반영된다" 는 같은 성질을 검증한다.
		this.jdbcTemplate.update("""
				INSERT INTO user_place_code_map
				    (user_input_kind, user_input_code, place_feature_type, match_kind, note)
				VALUES ('PREFERENCE', 'CATEGORY', 'ATMOSPHERE_TAG', 'TAG_OVERLAP', 'facet 테스트용 여분 매핑')
				""");

		PlaceFacetResponse response = this.placeFacetService.facets();

		assertThat(response.facets()).anySatisfy(item -> {
			assertThat(item.userInputCode()).isEqualTo("CATEGORY");
			assertThat(item.placeFeatureType()).isEqualTo("ATMOSPHERE_TAG");
		});
	}
}
