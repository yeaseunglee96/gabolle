package com.gabolle.backend.place.api;

import java.util.List;

/**
 * 「이 문항을 판정할 장소 자료가 지금 얼마나 있나」 — S15P21E201-1508.
 *
 * <p>여행 조건 화면이 <b>묻고서 못 지키는 약속</b>을 하지 않게 하려고 있다
 * (프론트는 S15P21E201-1044). 자료가 없는 문항에 「지금은 이 조건을 확인할 자료가 없어요」를
 * 붙이려면 <b>무엇이 없는지를 화면이 알아야</b> 하는데, 그 목록을 화면에 박으면 자료가
 * 들어온 날 거짓말이 된다.
 *
 * <p>🔴 <b>실제로 그렇게 됐을 뻔했다.</b> {@code S15P21E201-1044} 본문의 표는 2026-09-16
 * 실측인데 엿새 뒤 다시 재니 경사(0 → 2,682곳)와 그늘(0 → 1,936곳) 두 줄이 뒤집혔다.
 * 박아 뒀으면 지금 「경사 자료가 없어요」라고 말하고 있을 것이다.
 */
public record ConditionCoverageResponse(List<Condition> conditions) {

	/**
	 * 문항 하나와, 그 문항을 판정하는 데 쓰는 표식마다의 덮임.
	 *
	 * @param kind {@code PREFERENCE}(취향 문항) · {@code CONSTRAINT}(꼭 지켜야 하는 조건)
	 * @param code 사용자 문항 코드. 예: {@code ALLERGY}·{@code SLOPE_PREFERENCE}
	 * @param features 이 문항이 보는 장소 표식들. <b>여럿일 수 있다</b> — {@code MOBILITY} 는
	 *     접근성과 계단 둘을 본다. 뭉치지 않고 갈라서 주는 이유는 지금 접근성은 102곳인데
	 *     계단은 0곳이라, 합치면 「이동 조건 자료 있음」이 되어 계단 쪽 거짓말을 덮기 때문이다
	 */
	public record Condition(String kind, String code, List<Feature> features) {
	}

	/**
	 * 표식 하나의 덮임.
	 *
	 * @param featureType 장소 표식 갈래. 예: {@code ALLERGEN_TAG}
	 * @param placeCount 그 표식을 가진 장소 수. <b>근거가 {@code UNKNOWN} 인 줄은 안 센다</b> —
	 *     모른다는 것은 행이 없다는 뜻이고, 그것까지 세면 「자료가 있다」고 해 놓고 판정은 못 하는
	 *     상태가 된다
	 * @param totalPlaceCount 전체 장소 수. 비율은 <b>화면이</b> 낸다 — 서버가 「28% 면 충분한가」를
	 *     대신 정하지 않는다
	 */
	public record Feature(String featureType, long placeCount, long totalPlaceCount) {
	}
}
