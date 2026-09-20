package com.gabolle.backend.route.domain;

/**
 * 경로를 물을 때의 이동수단. trip.travel_modes 의 아홉 갈래와 다른 목록이다 — 저쪽은
 * 사용자가 고르는 수단이고 여기는 경로를 계산하는 방법이라, 계산이 같은 것끼리 묶인다.
 */
public enum TravelMode {

	/** 도로를 달린다 — 자가용·택시·렌터카가 전부 여기로 온다. */
	CAR,

	/** 버스·지하철. */
	TRANSIT,

	/** 걷는다. */
	WALK
}
