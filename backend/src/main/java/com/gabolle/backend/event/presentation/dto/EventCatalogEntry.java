package com.gabolle.backend.event.presentation.dto;

import com.gabolle.backend.event.domain.EventType;

/**
 * 이벤트 사전 한 줄 — {@code GET /api/v1/events/catalog} 응답 (S15P21E201-352 작업 내용 4).
 *
 * <p>🔴 이 모양은 팀 API 명세에 아직 확정돼 있지 않다. 참고 문서
 * (LOCAL_ROUTE 기획서 v4 7.5)는 <b>이 엔드포인트가 존재해야 한다</b>는 것만 정하고
 * 응답 필드는 정하지 않았다. 여기서는 {@link EventType} 이 이미 아는 것만 그대로
 * 내보낸다 — 새 정보를 지어내지 않는다.
 */
public record EventCatalogEntry(

		/** 소문자 이름. 예: {@code recommendation_impression} */
		String eventType,

		/** 이 종류를 만들어야 하는 쪽 (DR-13). */
		String producer,

		/** S15P21E201-542 8.1 이 요구하는 M1 필수 이벤트인가. */
		boolean requiredForM1,

		/** NONE · RECOMMENDATION · BEST_EFFORT. */
		String versionRequirement,

		/**
		 * {@code event_outbox.aggregate_type} 에 들어가는 값.
		 *
		 * <p>🔴 축이 아직 안 정해진 종류는 {@code null} 이다 — 임의 기본값을 지어내지 않는다.
		 * {@link #hasAggregateAxis} 로 그 사실 자체를 구분한다.
		 */
		String aggregateType,

		boolean hasAggregateAxis) {

	public static EventCatalogEntry from(EventType type) {
		return new EventCatalogEntry(
				type.wireName(),
				type.expectedProducer().name(),
				type.requiredForM1(),
				type.versionRequirement().name(),
				type.hasAggregateAxis() ? type.aggregateType() : null,
				type.hasAggregateAxis());
	}
}
