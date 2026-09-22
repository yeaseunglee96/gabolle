package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.loader.AppFoodVocabulary;

/**
 * 채점기({@code BaselineCandidateScorer})가 앱이 보낸 코드와 {@code feature_key} 를
 * {@code contains} 로 글자 그대로 비교하므로, 적재가 붙이는 낱말이 앱의 어휘 밖으로
 * 나가면 아무것도 안 맞는다.
 */
class AppFoodVocabularyTest {

	/** {@code frontend/src/plan/foodConflicts.ts} 의 {@code FOODS} 여섯. 이 밖의 값은 안 맞는다. */
	private static final java.util.Set<String> APP_FOOD_CODES = java.util.Set.of(
			"SEAFOOD", "PORK_SOUP", "MILMYEON", "CAFE_DESSERT", "MARKET", "VEGETARIAN");

	/** {@code frontend/app/(plan)/taste.tsx} 의 {@code CATEGORIES} 여섯. */
	private static final java.util.Set<String> APP_CATEGORY_CODES = java.util.Set.of(
			"SEA_BEACH", "CITY", "CAFE_HEALING", "CULTURE_TEMPLE", "FOOD", "NATURE_WALK");

	@Test
	@DisplayName("해산물은 소분류로 가른다")
	void 해산물() {
		assertThat(AppFoodVocabulary.cuisineTags("횟집", "어느 횟집")).containsExactly("SEAFOOD");
		assertThat(AppFoodVocabulary.cuisineTags("해산물 구이/찜", "어느 조개구이")).containsExactly("SEAFOOD");
		assertThat(AppFoodVocabulary.cuisineTags("일식 회/초밥", "어느 스시")).containsExactly("SEAFOOD");
		assertThat(AppFoodVocabulary.cuisineTags("복 요리 전문", "어느 복집")).containsExactly("SEAFOOD");
	}

	@Test
	@DisplayName("🔴 돼지국밥은 소분류가 아니라 상호명으로 가른다 — 537곳이 백반/한정식에 있다")
	void 돼지국밥은_상호명으로_가른다() {
		assertThat(AppFoodVocabulary.cuisineTags("백반/한정식", "소문난돼지국밥")).containsExactly("PORK_SOUP");
		// 백반/한정식 10,640곳 중 돼지국밥집은 537곳뿐이다.
		assertThat(AppFoodVocabulary.cuisineTags("백반/한정식", "어느 한정식")).isEmpty();
		// 국/탕/찌개류 1,520곳 중 돼지국밥집은 5곳이다. 통째로 붙이면 거의 다 오답이다.
		assertThat(AppFoodVocabulary.cuisineTags("국/탕/찌개류", "어느 김치찌개")).isEmpty();
		// "국밥" 으로 넓히지 않는다 — 순대국밥·소고기국밥이 섞인다.
		assertThat(AppFoodVocabulary.cuisineTags("백반/한정식", "병천순대국밥")).isEmpty();
	}

	@Test
	@DisplayName("🔴 밀면은 냉면과 한 소분류에 묶여 있어 상호명으로 가른다")
	void 밀면은_상호명으로_가른다() {
		assertThat(AppFoodVocabulary.cuisineTags("냉면/밀면", "부경밀면")).containsExactly("MILMYEON");
		// 밀면집 389곳 중 89곳이 이 소분류 바깥에 있어 이름으로도 붙인다.
		assertThat(AppFoodVocabulary.cuisineTags("국수/칼국수", "만수르가야밀면")).containsExactly("MILMYEON");
		assertThat(AppFoodVocabulary.cuisineTags("냉면/밀면", "어느 평양냉면")).isEmpty();
	}

	@Test
	@DisplayName("카페·디저트는 소분류로 가른다. 떡집과 샌드위치는 안 붙인다")
	void 카페_디저트() {
		assertThat(AppFoodVocabulary.cuisineTags("카페", "어느 커피")).containsExactly("CAFE_DESSERT");
		assertThat(AppFoodVocabulary.cuisineTags("빵/도넛", "어느 베이커리")).containsExactly("CAFE_DESSERT");
		assertThat(AppFoodVocabulary.cuisineTags("아이스크림/빙수", "어느 빙수")).containsExactly("CAFE_DESSERT");
		// 앉아서 쉬는 곳이 아니라 사 가는 떡집이다.
		assertThat(AppFoodVocabulary.cuisineTags("떡/한과", "어느 떡집")).isEmpty();
		// 디저트가 아니라 가벼운 한 끼다.
		assertThat(AppFoodVocabulary.cuisineTags("토스트/샌드위치/샐러드", "어느 샌드위치")).isEmpty();
	}

	@Test
	@DisplayName("🔴 시장 먹거리와 채식은 이 자료로 못 가른다 — 비워 둔다")
	void 못_가르는_둘은_비운다() {
		// 업종에 "시장 안에 있나" 칸이 없고, 채식은 소분류에 아예 없다.
		for (String sub : new String[] { "백반/한정식", "김밥/만두/분식", "그 외 기타 간이 음식점", "구내식당" }) {
			assertThat(AppFoodVocabulary.cuisineTags(sub, "어느 가게"))
					.doesNotContain("MARKET", "VEGETARIAN");
		}
	}

	@Test
	@DisplayName("🔴 어떤 입력에도 앱의 여섯 낱말 밖으로 안 나간다")
	void 앱_어휘_밖으로_안_나간다() {
		String[] subs = { "백반/한정식", "카페", "요리 주점", "돼지고기 구이/찜", "김밥/만두/분식", "치킨",
				"일반 유흥 주점", "빵/도넛", "횟집", "중국집", "국/탕/찌개류", "경양식", "일식 회/초밥",
				"국수/칼국수", "해산물 구이/찜", "피자", "생맥주 전문", "떡/한과", "구내식당", "족발/보쌈",
				"그 외 기타 간이 음식점", "곱창 전골/구이", "냉면/밀면", "닭/오리고기 구이/찜", "버거",
				"소고기 구이/찜", "베트남식 전문", "마라탕/훠궈", "토스트/샌드위치/샐러드",
				"일식 카레/돈가스/덮밥", "아이스크림/빙수", "일식 면 요리", "뷔페", "파스타/스테이크",
				"기타 일식 음식점", "기타 한식 음식점", "무도 유흥 주점", "전/부침개", "기타 서양식 음식점",
				"복 요리 전문", "기타 동남아식 전문", "패밀리레스토랑", "분류 안된 외국식 음식점",
				// 처음 보는 값과 null 도 같이 넣는다.
				"처음 보는 소분류", "", null };
		for (String sub : subs) {
			assertThat(AppFoodVocabulary.cuisineTags(sub, "어느돼지국밥밀면가게"))
					.isSubsetOf(APP_FOOD_CODES);
			assertThat(AppFoodVocabulary.categoryTags(sub)).isSubsetOf(APP_CATEGORY_CODES);
		}
	}

	@Test
	@DisplayName("관심 태그는 언제나 FOOD 를 담고, 카페에만 CAFE_HEALING 이 더 붙는다")
	void 관심_태그() {
		assertThat(AppFoodVocabulary.categoryTags("카페")).containsExactly("FOOD", "CAFE_HEALING");
		assertThat(AppFoodVocabulary.categoryTags("횟집")).containsExactly("FOOD");
		// 빵집은 앉을 자리가 있는지 자료에 없어 안 넣는다.
		assertThat(AppFoodVocabulary.categoryTags("빵/도넛")).containsExactly("FOOD");
	}

	@Test
	@DisplayName("🔴 음식점 자료로는 바다·도심·문화·자연을 못 채운다. 채우지 않는다")
	void 자료가_없는_갈래는_안_채운다() {
		for (String sub : new String[] { "카페", "횟집", "백반/한정식", "요리 주점", "뷔페" }) {
			assertThat(AppFoodVocabulary.categoryTags(sub))
					.doesNotContain("SEA_BEACH", "CITY", "CULTURE_TEMPLE", "NATURE_WALK");
		}
	}

	@Test
	@DisplayName("이름에 둘이 다 들어 있으면 둘 다 붙는다")
	void 둘_다_붙을_수_있다() {
		assertThat(AppFoodVocabulary.cuisineTags("냉면/밀면", "돼지국밥밀면집"))
				.containsExactlyInAnyOrder("PORK_SOUP", "MILMYEON");
		assertThat(AppFoodVocabulary.cuisineTags("횟집", "해운대돼지국밥회센터"))
				.containsExactlyInAnyOrder("SEAFOOD", "PORK_SOUP");
	}
}
