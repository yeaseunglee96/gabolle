package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관광공사 분류 → 앱 낱말 — S15P21E201-854.
 *
 * <p>🔴 이 검사가 막는 것은 <b>앱이 모르는 낱말이 들어가는 것</b>이다.
 * {@code BaselineCandidateTranslator} 가 글자 그대로 비교하므로, 여기서 한 글자만 달라도 그
 * 갈래를 고른 사용자의 후보가 조용히 0 건이 된다 — 아무것도 빨개지지 않는 종류의 고장이다.
 */
class TourApiCategoryTest {

	/** 앱이 실제로 보내는 여섯. {@code SbizPlaceLoader} 주석에 적힌 목록 그대로다. */
	private static final String[] APP_WORDS = {
			"SEA_BEACH", "CITY", "CAFE_HEALING", "CULTURE_TEMPLE", "FOOD", "NATURE_WALK" };

	@Test
	@DisplayName("자연 안에서 해수욕장만 SEA_BEACH 다 — 실측에서 감지해변·송도해수욕장 둘이 그 분류다")
	void beachOnlyForTheBeachCode() {
		assertThat(TourApiCategory.of("A01", "A01011200")).isEqualTo("SEA_BEACH");
	}

	@Test
	@DisplayName("자연의 나머지는 NATURE_WALK — 산·수목원·계곡·강이 여기다")
	void otherNatureIsNatureWalk() {
		assertThat(TourApiCategory.of("A01", "A01010400")).isEqualTo("NATURE_WALK"); // 금정산
		assertThat(TourApiCategory.of("A01", "A01010700")).isEqualTo("NATURE_WALK"); // 수목원
		assertThat(TourApiCategory.of("A01", "A01010900")).isEqualTo("NATURE_WALK"); // 계곡
		assertThat(TourApiCategory.of("A01", null)).isEqualTo("NATURE_WALK");
	}

	@Test
	@DisplayName("인문은 CULTURE_TEMPLE, 쇼핑은 CITY")
	void humanitiesAndShopping() {
		assertThat(TourApiCategory.of("A02", "A02010100")).isEqualTo("CULTURE_TEMPLE");
		assertThat(TourApiCategory.of("A04", "A04010200")).isEqualTo("CITY");
	}

	@Test
	@DisplayName("🔴 레포츠와 숙박은 비운다 — 억지로 넣으면 자연 산책에 실내 사격장이 섞인다")
	void sportsAndLodgingHaveNoAppWord() {
		assertThat(TourApiCategory.of("A03", "A03021600")).isNull(); // 실탄사격장
		assertThat(TourApiCategory.of("A03", "A03022700")).isNull(); // 무장애숲길 — 걷는 길이지만 같은 대분류다
		assertThat(TourApiCategory.of("B02", "B02010100")).isNull(); // 호텔
	}

	@Test
	@DisplayName("음식은 여기서 판정하지 않는다 — 판독기가 애초에 넘기지 않는다")
	void foodIsNotThisMethodsJob() {
		assertThat(TourApiCategory.of("A05", "A05020100")).isNull();
	}

	@Test
	@DisplayName("모르는 대분류가 오면 비운다 — 지어내지 않는다")
	void unknownCat1IsEmpty() {
		assertThat(TourApiCategory.of("C01", null)).isNull();
		assertThat(TourApiCategory.of(null, null)).isNull();
		assertThat(TourApiCategory.of("", null)).isNull();
	}

	@Test
	@DisplayName("🔴 내는 낱말은 앱이 보내는 여섯 안에 있다 — 하나라도 벗어나면 그 갈래가 조용히 0 건이 된다")
	void everyProducedWordIsOneTheAppSends() {
		String[] cat1s = { "A01", "A02", "A03", "A04", "A05", "B02", "C01" };
		String[] cat3s = { null, "A01011200", "A01010400", "A02010100", "A04010200", "A03021600" };
		for (String cat1 : cat1s) {
			for (String cat3 : cat3s) {
				String produced = TourApiCategory.of(cat1, cat3);
				if (produced != null) {
					assertThat(produced)
							.as("앱이 모르는 낱말이다: cat1=%s cat3=%s", cat1, cat3)
							.isIn((Object[]) APP_WORDS);
				}
			}
		}
	}
}
