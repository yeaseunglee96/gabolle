package com.gabolle.backend.place.loader;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 상가정보의 업종 낱말을 <b>앱이 실제로 보내는 낱말</b>로 옮긴다 — S15P21E201-635.
 *
 * <h2>🔴 왜 이 파일이 있나 — 채점이 글자 그대로 비교한다</h2>
 *
 * {@code BaselineCandidateScorer} 의 태그 겹침은 {@code placeTags.contains(code)} 다. 사용자가
 * 보낸 코드와 {@code place_feature.feature_key} 를 <b>문자열로 직접</b> 비교하고, 중간에 변환이
 * 없다. 그래서 서버가 {@code "한식"} 이라고 적어 두면 앱이 {@code "PORK_SOUP"} 이라고 물었을 때
 * <b>한 건도 안 맞는다.</b> 오류는 안 난다 — 겹침이 0 이 되어 그 항이 조용히 빠질 뿐이다.
 *
 * <p>앞선 적재(S15P21E201-636)가 {@code CUISINE_TAG} 에 상가정보 <b>중분류</b>(한식·일식·주점 …)를
 * 그대로 넣었다. 그 낱말은 앱 어디에도 없다. 여기서 앱의 여섯 코드로 옮긴다.
 *
 * <h2>🔴 소분류로 가르고, 소분류로 못 가르는 둘만 상호명으로 가른다</h2>
 *
 * 중분류(10종)는 너무 굵어서 못 가른다 — {@code 한식} 22,303곳 안에 회·국밥·삼겹살이 다 들어 있다.
 * 소분류(43종)가 기본이다. 다만 <b>부산의 두 음식은 소분류에 아예 없다.</b> 실제 파일을 세어
 * 확인한 것이다.
 *
 * <table border="1">
 * <caption>왜 상호명을 보는가 — 부산 2026-06 판 음식 53,716행 실측</caption>
 * <tr><th>앱 코드</th><th>소분류로 가능한가</th><th>실측</th></tr>
 * <tr><td>{@code PORK_SOUP}(돼지국밥)</td><td>🔴 <b>불가능</b></td>
 *     <td>상호명에 "돼지국밥" 이 든 550곳 중 <b>537곳의 소분류가 {@code 백반/한정식}</b> 이다.
 *     그 소분류는 10,640곳짜리라 통째로 붙이면 95% 가 오답이다. 반대로 {@code 국/탕/찌개류}
 *     1,520곳 중 돼지국밥집은 <b>5곳</b>뿐이다 — 어느 쪽으로도 못 가른다</td></tr>
 * <tr><td>{@code MILMYEON}(밀면)</td><td>🔴 <b>불가능</b></td>
 *     <td>소분류 {@code 냉면/밀면} 407곳은 냉면과 밀면이 <b>섞여 있고</b>, 밀면집 389곳 중
 *     89곳은 그 소분류 바깥({@code 백반/한정식}·{@code 국수/칼국수})에 있다</td></tr>
 * </table>
 *
 * <p>상호명은 <b>지어낸 신호가 아니다</b> — 가게가 스스로 무엇을 파는지 적어 둔 것이고,
 * "○○돼지국밥" 이라는 이름의 가게가 돼지국밥을 안 팔 수는 없다. 대신 <b>이름에 안 적은 집은
 * 안 붙는다.</b> 그것이 이 규칙의 대가이고, 틀린 짝을 붙이는 것보다 낫다.
 *
 * <h2>🔴 비워 둔 둘 — {@code MARKET} · {@code VEGETARIAN}</h2>
 *
 * <ul>
 * <li>{@code MARKET}(시장 먹거리) — 업종에는 "시장 안에 있나" 라는 칸이 없다. 주소에 "시장" 이
 * 든 곳이 818곳이지만 그것은 <b>업종이 아니라 위치</b>이고, "시장로"·"시장길" 같은 길 이름과
 * 전통시장이 아닌 상가건물까지 걸린다. 앱 자신도 이 항목만은 재료를 단정하지 않는다
 * ({@code foodConflicts.ts}: <i>"시장 먹거리처럼 재료가 뒤섞인 항목은 지어내지 않고 그대로 둔다"</i>)</li>
 * <li>{@code VEGETARIAN}(채식) — 채식·비건·사찰음식을 이름에 적은 집이 <b>53,716곳 중 4곳</b>이다.
 * 소분류에는 아예 없다. 게다가 채식은 안전 항목에 가깝다 — 업종에서 추정하면 안 된다</li>
 * </ul>
 *
 * <p>🔴 <b>애매하면 안 붙인다.</b> 안 붙은 가게는 그 축에서 점수를 못 받을 뿐이지만, 잘못 붙으면
 * 틀린 추천이 나가고 아무 오류도 안 난다.
 */
public final class AppFoodVocabulary {

	/**
	 * 상가정보 <b>소분류</b> → 앱의 {@code FOOD_PREFERENCE} 코드.
	 *
	 * <p>여기 없는 소분류는 <b>일부러</b> 없는 것이다. 43종 중 이 일곱만 앱의 여섯 코드 중
	 * 하나를 분명히 가리킨다.
	 *
	 * <ul>
	 * <li>{@code SEAFOOD} — 횟집 · 해산물 구이/찜 · 일식 회/초밥 · 복 요리 전문.
	 * 넷 다 <b>해산물이 아닌 경우가 없다</b></li>
	 * <li>{@code CAFE_DESSERT} — 카페 · 빵/도넛 · 아이스크림/빙수</li>
	 * </ul>
	 *
	 * <p>🔴 안 넣은 것 중 헷갈리는 셋을 적어 둔다. 다음 사람이 다시 재지 않게.
	 * <ul>
	 * <li>{@code 떡/한과}(603곳) — 디저트이긴 하나 앉아서 먹는 카페가 아니라 사 가는 떡집이다.
	 * 앱의 낱말은 "카페·디저트" 라 한 자리에서 쉬는 것을 함께 가리킨다</li>
	 * <li>{@code 토스트/샌드위치/샐러드}(202곳) — 디저트가 아니라 가벼운 한 끼다</li>
	 * <li>{@code 국/탕/찌개류}(1,520곳) — 돼지국밥이 5곳뿐이다. 위 표 참조</li>
	 * </ul>
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
	 * 상호명에 이 낱말이 있으면 그 코드다. <b>소분류로 못 가르는 둘만</b> 여기 있다.
	 *
	 * <p>🔴 "국밥" 이 아니라 <b>"돼지국밥"</b> 이다. "국밥" 으로 넓히면 921곳이 걸리지만 그 안에
	 * 순대국밥·소고기국밥·콩나물국밥이 섞인다. 앱이 묻는 것은 돼지국밥 하나다.
	 */
	private static final Map<String, String> CUISINE_BY_NAME_WORD = Map.of(
			"돼지국밥", "PORK_SOUP",
			"밀면", "MILMYEON");

	/**
	 * 앱의 {@code CATEGORY} 여섯 갈래 중 <b>이 자료로 채울 수 있는 것</b>.
	 *
	 * <p>대분류가 "음식" 이라 전부 {@code FOOD} 다. 그 하나만으로는 <b>순서를 못 바꾼다</b> —
	 * 모든 후보가 똑같이 맞아서 겹침 비율이 다 같아진다. 소분류 {@code 카페}(7,335곳)에만
	 * {@code CAFE_HEALING} 을 더 붙여, 그 갈래를 고른 사용자에게는 순서가 실제로 갈리게 한다.
	 *
	 * <p>🔴 <b>{@code SEA_BEACH}·{@code CITY}·{@code CULTURE_TEMPLE}·{@code NATURE_WALK} 는
	 * 이 자료로 못 채운다.</b> 음식점 목록에 해변도 사찰도 산책로도 없다. 업종 이름에서 지어내면
	 * 틀린 짝이 아무 오류도 없이 학습된다. 그 넷은 다른 자료가 들어와야 채워진다.
	 *
	 * <p>🔴 빵집을 {@code CAFE_HEALING} 에 안 넣었다. 앉을 자리가 있는지 자료에 없고, "카페 &
	 * 힐링" 은 사 가는 곳이 아니라 <b>머무는 곳</b>을 가리킨다.
	 */
	private static final String CAFE_SUB_CATEGORY = "카페";

	private AppFoodVocabulary() {
	}

	/**
	 * 이 가게에 붙일 {@code CUISINE_TAG} 코드들. 없으면 빈 집합이다.
	 *
	 * <p>둘 이상 붙을 수 있다 — 소분류가 {@code 냉면/밀면} 이면서 이름이 "밀면" 인 집처럼.
	 * 순서를 지키려고 {@link LinkedHashSet} 을 쓴다(같은 파일을 두 번 돌려도 같은 순서여야
	 * 로그를 대조할 수 있다).
	 *
	 * @param subCategory 상권업종소분류명
	 * @param name 상호명. 🔴 지점명은 안 본다 — 지점명은 위치("서면점")이지 음식이 아니다
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
	 * 이 가게에 붙일 {@code CATEGORY_TAG} 코드들 (S15P21E201-904 전에는 {@code INTEREST_TAG} 였다). 언제나 {@code FOOD} 가 들어 있다 —
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
