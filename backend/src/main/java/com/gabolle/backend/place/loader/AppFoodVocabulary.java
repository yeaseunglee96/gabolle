package com.gabolle.backend.place.loader;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 상가정보의 업종 낱말을 앱이 실제로 보내는 낱말로 옮긴다.
 *
 * <p>채점의 태그 겹침은 사용자가 보낸 코드와 {@code place_feature.feature_key} 를 문자열로 직접
 * 비교한다. 중간에 변환이 없어, 서버가 {@code "한식"} 이라고 적어 두면 앱의 {@code "PORK_SOUP"}
 * 과는 한 건도 안 맞는다. 오류는 안 나고 겹침이 0 이 되어 그 항이 조용히 빠질 뿐이다.
 *
 * <p>소분류(43종)로 가르는 것이 기본이다. 중분류(10종)는 너무 굵어 못 가르고(한식 안에 회·국밥·
 * 삼겹살이 다 있다), 돼지국밥과 밀면은 소분류에 아예 없어 상호명으로만 가를 수 있다. 대신
 * 이름에 안 적은 집은 안 붙는다 — 틀린 짝을 붙이는 것보다 낫다.
 *
 * <p>{@code MARKET}(시장 먹거리)과 {@code VEGETARIAN}(채식)은 비워 둔다. 업종에는 시장 안에
 * 있는지가 없고 주소의 "시장" 은 업종이 아니라 위치다. 채식은 이름에 적은 집이 거의 없고
 * 안전 항목에 가까워 업종에서 추정하면 안 된다.
 *
 * <p>애매하면 안 붙인다. 안 붙은 가게는 그 축에서 점수를 못 받을 뿐이지만, 잘못 붙으면 틀린
 * 추천이 나가고 아무 오류도 안 난다.
 */
public final class AppFoodVocabulary {

	/**
	 * 상가정보 소분류 → 앱의 {@code FOOD_PREFERENCE} 코드. 여기 없는 소분류는 일부러 없는
	 * 것이다 — 43종 중 이 일곱만 앱의 여섯 코드 중 하나를 분명히 가리킨다.
	 *
	 * <p>뺀 것 중 헷갈리는 셋: {@code 떡/한과} 는 앉아 쉬는 카페가 아니라 사 가는 떡집,
	 * {@code 토스트/샌드위치/샐러드} 는 디저트가 아니라 가벼운 한 끼, {@code 국/탕/찌개류} 에는
	 * 돼지국밥이 거의 없다.
	 */
	private static final Map<String, String> CUISINE_BY_SUB_CATEGORY = Map.of(
			"횟집", "SEAFOOD",
			"해산물 구이/찜", "SEAFOOD",
			"일식 회/초밥", "SEAFOOD",
			"복 요리 전문", "SEAFOOD",
			"카페", "CAFE_DESSERT",
			"빵/도넛", "CAFE_DESSERT",
			"아이스크림/빙수", "CAFE_DESSERT");

	/**
	 * 상호명에 이 낱말이 있으면 그 코드다. 소분류로 못 가르는 둘만 여기 있다.
	 * "국밥" 이 아니라 "돼지국밥" 인 것은 순대국밥·소고기국밥·콩나물국밥이 섞여 들어오기
	 * 때문이다 — 앱이 묻는 것은 돼지국밥 하나다.
	 */
	private static final Map<String, String> CUISINE_BY_NAME_WORD = Map.of(
			"돼지국밥", "PORK_SOUP",
			"밀면", "MILMYEON");

	/**
	 * 대분류가 "음식" 이라 {@code CATEGORY} 는 전부 {@code FOOD} 이고, 그것만으로는 모든 후보가
	 * 똑같이 맞아 순서가 안 갈린다. 소분류 카페에만 {@code CAFE_HEALING} 을 더 붙여 그 갈래를
	 * 고른 사용자에게는 순서가 갈리게 한다.
	 *
	 * <p>{@code SEA_BEACH}·{@code CITY}·{@code CULTURE_TEMPLE}·{@code NATURE_WALK} 는 이 자료로
	 * 못 채운다. 빵집도 {@code CAFE_HEALING} 이 아니다 — 앉을 자리가 있는지 자료에 없고,
	 * 그 낱말은 사 가는 곳이 아니라 머무는 곳을 가리킨다.
	 */
	private static final String CAFE_SUB_CATEGORY = "카페";

	private AppFoodVocabulary() {
	}

	/**
	 * 이 가게에 붙일 {@code CUISINE_TAG} 코드들. 없으면 빈 집합이고, 소분류와 상호명이 둘 다
	 * 맞으면 둘 이상 붙는다. 같은 파일을 두 번 돌려도 로그를 대조할 수 있도록
	 * {@link LinkedHashSet} 으로 순서를 지킨다. 상호명만 보고 지점명은 안 본다 — 지점명은
	 * 위치이지 음식이 아니다.
	 */
	public static Set<String> cuisineTags(String subCategory, String name) {
		Set<String> tags = new LinkedHashSet<>();
		String sub = subCategory == null ? "" : subCategory.trim();
		String bySub = CUISINE_BY_SUB_CATEGORY.get(sub);
		if (bySub != null) {
			tags.add(bySub);
		}
		String storeName = name == null ? "" : name;
		CUISINE_BY_NAME_WORD.forEach((word, code) -> {
			if (storeName.contains(word)) {
				tags.add(code);
			}
		});
		return tags;
	}

	/**
	 * 이 가게에 붙일 {@code CATEGORY_TAG} 코드들. 언제나 {@code FOOD} 가 들어 있다 —
	 * 대분류가 "음식" 인 행만 여기까지 온다.
	 */
	public static Set<String> categoryTags(String subCategory) {
		Set<String> tags = new LinkedHashSet<>();
		tags.add("FOOD");
		if (CAFE_SUB_CATEGORY.equals(subCategory == null ? "" : subCategory.trim())) {
			tags.add("CAFE_HEALING");
		}
		return tags;
	}
}
