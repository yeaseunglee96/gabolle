package com.gabolle.backend.event.presentation.dto;

import com.gabolle.backend.event.domain.EventType;

/**
 * 이벤트 사전 한 줄 — {@code GET /api/v1/events/catalog} 응답. 응답 필드가 팀 명세에 확정돼
 * 있지 않아 {@link EventType} 이 이미 아는 것만 내보낸다.
 */
public record EventCatalogEntry(

		/** 소문자 이름. 예: {@code recommendation_impression} */
		String eventType,

		/** 이 종류를 만드는 것이 맞는 쪽. 보낼 수 있는 쪽과 다를 수 있다 */
		String producer,

		/**
		 * 이 종류를 실제로 보내도 되는 쪽 전부. {@link #producer} 와 다를 수 있으므로 계측하는
		 * 쪽은 이쪽을 본다 — {@code producer} 만 보면 보낼 수 있는 이벤트를 안 보내게 된다.
		 */
		java.util.List<String> acceptedProducers,

		boolean requiredForM1,

		/** NONE · RECOMMENDATION · BEST_EFFORT. */
		String versionRequirement,

		/**
		 * {@code event_outbox.aggregate_type} 에 들어가는 값. 축이 아직 안 정해진 종류는
		 * {@code null} 이다 — 기본값을 지어내지 않고 {@link #hasAggregateAxis} 로 구분한다.
		 */
		String aggregateType,

		boolean hasAggregateAxis) {

	public static EventCatalogEntry from(EventType type) {
		return new EventCatalogEntry(
				type.wireName(),
				type.expectedProducer().name(),
				type.acceptedProducers().stream()
						.map(Enum::name)
						.sorted()
						.toList(),
				type.requiredForM1(),
				type.versionRequirement().name(),
				type.hasAggregateAxis() ? type.aggregateType() : null,
				type.hasAggregateAxis());
	}
}
