package com.gabolle.backend.recommendation.domain;

/**
 * 이 Job 이 무엇을 하려던 것인가. GB-API-001 4.2 JobDto 의 {@code type} 이다.
 *
 * <p>값은 API 명세서 3.5 절의 Job 을 만드는 엔드포인트에서 그대로 왔다.
 */
public enum JobType {
	/** REC-01 — 여행 전체 일정 생성. 202 + jobId 로 비동기 처리한다. */
	ITINERARY_GENERATION,
	/** ITN-06 — 일정 항목 하나를 대체 후보로 교체. */
	ITEM_REPLACE,
	/** ITN-08 — 일정 항목 제거. */
	ITEM_REMOVE,
	/** ITN-07 — 일정 항목 순서 변경. */
	ITEM_ORDER_CHANGE,
	/** ITN-09 — 남은 일정 재계산. */
	ITINERARY_RECALCULATE,
	/** REC-04 — "지금 갈 곳". 🔴 이것만 <b>동기</b> 응답이다 (API-10). */
	NOW_RECOMMENDATION
}
