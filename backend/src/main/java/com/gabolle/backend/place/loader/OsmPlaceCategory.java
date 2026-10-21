package com.gabolle.backend.place.loader;

import java.util.Map;

/**
 * OpenStreetMap 의 태그를 {@code place.category} 한 값으로 옮긴다.
 *
 * <p>🔴 <b>모르는 태그는 갈래를 지어내지 않고 {@code null} 을 돌려준다.</b> 부르는 쪽이 그 행을
 * 안 넣는다. 억지로 갈래를 붙이면 「은행·주유소·유치원」이 여행 후보에 섞이고, 그 사실은 추천
 * 결과를 사람이 눈으로 보기 전까지 아무 데도 안 드러난다.
 *
 * <p>여기 적힌 낱말은 {@code TourApiCategory}·{@code SbizPlaceLoader} 가 쓰는 것과 같은 어휘다.
 * 어긋나면 그 갈래를 고른 사용자의 후보에서 이 장소들만 통째로 빠진다.
 */
public final class OsmPlaceCategory {

	/**
	 * {@code amenity} 태그 → 갈래.
	 * <p>
	 * 편의점·빵집을 {@code CAFE_HEALING} 에 넣지 않는다. 「카페·힐링」을 고른 사람에게 편의점이
	 * 나오면 그건 맞는 추천이 아니다 — 빵집은 앉아 있을 수 있어 카페 쪽에 둔다.
	 */
	private static final Map<String, String> BY_AMENITY = Map.ofEntries(
			Map.entry("restaurant", "FOOD"),
			Map.entry("fast_food", "FOOD"),
			Map.entry("food_court", "FOOD"),
			Map.entry("bar", "FOOD"),
			Map.entry("pub", "FOOD"),
			Map.entry("cafe", "CAFE_HEALING"),
			Map.entry("place_of_worship", "CULTURE_TEMPLE"),
			Map.entry("arts_centre", "CULTURE_TEMPLE"),
			Map.entry("theatre", "CULTURE_TEMPLE"));

	/** {@code tourism} 태그 → 갈래. 숙소 다섯은 전부 {@code LODGING} 이다. */
	private static final Map<String, String> BY_TOURISM = Map.ofEntries(
			Map.entry("hotel", "LODGING"),
			Map.entry("motel", "LODGING"),
			Map.entry("guest_house", "LODGING"),
			Map.entry("hostel", "LODGING"),
			Map.entry("apartment", "LODGING"),
			Map.entry("attraction", "CULTURE_TEMPLE"),
			Map.entry("museum", "CULTURE_TEMPLE"),
			Map.entry("gallery", "CULTURE_TEMPLE"),
			Map.entry("viewpoint", "NATURE_WALK"));

	/** {@code shop} 태그 → 갈래. 빵집만 받는다 — 나머지 가게는 여행 후보가 아니다. */
	private static final Map<String, String> BY_SHOP = Map.of(
			"bakery", "CAFE_HEALING",
			"coffee", "CAFE_HEALING",
			"pastry", "CAFE_HEALING");

	/** {@code leisure} 태그 → 갈래. */
	private static final Map<String, String> BY_LEISURE = Map.of(
			"park", "NATURE_WALK",
			"garden", "NATURE_WALK",
			"nature_reserve", "NATURE_WALK");

	/** {@code natural} 태그 → 갈래. 부산이라 해변이 여기 온다. */
	private static final Map<String, String> BY_NATURAL = Map.of(
			"beach", "SEA_BEACH",
			"peak", "NATURE_WALK",
			"wood", "NATURE_WALK");

	private OsmPlaceCategory() {
	}

	/**
	 * 이 장소의 갈래. 아는 태그가 하나도 없으면 {@code null} 이다.
	 *
	 * <p>보는 차례가 정해져 있다 — {@code tourism} 을 {@code amenity} 보다 먼저 본다. 숙소에
	 * {@code amenity=restaurant} 가 같이 붙어 있는 일이 흔한데(호텔 안 식당), 그때 이 장소는
	 * 식당이 아니라 숙소다. 차례를 바꾸면 모텔 601곳이 음식점이 된다.
	 *
	 * <p>{@code historic} 은 값을 안 가린다. 그 태그가 붙었다는 것 자체가 「옛것」이라는 뜻이고,
	 * 값(성·기념비·유적)을 하나하나 적으면 목록이 늘 낡는다.
	 */
	public static String of(Map<String, String> tags) {
		if (tags == null || tags.isEmpty()) {
			return null;
		}
		if (isNotATravelPlace(tags)) {
			return null;
		}
		String category = lookup(BY_TOURISM, tags.get("tourism"));
		if (category == null) {
			category = lookup(BY_NATURAL, tags.get("natural"));
		}
		if (category == null) {
			category = lookup(BY_LEISURE, tags.get("leisure"));
		}
		if (category == null) {
			category = lookup(BY_AMENITY, tags.get("amenity"));
		}
		if (category == null) {
			category = lookup(BY_SHOP, tags.get("shop"));
		}
		if (category == null && tags.containsKey("historic")) {
			category = "CULTURE_TEMPLE";
		}
		return category;
	}

	/**
	 * 갈래가 붙더라도 여행 장소로 받지 않는 것 (S15P21E201-2038). 운영에서 실제로 추천됐다가 지운 것들이다.
	 * <ul>
	 * <li>조형물·동상({@code tourism=artwork}) — 들러서 시간을 보낼 곳이 아니라 길가의 조각 하나다.
	 * 이 태그는 위 표에서도 뺐다. 여기 한 번 더 적는 것은 다른 태그({@code historic})와 같이 붙어 들어오는 것을 막으려고다.
	 * <li>기념비({@code historic=memorial·monument}) — 같은 까닭.
	 * <li>교회·성당({@code place_of_worship} 가운데 기독교이거나 이름에 교회·성당) — 동네 예배당이다. 절은 받는다.
	 * <li>이름이 「나무」로 끝나는 점 — 보호수 한 그루를 전망대로 올려 둔 것이다.
	 * </ul>
	 */
	static boolean isNotATravelPlace(Map<String, String> tags) {
		if ("artwork".equals(tags.get("tourism"))) {
			return true;
		}
		String historic = tags.get("historic");
		if ("memorial".equals(historic) || "monument".equals(historic)) {
			return true;
		}
		String name = tags.getOrDefault("name:ko", tags.getOrDefault("name", "")).strip();
		if ("place_of_worship".equals(tags.get("amenity"))
				&& ("christian".equals(tags.get("religion")) || name.contains("교회") || name.contains("성당"))) {
			return true;
		}
		return name.endsWith("나무") || "tree".equals(tags.get("natural"));
	}

	private static String lookup(Map<String, String> table, String value) {
		return (value == null) ? null : table.get(value);
	}
}
