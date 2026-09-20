package com.gabolle.backend.place;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.service.PlaceDetailService;
import com.gabolle.backend.place.service.PlaceNotFoundException;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 장소 상세. "정보 없음" 과 "해당 없음" 이 구분되는지가 핵심이라, 네 상태를 각각 만들어
 * 놓고 서로 다르게 나오는지 본다.
 */
class PlaceDetailIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceDetailService placeDetailService;

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
	@DisplayName("없는 장소는 404 로 이어지는 예외다")
	void unknownPlaceIsNotFound() {
		UUID missing = UUID.randomUUID();

		assertThatThrownBy(() -> this.placeDetailService.get(missing, null))
				.isInstanceOf(PlaceNotFoundException.class);
	}

	@Test
	@DisplayName("한 번의 조회로 장소 정보와 출처가 함께 온다")
	void detailCarriesPlaceAndProvenance() {
		UUID placeId = this.fixture.insertPlace("흰여울문화마을", "Huinnyeoul Culture Village",
				"ATTRACTION", 35.0781, 129.0175);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		assertThat(detail.placeId()).isEqualTo(placeId);
		assertThat(detail.nameKo()).endsWith("흰여울문화마을");
		assertThat(detail.nameEn()).endsWith("Huinnyeoul Culture Village");
		assertThat(detail.lat()).isEqualTo(35.0781);
		assertThat(detail.provenance().sourceType()).isEqualTo("FIXTURE");
		assertThat(detail.provenance().datasetVersion()).isEqualTo("fixture-test");
		assertThat(detail.provenance().collectedAt()).isNotNull();
		assertThat(detail.provenance().observedAt()).isNotNull();
	}

	@Test
	@DisplayName("🔴 해당 없음(VERIFIED false)과 정보 없음(UNKNOWN)이 응답에서 다르게 나온다")
	void verifiedFalseIsDistinctFromUnknown() {
		UUID placeId = this.fixture.insertPlace("네상태", null, "ATTRACTION", 35.1, 129.0);
		// 확인했고 결과가 "아니다" — 해당 없음
		this.fixture.insertTagFeature(placeId, "ACCESSIBILITY_TAG", "WHEELCHAIR", "VERIFIED", "false");
		// 보러 갔는데 못 정했다 — 정보 없음
		this.fixture.insertValueFeature(placeId, "SHADE_SCORE", "UNKNOWN", null);
		// 추정
		this.fixture.insertValueFeature(placeId, "QUIETNESS_SCORE", "ESTIMATED", "{\"score\": 0.7}");

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		PlaceFeatureView wheelchair = feature(detail, "ACCESSIBILITY_TAG");
		assertThat(wheelchair.evidenceStatus()).isEqualTo("VERIFIED");
		assertThat(wheelchair.value()).isNotNull();
		assertThat(wheelchair.value().asBoolean()).isFalse();

		PlaceFeatureView shade = feature(detail, "SHADE_SCORE");
		assertThat(shade.evidenceStatus()).isEqualTo("UNKNOWN");
		assertThat(shade.value()).isNull();

		PlaceFeatureView quietness = feature(detail, "QUIETNESS_SCORE");
		assertThat(quietness.evidenceStatus()).isEqualTo("ESTIMATED");
		assertThat(quietness.value().get("score").asDouble()).isEqualTo(0.7);

		assertThat(wheelchair.evidenceStatus())
				.isNotEqualTo(shade.evidenceStatus())
				.isNotEqualTo(quietness.evidenceStatus());
	}

	@Test
	@DisplayName("🔴 행이 아예 없는 종류는 NOT_COLLECTED 로 나온다 — 모른다와 아직 안 봤다는 다르다")
	void missingRowsBecomeNotCollected() {
		UUID placeId = this.fixture.insertPlace("일부만수집", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		// 대조표에 있는 종류인데 행이 없으면 NOT_COLLECTED 가 붙는다.
		assertThat(detail.features())
				.filteredOn(view -> "NOT_COLLECTED".equals(view.evidenceStatus()))
				.isNotEmpty()
				.allSatisfy(view -> {
					assertThat(view.value()).isNull();
					assertThat(view.featureKey()).isNull();
				});
		assertThat(detail.features()).extracting(PlaceFeatureView::featureType).contains("ALLERGEN_TAG");
		assertThat(feature(detail, "INTEREST_TAG").evidenceStatus()).isEqualTo("VERIFIED");
	}

	@Test
	@DisplayName("🔴 NOT_COLLECTED 목록은 자바가 아니라 대조표에서 나온다 — 짝 없는 피처는 안 붙는다")
	void notCollectedListComesFromTheCodeMapTable() {
		UUID placeId = this.fixture.insertPlace("짝없는피처", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, null);

		// POPULARITY_SCORE·CROWDING_SCORE 는 사용자가 직접 고르는 화면이 없어 대조표에 없다.
		// 빠뜨린 것이 아니라 짝이 없는 것이므로 NOT_COLLECTED 로도 나오지 않아야 한다.
		assertThat(detail.features()).extracting(PlaceFeatureView::featureType)
				.doesNotContain("POPULARITY_SCORE", "CROWDING_SCORE");
	}

	/**
	 * {@code NOT_INCLUDED} 를 돌려주면 화면이 "이 장소는 일정에 없다" 고 단정하는데, 그것은
	 * 확인한 사실이 아니라 물어보지 않은 것이다.
	 */
	@Test
	@DisplayName("일정을 지정하지 않으면 포함 여부는 false 가 아니라 알 수 없음이다")
	void inclusionIsUnavailableWhenNoItineraryIsGiven() {
		UUID placeId = this.fixture.insertPlace("일정확인", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService.get(placeId, UUID.randomUUID());

		assertThat(detail.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");
		assertThat(detail.itineraryInclusion().reason()).isEqualTo("ITINERARY_NOT_SPECIFIED");
		// 포함 여부를 모른다고 장소 정보가 비지 않는다.
		assertThat(detail.placeId()).isEqualTo(placeId);
		assertThat(detail.nameKo()).endsWith("일정확인");
	}

	/**
	 * 이 슬라이스({@code PlaceSliceApplication})는 {@code common}·{@code place} 만 스캔하므로
	 * {@code ItineraryMembershipPort} 구현이 빈으로 없다. 배선이 빠진 것을
	 * {@code NOT_INCLUDED} 라는 사실로 바꿔 내보내지 않는다.
	 */
	@Test
	@DisplayName("일정을 지정해도 이 슬라이스에는 판정할 구현이 없어 알 수 없음이다")
	void inclusionIsUnavailableWhenNoPortImplementationIsWired() {
		UUID placeId = this.fixture.insertPlace("구현없음", null, "ATTRACTION", 35.1, 129.0);

		PlaceDetailResponse detail = this.placeDetailService
				.get(placeId, UUID.randomUUID(), null, UUID.randomUUID());

		assertThat(detail.itineraryInclusion().state()).isEqualTo("UNAVAILABLE");
		assertThat(detail.itineraryInclusion().reason()).isEqualTo("ITINERARY_LOOKUP_UNAVAILABLE");
		assertThat(detail.placeId()).isEqualTo(placeId);
	}

	private PlaceFeatureView feature(PlaceDetailResponse detail, String featureType) {
		return detail.features().stream()
				.filter(view -> featureType.equals(view.featureType()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("피처가 없다: " + featureType));
	}
}
