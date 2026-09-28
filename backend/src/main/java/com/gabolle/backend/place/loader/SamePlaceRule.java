package com.gabolle.backend.place.loader;

import java.util.Locale;
import java.util.Set;

import com.gabolle.backend.place.service.ChainBrand;

/**
 * 새로 넣으려는 장소가 이미 있는 장소와 「같은 곳」인가 (S15P21E201-1620).
 *
 * <p>🔴 <b>장소 합치기(S15P21E201-1619)가 운영 중복을 고를 때 쓴 규칙 그대로다.</b> 합치기는 이미 들어온 중복을 한 번
 * 치웠고, 이 규칙은 다음 적재가 같은 중복을 다시 만들지 않게 한다. 둘이 다르면 합친 뒤에도 중복이 새로 생기거나, 합치지
 * 않은 짝을 적재기만 막는 어긋남이 생긴다.
 *
 * <ul>
 *   <li><b>같다</b> — 이름({@link #nameKey})이 같고 150m 안. 전국 체인({@link ChainBrand})은 지점이 붙어 있을 수 있어
 *       30m 안일 때만 같다</li>
 *   <li><b>사람 확인</b> — 체인의 같은 이름 30~150m, 그리고 해변·산책로·공원 갈래의 같은 이름 150m~1km(해변이 길어 두
 *       점이 멀다 — 송정해수욕장 두 줄이 381m 떨어져 있었다)</li>
 * </ul>
 *
 * <p>이름이 같고 150~500m 인 흔한 이름(횟집 등)이나 한쪽 이름이 다른 쪽을 품는 경우는 합치기에서도 사람 확인이었지만
 * 여기서는 보지 않는다 — 흔한 이름의 다른 가게를 막으면 없는 장소를 만드는 것보다 나쁘다.
 */
public final class SamePlaceRule {

	/** 이름이 같으면 같은 곳으로 보는 거리(m). */
	public static final double SAME_M = 150;

	/** 체인의 같은 이름을 같은 곳으로 보는 거리(m). 더 멀면 다른 지점일 수 있다. */
	public static final double CHAIN_SAME_M = 30;

	/** 넓은 갈래의 같은 이름을 사람 확인으로 돌리는 거리(m). */
	public static final double WIDE_M = 1_000;

	/** 한 장소가 넓게 퍼져 있어 좌표 두 개가 멀리 찍히는 갈래 — 해변·산책로·공원(공원은 {@code NATURE_WALK} 로 들어온다). */
	static final Set<String> WIDE_CATEGORIES = Set.of("SEA_BEACH", "NATURE_WALK");

	/** 판정. 같은 곳이 아니면 판정 자체가 없다({@code null}). */
	public enum Kind {

		/** 같은 곳 — 넣지 않는다. */
		SAME,

		/** 체인의 같은 이름 30~150m — 넣지 않고 사람이 본다. */
		REVIEW_CHAIN,

		/** 넓은 갈래의 같은 이름 150m~1km — 넣지 않고 사람이 본다. */
		REVIEW_WIDE
	}

	private SamePlaceRule() {
	}

	/**
	 * 비교용 이름 — 괄호 속(영문·설명)을 빼고, 빈칸과 부호({@code ·-.,&'"!~/})를 빼고, 소문자로 맞춘다. 「파라다이스 호텔
	 * 부산」과 「파라다이스호텔부산」이 같아진다. 합치기가 쓴 것과 같은 규칙이다.
	 *
	 * @return 비교할 글자가 남지 않으면 빈 문자열 — 빈 이름끼리는 같다고 하지 않는다
	 */
	public static String nameKey(String name) {
		if (name == null) {
			return "";
		}
		String withoutParentheses = name.replaceAll("\\([^)]*\\)", "");
		return withoutParentheses.replaceAll("(?U)[\\s·\\-.,&'\"!~/]", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * 한 쌍을 본다.
	 *
	 * @return 같은 곳이거나 사람이 볼 짝이면 그 종류, 아니면 {@code null}
	 */
	public static Kind judge(String newName, String newCategory, String existingName, String existingCategory,
			double distanceM) {
		String key = nameKey(newName);
		if (key.isEmpty() || !key.equals(nameKey(existingName))) {
			return null;
		}
		if (ChainBrand.brandOf(newName) != null || ChainBrand.brandOf(existingName) != null) {
			if (distanceM <= CHAIN_SAME_M) {
				return Kind.SAME;
			}
			return (distanceM <= SAME_M) ? Kind.REVIEW_CHAIN : null;
		}
		if (distanceM <= SAME_M) {
			return Kind.SAME;
		}
		boolean wide = isWide(newCategory) || isWide(existingCategory);
		return (wide && distanceM <= WIDE_M) ? Kind.REVIEW_WIDE : null;
	}

	/** 갈래가 없는 장소도 있다(운영 84곳) — {@code Set.of} 는 {@code contains(null)} 에서 멈추므로 먼저 거른다. */
	private static boolean isWide(String category) {
		return category != null && WIDE_CATEGORIES.contains(category);
	}
}
