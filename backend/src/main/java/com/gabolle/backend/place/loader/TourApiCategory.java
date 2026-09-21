package com.gabolle.backend.place.loader;

import java.util.Set;

/**
 * 관광공사 분류를 앱이 보내는 낱말로 옮긴다.
 *
 * <p>후보 필터가 사용자의 {@code CATEGORY} 답을 {@code place.category} 와 글자 그대로 비교한다.
 * 여기서 다른 낱말을 내면 그 갈래를 고른 사용자의 후보가 조용히 0 건이 된다. 앱이 쓰는 여섯은
 * {@code SEA_BEACH}·{@code CITY}·{@code CAFE_HEALING}·{@code CULTURE_TEMPLE}·{@code FOOD}·
 * {@code NATURE_WALK} 다.
 *
 * <p>이름으로 짐작하지 않고 원천이 스스로 매긴 {@code cat1} 을 쓴다. 자연은
 * {@code NATURE_WALK}(해수욕장만 {@code SEA_BEACH}), 인문은 {@code CULTURE_TEMPLE}, 쇼핑은
 * {@code CITY} 다. 음식은 장소 적재 자체를 안 한다.
 *
 * <p>레포츠와 숙박은 비운다. 앱의 여섯 낱말 중 그것을 가리키는 것이 없고, 레포츠 대분류에는
 * 캠핑장·서핑학교와 실탄사격장이 함께 들어 있어 통째로 {@code NATURE_WALK} 에 넣으면 "자연
 * 산책" 을 고른 사용자에게 실내 사격장이 섞인다. 비워 두면 그 갈래에서 안 나올 뿐이고, 장소로는
 * 존재해서 숙소 지정·필수 방문지 지정에는 쓸 수 있다. 더 잘 나누려면 {@code cat3} 단위로 봐야
 * 하는데 그것은 채점 기준을 정하는 일이다.
 *
 * <p>해수욕장 소분류에 드는 곳은 몇 안 된다. 수집이 빠뜨린 것이 아니라 관광공사 「관광지」
 * 목록을 전부 받은 결과가 그렇다. 해안 산책로·항구·등대·섬까지 넓히면 숫자는 늘지만 「해변」 을
 * 고른 사람에게 등대가 나온다.
 *
 * <p>다만 바닷가를 걷는 길 둘은 {@link #COASTAL_WALK_CONTENT_IDS} 로 콕 집어 넣는다. 그 소분류
 * ({@code A01010500})에는 지질공원 성격이 큰 곳이 함께 있어 {@code cat3} 로는 못 가른다.
 */
final class TourApiCategory {

	/** 자연 안에서 해수욕장·해변. */
	private static final String CAT3_BEACH = "A01011200";

	/**
	 * 소분류 {@code A01010500} 안에서 「바닷가를 걷는 길」인 것만. 같은 소분류의 나머지는
	 * 지질공원 성격이 커서 일부러 뺐다.
	 */
	private static final Set<String> COASTAL_WALK_CONTENT_IDS = Set.of("252561", "2822343");

	private TourApiCategory() {
	}

	/**
	 * 앱이 보내는 갈래 낱말. 옮길 낱말이 없으면 {@code null} 이고, 호출자는 그것을 정상으로
	 * 다뤄야 한다. {@code contentId} 는 {@link #COASTAL_WALK_CONTENT_IDS} 콕 집기에만 쓴다.
	 */
	static String of(String contentId, String cat1, String cat3) {
		if (COASTAL_WALK_CONTENT_IDS.contains(contentId)) {
			return "SEA_BEACH";
		}
		if (cat1 == null) {
			return null;
		}
		return switch (cat1) {
			case "A01" -> CAT3_BEACH.equals(cat3) ? "SEA_BEACH" : "NATURE_WALK";
			case "A02" -> "CULTURE_TEMPLE";
			case "A04" -> "CITY";
			// 레포츠·숙박 — 앱의 낱말에 대응하는 것이 없다.
			default -> null;
		};
	}
}
