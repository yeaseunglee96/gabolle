package com.gabolle.backend.place;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 매핑이 실제 표와 맞는가. 이 클래스가 뜨는 것 자체가 검사의 절반이다 —
 * {@link PlacePostgresIntegrationTest} 가 {@code ddl-auto=validate} 로 돌아서 엔티티가 칼럼 하나라도
 * 틀리면 컨텍스트가 안 뜬다. 나머지 절반은 UNKNOWN 을 "있다" 로 세지 않는가다.
 */
class PlaceMappingIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceRepository placeRepository;

	@Autowired
	private PlaceFeatureRepository placeFeatureRepository;

	@Autowired
	private UserPlaceCodeMapRepository codeMapRepository;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		// 표를 통째로 비우지 않는다 — 다른 테스트가 같은 표에 행을 남긴다.
		this.fixture.cleanUp();
	}

	@Test
	@DisplayName("장소를 넣고 엔티티로 읽으면 칼럼이 그대로 온다")
	void placeMapsToTable() {
		UUID placeId = this.fixture.insertPlace("감천문화마을", "Gamcheon Culture Village",
				"ATTRACTION", 35.0975, 129.0107);

		Place place = this.placeRepository.findById(placeId).orElseThrow();

		assertThat(place.getNameKo()).isEqualTo(this.fixture.prefix() + "감천문화마을");
		assertThat(place.getNameEn()).isEqualTo(this.fixture.prefix() + "Gamcheon Culture Village");
		assertThat(place.getCategory()).isEqualTo("ATTRACTION");
		assertThat(place.getLat()).isEqualTo(35.0975);
		assertThat(place.hasCoordinates()).isTrue();
		assertThat(place.getDatasetVersion()).isEqualTo("fixture-test");
	}

	@Test
	@DisplayName("좌표 없는 장소는 hasCoordinates 가 거짓이고 경계상자 조회에 안 걸린다")
	void placeWithoutCoordinatesIsExcludedFromBoundingBox() {
		UUID placeId = this.fixture.insertPlace("좌표없는곳", null, "ATTRACTION", null, null);

		Place place = this.placeRepository.findById(placeId).orElseThrow();
		assertThat(place.hasCoordinates()).isFalse();

		List<Place> found = this.placeRepository.findWithinBoundingBox(-90, 90, -180, 180, Limit.of(500));
		assertThat(found).extracting(Place::getPlaceId).doesNotContain(placeId);
	}

	@Test
	@DisplayName("JSONB 값과 세 evidence 상태가 그대로 오간다")
	void featureMapsToTableIncludingJsonb() {
		UUID placeId = this.fixture.insertPlace("피처있는곳", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(placeId, "INTEREST_TAG", "SEA", "VERIFIED", "{\"present\": true}");
		this.fixture.insertValueFeature(placeId, "SHADE_SCORE", "ESTIMATED", "{\"score\": 0.4}");
		this.fixture.insertValueFeature(placeId, "SLOPE_PERCENT", "UNKNOWN", null);

		List<PlaceFeature> features = this.placeFeatureRepository.findByPlaceId(placeId);

		assertThat(features).hasSize(3);
		PlaceFeature tag = features.stream()
				.filter(f -> "INTEREST_TAG".equals(f.getFeatureType())).findFirst().orElseThrow();
		assertThat(tag.getFeatureKey()).isEqualTo("SEA");
		assertThat(tag.getEvidenceStatus()).isEqualTo(PlaceEvidenceStatus.VERIFIED);
		assertThat(tag.getValue()).contains("present");

		PlaceFeature unknown = features.stream()
				.filter(f -> "SLOPE_PERCENT".equals(f.getFeatureType())).findFirst().orElseThrow();
		assertThat(unknown.getEvidenceStatus()).isEqualTo(PlaceEvidenceStatus.UNKNOWN);
		// DB CHECK 가 강제한다 — UNKNOWN 에는 값이 없다.
		assertThat(unknown.getValue()).isNull();
		assertThat(unknown.indicatesPresence()).isFalse();
	}

	@Test
	@DisplayName("🔴 UNKNOWN 표식만 가진 장소는 그 갈래 조회에 나오지 않는다 — 모른다는 있다가 아니다")
	void unknownEvidenceDoesNotCountAsHavingTheFeature() {
		UUID verified = this.fixture.insertPlace("확인된곳", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(verified, "ATMOSPHERE_TAG", "QUIET", "VERIFIED", "{\"present\": true}");

		UUID unknown = this.fixture.insertPlace("모르는곳", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(unknown, "ATMOSPHERE_TAG", "QUIET", "UNKNOWN", null);

		List<Place> found = this.placeRepository.findHavingFeature("ATMOSPHERE_TAG", "QUIET", Limit.of(500));

		assertThat(found).extracting(Place::getPlaceId).contains(verified);
		assertThat(found).extracting(Place::getPlaceId).doesNotContain(unknown);
	}

	@Test
	@DisplayName("🔴 건수 집계도 UNKNOWN 을 세지 않는다 — 세면 건수가 거짓말이 된다")
	void featureCountsExcludeUnknown() {
		UUID a = this.fixture.insertPlace("세는곳가", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(a, "CUISINE_TAG", "SEAFOOD", "VERIFIED", "{\"present\": true}");
		UUID b = this.fixture.insertPlace("세는곳나", null, "ATTRACTION", 35.1, 129.0);
		this.fixture.insertTagFeature(b, "CUISINE_TAG", "SEAFOOD", "UNKNOWN", null);

		// 건수는 DB 가 아니라 자바에서 센다 — JPQL 로는 JSONB 값을 못 봐서 "확인된 부재" 를 못 뺀다.
		List<UUID> counted = this.placeFeatureRepository.findByFeatureTypeIn(List.of("CUISINE_TAG")).stream()
				.filter(feature -> "SEAFOOD".equals(feature.getFeatureKey()))
				.filter(PlaceFeature::indicatesPresence)
				.map(PlaceFeature::getPlaceId)
				.distinct()
				.toList();

		// 다른 테스트가 남긴 행이 있을 수 있어 절대값이 아니라 포함 여부로 본다.
		assertThat(counted).contains(a).doesNotContain(b);
		List<Place> having = this.placeRepository.findHavingFeature("CUISINE_TAG", "SEAFOOD", Limit.of(500));
		assertThat(having).extracting(Place::getPlaceId).contains(a).doesNotContain(b);
	}

	@Test
	@DisplayName("🔴 갈래 목록은 자바가 아니라 대조표에서 나온다 — 표에 줄을 더하면 코드 없이 반영된다")
	void facetListComesFromTheCodeMapTable() {
		List<UserPlaceCodeMap> preferences =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);

		// 마이그레이션이 취향 여덟을 넣어 뒀다. 자바에는 그 목록이 없다.
		assertThat(preferences).extracting(UserPlaceCodeMap::getUserInputCode)
				.contains("CATEGORY", "ATMOSPHERE", "LOCALITY", "QUIETNESS",
						"TOURIST_PREFERENCE", "FOOD_PREFERENCE", "SLOPE_PREFERENCE", "SHADE_PREFERENCE");
		// CATEGORY 는 INTEREST_TAG 가 아니라 CATEGORY_TAG 를 가리킨다 — 온보딩 낱말과
		// 둘러보기 낱말이 한 서랍에 섞이지 않게 하는 자리다.
		assertThat(preferences).extracting(UserPlaceCodeMap::getPlaceFeatureType).contains("CATEGORY_TAG");
	}

	@Test
	@DisplayName("한 사용자 입력이 피처 둘에 걸릴 수 있다 — MOBILITY 가 실제로 그렇다")
	void oneUserInputCodeCanMapToTwoFeatures() {
		List<UserPlaceCodeMap> mobility =
				this.codeMapRepository.findByIdUserInputKindAndIdUserInputCode(UserInputKind.CONSTRAINT, "MOBILITY");

		assertThat(mobility).extracting(UserPlaceCodeMap::getPlaceFeatureType)
				.containsExactlyInAnyOrder("ACCESSIBILITY_TAG", "STAIRS_PRESENT");
		assertThat(mobility).extracting(UserPlaceCodeMap::getMatchKind)
				.contains(MatchKind.HARD_FILTER, MatchKind.FLAG_COMPARE);
	}

	@Test
	@DisplayName("여러 장소의 피처를 한 번에 읽는다 — 후보 조회가 장소마다 질의하지 않게 하는 자리")
	void featuresForManyPlacesLoadInOneQuery() {
		UUID a = this.fixture.insertPlace("묶어읽기가", null, "CAFE", 35.1, 129.0);
		UUID b = this.fixture.insertPlace("묶어읽기나", null, "CAFE", 35.1, 129.0);
		this.fixture.insertTagFeature(a, "INTEREST_TAG", "ALLEY", "VERIFIED", "{\"present\": true}");
		this.fixture.insertTagFeature(b, "INTEREST_TAG", "ALLEY", "VERIFIED", "{\"present\": true}");

		List<PlaceFeature> features = this.placeFeatureRepository.findByPlaceIdIn(List.of(a, b));

		assertThat(features).hasSize(2);
		assertThat(features).extracting(PlaceFeature::getPlaceId).containsExactlyInAnyOrder(a, b);
	}
}
