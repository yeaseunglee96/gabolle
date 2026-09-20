package com.gabolle.backend.recommendation.domain;

/**
 * Job 이 붙어 있는 대상. 여행에도 일정에도 붙지 않는 요청은 이 값을 비워 둔다 — 없는 값을
 * 지어내지 않는다.
 */
public enum ResourceType {
	TRIP,
	ITINERARY
}
