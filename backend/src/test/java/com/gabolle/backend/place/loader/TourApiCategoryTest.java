package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.domain.AccommodationCategories;

/**
 * 아무도 모르는 낱말이 들어가는 것을 막는다. 후보 필터가 글자 그대로 비교하므로 한 글자만
 * 달라도 그 갈래를 고른 사용자의 후보가 조용히 0 건이 된다.
 *
 * <p>사전이 둘이다 — 앱이 취향으로 보내는 여섯과, 숙소 조회가 쓰는
 * {@link AccommodationCategories#CODES}. 둘은 겹치지 않아야 한다.
 */
class TourApiCategoryTest {

	/** 앱이 실제로 보내는 여섯. */
	private static final String[] APP_WORDS = {
			"SEA_BEACH", "CITY", "CAFE_HEALING", "CULTURE_TEMPLE", "FOOD", "NATURE_WALK" };

	@Test
	@DisplayName("자연 안에서 해수욕장만 SEA_BEACH 다 — 실측에서 감지해변·송도해수욕장 둘이 그 분류다")
	void beachOnlyForTheBeachCode() {
		assertThat(TourApiCategory.of("129999", "A01", "A01011200")).isEqualTo("SEA_BEACH");
	}

	@Test
	@DisplayName("자연의 나머지는 NATURE_WALK — 산·수목원·계곡·강이 여기다")
	void otherNatureIsNatureWalk() {
		assertThat(TourApiCategory.of("129999", "A01", "A01010400")).isEqualTo("NATURE_WALK"); // 금정산
		assertThat(TourApiCategory.of("129999", "A01", "A01010700")).isEqualTo("NATURE_WALK"); // 수목원
		assertThat(TourApiCategory.of("129999", "A01", "A01010900")).isEqualTo("NATURE_WALK"); // 계곡
		assertThat(TourApiCategory.of("129999", "A01", null)).isEqualTo("NATURE_WALK");
	}

	@Test
	@DisplayName("🔴 절영해안산책로·해운대 그린레일웨이만 SEA_BEACH 다 — 같은 소분류(A01010500)의 송도반도는 아니다 (S15P21E201-106)")
	void coastalWalkContentIdsAreBeach() {
		// contentid 로 콕 집는다 — 셋 다 cat3=A01010500 이라 소분류로는 못 가른다.
		assertThat(TourApiCategory.of("252561", "A01", "A01010500")).isEqualTo("SEA_BEACH");
		assertThat(TourApiCategory.of("2822343", "A01", "A01010500")).isEqualTo("SEA_BEACH");
		assertThat(TourApiCategory.of("2614725", "A01", "A01010500")).isEqualTo("NATURE_WALK"); // 일부러 뺐다
	}

	/**
	 * 축제는 대분류가 비어 오거나(운영 수집본 57곳) 인문으로 온다. 대분류로 가르면 비거나 문화·사찰이 된다 —
	 * 전에는 비어서 추천에 한 번도 안 들어갔다 (S15P21E201-1618).
	 */
	@Test
	@DisplayName("🔴 콘텐츠 유형 15(축제·공연·행사)는 대분류와 상관없이 FESTIVAL_EVENT 다")
	void festivalContentTypeIsFestival() {
		assertThat(TourApiCategory.of("3498395", "15", "", "")).isEqualTo("FESTIVAL_EVENT");
		assertThat(TourApiCategory.of("3498395", "15", null, null)).isEqualTo("FESTIVAL_EVENT");
		assertThat(TourApiCategory.of("3498395", "15", "A02", "A02070200")).isEqualTo("FESTIVAL_EVENT");
	}

	@Test
	@DisplayName("다른 유형은 전과 같다 — 관광지(12)의 인문은 여전히 CULTURE_TEMPLE, 레포츠(28)는 비운다")
	void otherContentTypesAreUnchanged() {
		assertThat(TourApiCategory.of("1", "12", "A02", "A02010100")).isEqualTo("CULTURE_TEMPLE");
		assertThat(TourApiCategory.of("1", "28", "A03", "A03021700")).isNull();
	}

	@Test
	@DisplayName("인문은 CULTURE_TEMPLE, 쇼핑은 CITY")
	void humanitiesAndShopping() {
		assertThat(TourApiCategory.of("129999", "A02", "A02010100")).isEqualTo("CULTURE_TEMPLE");
		assertThat(TourApiCategory.of("129999", "A04", "A04010200")).isEqualTo("CITY");
	}

	@Test
	@DisplayName("🔴 레포츠 안의 걷기길만 NATURE_WALK 다 — 갈맷길·문탠로드가 대분류에 묶여 묻혀 있었다")
	void onlyTheTrailSubcategoryBecomesNatureWalk() {
		// 이 줄은 원래 isNull() 이었다 (「무장애숲길 — 걷는 길이지만 같은 대분류다」).
		// 2026-09-21 에 레포츠 29곳을 소분류로 세어 보니 길 그 자체인 곳이 7곳이었다.
		assertThat(TourApiCategory.of("129999", "A03", "A03022700")).isEqualTo("NATURE_WALK");
		// 탐색 축이 같은 소분류를 이미 「산책」으로 가르고 있다. 두 축이 어긋나면 안 된다.
		assertThat(TourApiExploreFacet.of("28", "A03", "A03022700")).contains("WALK");
	}

	@Test
	@DisplayName("🔴 레포츠의 나머지는 여전히 비운다 — 억지로 넣으면 자연 산책에 실내 사격장이 섞인다")
	void otherSportsStillHaveNoAppWord() {
		// 실측한 소분류 그대로다 (2026-09-21, 레포츠 29곳). 걷기길 7곳을 뺀 22곳이 여기 든다.
		assertThat(TourApiCategory.of("129999", "A03", "A03021600")).isNull(); // 실탄사격장
		assertThat(TourApiCategory.of("129999", "A03", "A03021700")).isNull(); // 캠핑장 — 자는 곳이라 따로 정한다
		assertThat(TourApiCategory.of("129999", "A03", "A03021300")).isNull(); // 아이스링크 — 실내
		assertThat(TourApiCategory.of("129999", "A03", "A03050100")).isNull(); // 레이저태그 — 실내
		assertThat(TourApiCategory.of("129999", "A03", "A03020700")).isNull(); // 골프장
		assertThat(TourApiCategory.of("129999", "A03", "A03030100")).isNull(); // 서핑학교 — 하는 것이지 걷는 것이 아니다
		assertThat(TourApiCategory.of("129999", "A03", null)).isNull();
	}

	@Test
	@DisplayName("🔴 숙박은 숙소 낱말을 낸다 — 2026-09-21 까지 비어 있었고 그동안 숙소 목록이 0건이었다")
	void lodgingGetsTheAccommodationWord() {
		// 이 줄은 원래 isNull() 이었다. 비운 것은 사고가 아니라 결정이었지만, 그 결정이
		// 숙소 목록을 0건으로 만들고 있었다. 호텔·모텔·펜션이 전부 대분류 하나로 들어온다.
		assertThat(TourApiCategory.of("129999", "B02", "B02010100")).isEqualTo("LODGING"); // 호텔
		assertThat(TourApiCategory.of("129999", "B02", "B02010700")).isEqualTo("LODGING"); // 펜션
		assertThat(TourApiCategory.of("129999", "B02", null)).isEqualTo("LODGING");
	}

	@Test
	@DisplayName("🔴 숙박이 내는 낱말은 조회 쪽이 찾는 낱말과 같다 — 한쪽만 고치면 목록이 조용히 0건으로 돌아간다")
	void lodgingWordMatchesTheQuerySide() {
		// 적재는 place.category 에 쓰고 조회는 AccommodationCategories.CODES 로 찾는다.
		// 두 곳에 글자를 따로 적어 두었으므로 어긋나는 것을 여기서 막는다.
		assertThat(TourApiCategory.of("129999", "B02", "B02010100"))
				.as("적재가 내는 숙소 낱말이 조회 목록에 없다")
				.isIn(AccommodationCategories.CODES);
	}

	@Test
	@DisplayName("음식은 여기서 판정하지 않는다 — 판독기가 애초에 넘기지 않는다")
	void foodIsNotThisMethodsJob() {
		assertThat(TourApiCategory.of("129999", "A05", "A05020100")).isNull();
	}

	@Test
	@DisplayName("모르는 대분류가 오면 비운다 — 지어내지 않는다")
	void unknownCat1IsEmpty() {
		assertThat(TourApiCategory.of("129999", "C01", null)).isNull();
		assertThat(TourApiCategory.of("129999", null, null)).isNull();
		assertThat(TourApiCategory.of("129999", "", null)).isNull();
	}

	@Test
	@DisplayName("🔴 내는 낱말은 앱의 여섯이거나 숙소 낱말이다 — 하나라도 벗어나면 그 갈래가 조용히 0 건이 된다")
	void everyProducedWordIsKnownSomewhere() {
		// 그물을 넓히기만 하고 없애지 않는다. 여섯은 사용자가 보내는 사전이고 숙소는 조회 쪽이
		// 쓰는 사전이라 출처가 다르다. 둘 중 어디에도 없는 낱말은 아무도 못 찾는다.
		List<String> known = new ArrayList<>(List.of(APP_WORDS));
		known.addAll(AccommodationCategories.CODES);

		String[] cat1s = { "A01", "A02", "A03", "A04", "A05", "B02", "C01" };
		String[] cat3s = { null, "A01011200", "A01010400", "A02010100", "A04010200", "A03021600",
				"A03022700", "A03021700" };
		for (String cat1 : cat1s) {
			for (String cat3 : cat3s) {
				String produced = TourApiCategory.of("129999", cat1, cat3);
				if (produced != null) {
					assertThat(produced)
							.as("앱도 조회도 모르는 낱말이다: cat1=%s cat3=%s", cat1, cat3)
							.isIn(known);
				}
			}
		}
	}

	@Test
	@DisplayName("🔴 숙소 낱말은 앱이 보내는 여섯과 겹치지 않는다 — 겹치면 취향 하나가 호텔 목록이 된다")
	void lodgingWordNeverCollidesWithSurveyWords() {
		assertThat(AccommodationCategories.CODES)
				.as("숙소 낱말이 취향 낱말과 같으면 그 취향을 고른 사용자 후보에 호텔이 섞인다")
				.doesNotContainAnyElementsOf(List.of(APP_WORDS));
	}
}
