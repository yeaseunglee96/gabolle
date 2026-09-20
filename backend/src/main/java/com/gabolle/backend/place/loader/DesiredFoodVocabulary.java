package com.gabolle.backend.place.loader;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 상가정보 상호명에서 "먹고 싶은 부산 음식 8종" 중 이 가게가 실제로 파는 것을 찾는다.
 *
 * <p>{@link AppFoodVocabulary#cuisineTags}가 붙이는 {@code CUISINE_TAG}는 취향 점수 매칭용이라
 * 다른 서랍({@code DESIRED_FOOD_TAG})에 넣는다. 이쪽은 "밀면이 먹고 싶다" 를 고르면 그 밀면집이
 * 일정에 들어가야 한다는 검색·필터링 질문이지 취향 점수가 아니다. 같은 서랍에 두면 두 질문이
 * 섞인다.
 *
 * <p>8종 중 {@code BOKGUK} 하나만 여기 있다. {@code MILMYEON}·{@code PORK_SOUP}·
 * {@code SEAFOOD} 는 {@link AppFoodVocabulary}가 이미 가르므로 복제하지 않는다.
 * 씨앗호떡·동래파전·부산어묵·낙곱새는 가진 표본에서 이름이 걸리는 가게가 거의 없어 채우지
 * 않았다 — 판다고 표시했는데 실제로는 없는 상태를 만드느니 비워 둔다.
 */
public final class DesiredFoodVocabulary {

	/** {@code place_feature.feature_type}. */
	public static final String FEATURE_TYPE = "DESIRED_FOOD_TAG";

	/**
	 * 상호명에 이 낱말이 있으면 그 코드다. {@link AppFoodVocabulary#CUISINE_BY_NAME_WORD}와
	 * 같은 방식 — 상호명은 가게가 스스로 무엇을 파는지 적어 둔 것이라 지어낸 신호가 아니다.
	 */
	private static final Map<String, String> BY_NAME_WORD = Map.of(
			"복국", "BOKGUK");

	private DesiredFoodVocabulary() {
	}

	/**
	 * 이 가게에 붙일 {@code DESIRED_FOOD_TAG} 코드들. 없으면 빈 집합이다. 상호명만 보고
	 * 지점명은 안 본다 — 위치이지 음식이 아니다.
	 */
	public static Set<String> desiredFoodTags(String name) {
		Set<String> tags = new LinkedHashSet<>();
		String storeName = name == null ? "" : name;
		BY_NAME_WORD.forEach((word, code) -> {
			if (storeName.contains(word)) {
				tags.add(code);
			}
		});
		return tags;
	}
}
