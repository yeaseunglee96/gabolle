package com.gabolle.backend.recommendation.domain;

/**
 * Job 이 붙어 있는 대상. GB-API-001 4.2 JobDto 의 {@code resourceType} 이다.
 *
 * <p>🔴 명세서 5장은 이 enum 의 값을 열거하지 않는다. 4.2 예시에 나온 {@code "TRIP"} 과,
 * 일정 편집 Job 들이 일정에 붙는다는 사실에서 둘만 뒀다. <b>여행에도 일정에도 붙지 않는
 * 요청은 이 값을 비워 둔다</b> — 없는 값을 지어내는 것보다 비어 있는 편이 낫다.
 */
public enum ResourceType {
	TRIP,
	ITINERARY
}
