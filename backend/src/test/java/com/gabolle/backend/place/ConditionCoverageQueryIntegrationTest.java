package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 덮임을 세는 질의가 <b>진짜 PostgreSQL 에서</b> 도는가 — S15P21E201-1508.
 *
 * <p>단위 시험은 저장소를 흉내 내므로 이것을 못 본다. 여기서 보는 것 셋이 전부 DB 쪽 성질이다.
 *
 * <ul>
 * <li>JPQL 이 실제로 파싱·실행되는가</li>
 * <li>{@code AS featureType}·{@code AS placeCount} 별명이 <b>투영 인터페이스에 실제로
 *     묶이는가</b> — 이름이 어긋나면 컴파일도 되고 문맥도 뜨지만 값이 안 온다</li>
 * <li>장소 하나에 같은 갈래가 여러 줄일 때 <b>장소로 세는가</b>(줄로 세지 않는가)</li>
 * </ul>
 */
class ConditionCoverageQueryIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private PlaceFeatureRepository featureRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PlaceFixture fixture;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
	}

	@AfterEach
	void tearDown() {
		this.fixture.cleanUp();
	}

	private Map<String, Long> countsOf(String... featureTypes) {
		return this.featureRepository.countPlacesByFeatureType(List.of(featureTypes)).stream()
				.collect(java.util.stream.Collectors.toMap(
						PlaceFeatureRepository.FeatureTypePlaceCount::getFeatureType,
						PlaceFeatureRepository.FeatureTypePlaceCount::getPlaceCount));
	}

	@Test
	@DisplayName("🔴 갈래마다 장소 수가 실제로 온다 — 별명이 투영에 묶인다")
	void countsComeBackBoundToTheProjection() {
		UUID placeId = this.fixture.insertPlace("덮임시험장소", null, "FOOD", 35.1, 129.0);
		this.fixture.insertTagFeature(placeId, "CUISINE_TAG", "PORK_SOUP", "VERIFIED", "true");

		Map<String, Long> counts = countsOf("CUISINE_TAG");

		assertThat(counts).as("별명이 안 묶이면 여기서 빈 값이 온다").containsKey("CUISINE_TAG");
		assertThat(counts.get("CUISINE_TAG")).isGreaterThanOrEqualTo(1L);
	}

	@Test
	@DisplayName("🔴 한 장소에 같은 갈래가 여러 줄이어도 «한 곳»으로 센다")
	void onePlaceWithManyRowsCountsOnce() {
		// 🔴 절대값이 아니라 «늘어난 양»을 본다. 이 질의는 표 전체를 세므로 다른 검사가
		//    남긴 행이 있으면 절대값이 달라진다 — 실제로 그렇게 CI 에서 빨개졌다.
		//    이 검사가 보려는 것은 「3줄이 1곳으로 세어지는가」이고, 그건 차이로 봐야 한다.
		long before = countsOf("ALLERGEN_TAG").getOrDefault("ALLERGEN_TAG", 0L);

		UUID placeId = this.fixture.insertPlace("여러줄장소", null, "FOOD", 35.11, 129.01);
		this.fixture.insertTagFeature(placeId, "ALLERGEN_TAG", "PEANUT", "VERIFIED", "true");
		this.fixture.insertTagFeature(placeId, "ALLERGEN_TAG", "SHRIMP", "VERIFIED", "true");
		this.fixture.insertTagFeature(placeId, "ALLERGEN_TAG", "MILK", "VERIFIED", "true");

		long after = countsOf("ALLERGEN_TAG").getOrDefault("ALLERGEN_TAG", 0L);

		// 줄로 세면 3 이 늘고, 장소로 세면 1 이 는다. 줄로 세면 알레르기 표식 열 줄이 붙은
		// 한 곳이 「열 곳」이 되어 화면이 자료가 넉넉한 줄 안다.
		assertThat(after - before).as("3줄을 넣었는데 장소 수가 1 만큼 늘지 않았다").isEqualTo(1L);
	}

	@Test
	@DisplayName("🔴 자료가 한 곳도 없는 갈래는 «줄 자체가 없다» — 0 이 오지 않는다")
	void aFeatureTypeWithNoRowsIsAbsentNotZero() {
		Map<String, Long> counts = countsOf("DIETARY_SUPPORT_TAG_없는갈래");

		// 이것이 ConditionCoverageService 가 getOrDefault(…, 0L) 로 채워야 하는 이유다.
		assertThat(counts).doesNotContainKey("DIETARY_SUPPORT_TAG_없는갈래");
	}
}
