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

	/**
	 * 이 장소에 붙일 접근성 코드들.
	 *
	 * @param exitText     주출입구 칸의 문장
	 * @param strollerText 유모차 칸의 문장
	 */
	public static List<String> of(String exitText, String strollerText) {
		List<String> codes = new ArrayList<>(2);
		if (exitText != null && exitText.contains(WHEELCHAIR_PHRASE)) {
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
