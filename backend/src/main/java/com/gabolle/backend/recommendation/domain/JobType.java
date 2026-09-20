package com.gabolle.backend.recommendation.domain;

/** 이 Job 이 무엇을 하려던 것인가. */
public enum JobType {
	/** 여행 전체 일정 생성. 202 + jobId 로 비동기 처리한다. */
	ITINERARY_GENERATION,
	/** 일정 항목 하나를 대체 후보로 교체. */
	ITEM_REPLACE,
	/** 일정 항목 제거. */
	ITEM_REMOVE,
	/** 일정 항목 순서 변경. */
	ITEM_ORDER_CHANGE,
	/** 남은 일정 재계산. */
	ITINERARY_RECALCULATE,
	/** "지금 갈 곳". 이것만 동기 응답이다. */
	NOW_RECOMMENDATION
}
