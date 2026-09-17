package com.gabolle.backend.place.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 무장애 여행 정보 → 접근성 표식 — S15P21E201-331.
 *
 * <h2>여기서 틀리면 사람이 못 들어가는 곳에 간다</h2>
 * 접근성은 추천에서 <b>하드 필터</b>다({@code user_place_code_map} 의 {@code MOBILITY} →
 * {@code ACCESSIBILITY_TAG} · {@code HARD_FILTER}). 휠체어를 쓰는 사람이 이 표식을 믿고
 * 그 장소를 고르므로, 틀린 표식은 취향이 안 맞는 정도가 아니라 <b>문 앞에서 돌아서게 하는</b>
 * 일이 된다. DB 도 그래서 이 갈래에 추정값을 못 넣게 막는다
 * ({@code ck_place_feature_safety_never_estimated}).
 *
 * <h2>원천이 그 말을 했을 때만 붙인다</h2>
 * 무장애 자료의 칸은 자유 문장이다. 그래서 "칸이 비어 있지 않다" 를 "접근 가능하다" 로 읽지
 * 않는다 — 실측 148건 중 31건은 접근성 이야기가 아니었다.
 *
 * <table border="1">
 * <caption>2026-09-11 수집본 148건의 {@code exit} 문장</caption>
 * <tr><th>문장</th><th>건</th><th>어떻게 읽나</th></tr>
 * <tr><td>"…휠체어 접근 가능함"</td><td>117</td><td>접근 가능하다고 원천이 말했다</td></tr>
 * <tr><td>"여닫이문" · "자동문"</td><td>일부</td><td>문 모양을 적은 것이다. 접근성 이야기가 아니다</td></tr>
 * <tr><td>"주출입구는 계단이 있고 보조출입구는 무단차임"</td><td>1</td><td>주출입구에 계단이 있다</td></tr>
 * <tr><td>"높낮이 차가 없어 휠체어를 이용하여 출입가능"</td><td>일부</td><td>같은 뜻인데 말이 다르다</td></tr>
 * </table>
 *
 * <p>마지막 줄이 이 판정을 좁게 만든 이유다. 뜻이 같은 다른 표현까지 받아들이려고 규칙을
 * 넓히면 <b>계단이 있다고 적힌 곳까지 함께 들어온다.</b> 그래서 원천이 쓴 <b>한 문구</b>
 * ({@code 휠체어 접근 가능})가 문장에 그대로 있을 때만 붙인다. 뜻이 같은데 표현이 다른 곳은
 * 표식이 안 붙고 <b>모름으로 남는다</b> — 그 손해는 후보가 조금 줄어드는 것이고, 반대 방향의
 * 잘못은 사람을 못 들어가는 곳으로 보내는 것이다. 둘은 크기가 다르다.
 *
 * <h2>🔴 2026-09-17 — {@code route} 칸을 함께 읽는다. 문구는 안 넓힌다</h2>
 *
 * 수집본 179건의 칸을 세어 보니 <b>읽지 않던 칸에 같은 사실이 들어 있었다.</b>
 *
 * <table border="1">
 * <caption>「휠체어 접근 가능」 문구가 있는 건수 (2026-09-17 실측)</caption>
 * <tr><th>칸</th><th>값 있음</th><th>그 문구</th></tr>
 * <tr><td>{@code exit}</td><td>148</td><td>117 ← 지금까지 보던 것</td></tr>
 * <tr><td>{@code route}</td><td>146</td><td>93</td></tr>
 * <tr><td colspan="2">합집합</td><td><b>127</b> (+10)</td></tr>
 * </table>
 *
 * <p>🔴 <b>다른 칸은 안 읽는다.</b> 같은 실측에서 {@code restroom}(84건 채워짐)은 「휠체어」를
 * <b>한 번도</b> 안 쓰고, {@code elevator}(95건)는 4건뿐이다. 칸이 채워져 있다는 것과 그 칸이
 * 접근성을 말한다는 것은 다르다 — 이 클래스가 처음부터 경계한 바로 그 혼동이다.
 *
 * <h2>🔴 문구를 넓히지 않은 이유 — 실측이 말린다</h2>
 *
 * 「휠체어」는 나오는데 그 문구가 없는 22건을 읽어 봤다. 뜻이 같은 것도 있지만
 * <b>반대인 것이 섞여 있다.</b>
 *
 * <pre>
 * 출입통로가 넓어 휠체어 … 진입 쉬움.  낮은 턱 있음.     ← 턱이 있다
 * 경사도 10도 내외의 경사구간 있음 … 휠체어 사용자 등     ← 경고문이다
 * 테이블 간격이 넓어 휠체어 … 이용 쉬움                   ← 내부 테이블 이야기다
 * </pre>
 *
 * 규칙을 넓히면 이 셋이 함께 들어온다. <b>칸을 더 읽는 것과 문구를 넓히는 것은 다른 일이고,
 * 여기서는 앞의 것만 한다.</b>
 *
 * <h2>무거운 짐은 비운다</h2>
 * 앱이 보내는 이동 조건은 셋({@code WHEELCHAIR}·{@code STROLLER}·{@code HEAVY_LUGGAGE})인데
 * 무장애 자료에 무거운 짐을 가리키는 칸이 없다. 엘리베이터가 있으면 짐 들기 쉽다는 것은
 * 추론이지 원천의 말이 아니다.
 */
public final class BarrierFreeAccessibility {

	/** 원천이 휠체어 접근을 말할 때 쓰는 문구. 이것이 있을 때만 붙인다. */
	private static final String WHEELCHAIR_PHRASE = "휠체어 접근 가능";

	private BarrierFreeAccessibility() {
	}

	/** 원천이 그 문구를 그대로 썼는가. 뜻을 해석하지 않는다. */
	private static boolean saysWheelchairAccessible(String text) {
		return text != null && text.contains(WHEELCHAIR_PHRASE);
	}

	/**
	 * 이 장소에 붙일 접근성 코드들.
	 *
	 * @param exitText     주출입구 칸의 문장
	 * @param routeText    주출입구까지의 경로 칸의 문장 — 2026-09-17 추가
	 * @param strollerText 유모차 칸의 문장
	 */
	public static List<String> of(String exitText, String routeText, String strollerText) {
		List<String> codes = new ArrayList<>(2);
		// 🔴 exit 과 route 를 **같은 문구 규칙**으로 본다. 둘 중 하나만 말해도 붙인다 —
		//    원천이 「출입구」와 「거기까지 가는 길」을 나눠 적었을 뿐 같은 사실이다.
		if (saysWheelchairAccessible(exitText) || saysWheelchairAccessible(routeText)) {
			codes.add("WHEELCHAIR");
		}
		if (strollerText != null && !strollerText.isBlank()) {
			// 유모차 칸은 "대여가능"·"유모차 있음" 처럼 있다는 말만 들어온다. 실측 13건에
			// 부정 표현이 하나도 없었다 — 그래도 뜻을 해석하지 않고 값이 있다는 사실만 쓴다.
			codes.add("STROLLER");
		}
		return List.copyOf(codes);
	}
}
