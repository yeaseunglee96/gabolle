package com.gabolle.backend.place.loader;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 장애인편의시설 실태조사의 항목 이름에서 접근성 코드를 정한다 — S15P21E201-1365.
 *
 * <h2>무엇을 읽는가</h2>
 * 실태조사 응답은 설치된 편의시설의 <b>이름만</b> 쉼표로 이어 붙여 준다. 적정·부적정을
 * 따로 주는 칸은 없다 (47건 전수 확인, 2026-09-21).
 *
 * <pre>
 * 주출입구(문), 주출입구 접근로, 주출입구 높이차이 제거
 * </pre>
 *
 * <h2>🔴 이름만 있는데 왜 접근 가능하다고 읽는가</h2>
 * 그 이름들이 <b>편의증진법이 수치로 정해 둔 항목</b>이기 때문이다. 「주출입구 접근로」는
 * 아무 길이 아니라 <b>유효폭 1.2m 이상 · 기울기 1/12 이하</b>를 만족해야 그 이름으로
 * 조사된다. 즉 폭과 기울기는 자료에 없는 것이 아니라 <b>이름 안에 이미 들어 있다.</b>
 *
 * <p>처음에는 반대로 판단했다 — <i>"항목이 있다는 것이 통과할 수 있다는 뜻은 아니다"</i>.
 * 그 판단을 뒤집은 증거가 셋이다.
 *
 * <ol>
 * <li><b>「장애인사용가능화장실」</b> 이라는 항목이 있다. 이름 자체가 판정이다. 조사한 항목을
 * 전부 나열하는 자료라면 이런 이름이 나올 수 없다</li>
 * <li>관광공사 무장애 자료가 <b>"휠체어 접근 가능"</b> 이라고 적은 11곳을 맞춰 보니
 * <b>11곳 전부</b>에 접근로 또는 높이차이 제거가 있었다. 어긋난 곳이 없다. 서로 다른 기관이
 * 따로 조사한 값이다</li>
 * <li>항목이 <b>아예 비어 있는 곳이 2곳</b> 있고, 일부만 실린 곳도 있다. 전부 나열하는
 * 자료라면 이렇게 생기지 않는다</li>
 * </ol>
 *
 * <h2>그래서 좁게 잡는다</h2>
 * {@link AccessibilityLoader} 가 이 값을 {@code VERIFIED} 로 저장한다. DB 가 접근성에
 * {@code ESTIMATED} 를 아예 거부하기 때문이다({@code ck_place_feature_safety_never_estimated}).
 * <b>중간 등급이 없다.</b> 그래서 통과시킬 항목을 둘로 줄였다.
 *
 * <table border="1">
 * <caption>판정</caption>
 * <tr><th>항목</th><th></th><th>왜</th></tr>
 * <tr><td>주출입구 접근로</td><td>🟢 붙인다</td><td>폭·기울기 기준을 만족해야 이 이름이 된다</td></tr>
 * <tr><td>주출입구 높이차이 제거</td><td>🟢 붙인다</td><td>턱이 없다는 말이다</td></tr>
 * <tr><td><b>주출입구(문)</b></td><td>🔴 <b>안 붙인다</b></td>
 *     <td>문이 조사됐다는 것뿐이다. 거기까지 갈 수 있는지를 말하지 않는다</td></tr>
 * <tr><td>장애인사용가능화장실 · 승강기 · 장애인전용주차구역</td><td>🔴 안 붙인다</td>
 *     <td>앱이 쓰는 이동 조건에 대응하는 코드가 없다</td></tr>
 * </table>
 *
 * <p>실측에서 문이 있는 곳이 41곳, 접근로가 있는 곳이 34곳이었다. 차이 나는 7곳은
 * <b>안 붙이는 쪽</b>으로 둔다. 휠체어 조건을 건 사용자에게 "갈 수 있다" 고 잘못 말하는 것이
 * 아무 말도 안 하는 것보다 나쁘다.
 *
 * <h2>없다는 말은 안 적는다</h2>
 * 코드가 안 나오면 행을 만들지 않는다. {@link BarrierFreeAccessibility} 와 같은 원칙이다 —
 * 행이 없는 것은 "접근 불가" 가 아니라 "모른다" 다.
 */
public final class FacilityAccessibility {

	/** 항목 이름의 공백. 같은 항목이 공백을 넣기도 빼기도 해서 지우고 본다. */
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/** 주출입구까지 가는 길이 기준(유효폭 1.2m·기울기 1/12)을 만족해 설치됐다는 항목. */
	static final String ROUTE = "주출입구접근로";

	/** 주출입구의 턱을 없앴다는 항목. */
	static final String NO_STEP = "주출입구높이차이제거";

	/** 앱이 쓰는 이동 조건 코드. 이 자료로 채울 수 있는 것은 이것 하나다. */
	static final String WHEELCHAIR = "WHEELCHAIR";

	private FacilityAccessibility() {
	}

	/**
	 * 이 시설에 붙일 접근성 코드들.
	 *
	 * @param evalInfo 실태조사가 준 항목 이름 목록. 쉼표로 이어 붙은 한 문자열이다
	 * @return 붙일 코드. 근거가 없으면 빈 목록
	 */
	public static List<String> of(String evalInfo) {
		if (evalInfo == null || evalInfo.isBlank()) {
			return List.of();
		}
		// 🔴 같은 항목이 공백을 넣기도 빼기도 한다 —「주출입구 높이차이 제거」71곳 옆에
		//    「주출입구높이차이제거(경사로)」3곳이 있다. 정확히 맞춰 보면 뒤엣것을 놓치고,
		//    놓친 것은 "근거 없음" 으로 세어져 아무 오류도 안 낸다. 그래서 공백을 지우고 본다.
		//    🔴 쉼표는 안 지운다. 지우면 앞 항목 끝과 뒤 항목 앞이 붙어 없던 이름이 생긴다.
		String packed = WHITESPACE.matcher(evalInfo).replaceAll("");
		if (packed.contains(ROUTE) || packed.contains(NO_STEP)) {
			return List.of(WHEELCHAIR);
		}
		return List.of();
	}
}
