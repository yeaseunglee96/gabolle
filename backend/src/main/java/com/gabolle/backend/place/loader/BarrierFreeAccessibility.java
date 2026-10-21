package com.gabolle.backend.place.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 무장애 여행 정보 → 접근성 표식.
 *
 * <p>접근성은 추천에서 하드 필터다({@code MOBILITY} → {@code ACCESSIBILITY_TAG}). 틀린 표식은
 * 취향이 안 맞는 정도가 아니라 사람을 못 들어가는 곳으로 보낸다. DB 도 이 갈래에는 추정값을
 * 못 넣게 막는다({@code ck_place_feature_safety_never_estimated}).
 *
 * <p>무장애 자료의 칸은 자유 문장이라 "칸이 비어 있지 않다" 를 "접근 가능하다" 로 읽지 않는다.
 * 원천이 쓴 한 문구({@code 휠체어 접근 가능})가 문장에 그대로 있을 때만 붙이고, 뜻이 같은데
 * 표현이 다른 곳은 모름으로 남는다. 문구를 넓히지 않는 것은 「휠체어」가 나오는 문장에
 * "낮은 턱 있음"·"경사구간 있음" 처럼 반대 사실을 적은 것이 섞여 있기 때문이다.
 *
 * <p>읽는 칸은 {@code exit} 과 {@code route} 둘뿐이다. {@code restroom}·{@code elevator} 는
 * 채워져 있어도 접근성을 말하지 않는다.
 *
 * <p>앱이 보내는 이동 조건 셋 중 {@code HEAVY_LUGGAGE} 는 비운다 — 무거운 짐을 가리키는 칸이
 * 자료에 없다. 엘리베이터가 있으면 짐 들기 쉽다는 것은 추론이지 원천의 말이 아니다.
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
	 * 이 장소에 붙일 접근성 코드들. {@code exitText} 는 주출입구 칸, {@code routeText} 는
	 * 거기까지 가는 경로 칸, {@code strollerText} 는 유모차 칸의 문장이다.
	 *
	 * <p>🔴 유모차 칸으로는 {@code STROLLER} 를 붙이지 않는다. 그 칸은 「유아차를 빌려주는가」를 적는 자리다 — 부산
	 * 수집본(bigData/data/raw/tourapi/tourapi-barrier-free-busan.ndjson, 181곳)에서 값이 있는 15곳이 전부
	 * 「대여가능」·「유모차 무료 대여(2대,1층안내데스크)」·「대여 가능(10대/안내소)」·「유모차 있음」·「가능」 같은 대여
	 * 안내였다. 예전에는 값이 있기만 하면 「유아차로 갈 수 있음(확인됨)」으로 붙여서, 유아차를 빌려주는 산 위 박물관이
	 * 경사 검사도 건너뛰고 확인된 곳으로 통과했다(확인된 표식은 경사 추정보다 앞선다). 빌려준다는 것은 들어갈 수
	 * 있다는 말이 아니다. 접근을 말하는 문구가 원천에 생기면 그때 휠체어처럼 그 문구 하나로 붙인다.
	 */
	public static List<String> of(String exitText, String routeText, String strollerText) {
		List<String> codes = new ArrayList<>(1);
		// exit 과 route 는 같은 문구 규칙으로 본다. 원천이 출입구와 거기까지 가는 길을 나눠
		// 적었을 뿐 같은 사실이라, 둘 중 하나만 말해도 붙인다.
		if (saysWheelchairAccessible(exitText) || saysWheelchairAccessible(routeText)) {
			codes.add("WHEELCHAIR");
		}
		// strollerText 는 읽지 않는다 — 위 설명. 매개변수는 부르는 쪽이 칸을 그대로 넘기는 모양을 지키려고 둔다.
		return List.copyOf(codes);
	}
}
