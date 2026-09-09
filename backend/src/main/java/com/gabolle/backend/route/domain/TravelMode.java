package com.gabolle.backend.route.domain;

/**
 * 경로를 물을 때의 이동수단 — S15P21E201-184.
 *
 * <p>🔴 {@code trip.travel_modes} 의 아홉 갈래(WALK·BUS·SUBWAY·TAXI·PRIVATE_CAR·RENTAL_CAR·
 * BICYCLE·FERRY·OTHER)와 <b>같은 목록이 아니다.</b> 저쪽은 사용자가 "이번 여행에서 쓸 수단"
 * 으로 고르는 것이고, 여기는 <b>경로를 계산하는 방법</b>이다. 버스와 지하철은 계산 방법이
 * 같아서 하나로 묶이고, 택시·자가용·렌터카도 도로를 달리는 점에서 같다.
 *
 * <p>둘을 억지로 한 enum 으로 합치면 "렌터카 경로" 를 물었을 때 무엇을 계산해야 하는지가
 * 값 자체로는 안 드러나고, 바꾸는 쪽마다 자기 규칙을 갖게 된다.
 */
public enum TravelMode {

	/** 도로를 달린다 — 자가용·택시·렌터카가 전부 여기로 온다. */
	CAR,

	/** 버스·지하철. 🔴 아직 경로를 물어볼 업체가 정해지지 않았다(아래 javadoc 참고). */
	TRANSIT,

	/** 걷는다. */
	WALK
}
