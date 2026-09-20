package com.gabolle.backend.event.application;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.gabolle.backend.event.domain.Producer;

/**
 * Outbox 에 이벤트 한 건을 적어 달라는 요청.
 *
 * <p>{@code eventId} 는 부르는 쪽이 만든다. 그래야 재시도할 때 같은 eventId 로 다시 부를 수
 * 있고, 두 번째 호출이 새 행을 만들지 않는다.
 *
 * @param partitionKey 브로커로 보낼 때 순서를 지켜야 하는 단위
 * @param payload envelope 밖의 본문. 개인정보 검사를 통과해야 저장된다
 * @param requestId {@code aggregateType} 이 {@code "recommendation"} 이면 반드시 {@code null} 이다 —
 *     그 이벤트의 요청 축은 이미 {@code aggregateId} 가 들고 있고, 같은 값을 두 칸에 넣으면
 *     나중에 둘이 어긋난다
 * @param userId 없을 수 있다(비로그인 이벤트)
 * @param tripId 없을 수 있다
 */
public record OutboxAppendCommand(
		UUID eventId,
		String eventType,
		int eventVersion,
		String aggregateType,
		UUID aggregateId,
		String partitionKey,
		Map<String, Object> payload,
		OffsetDateTime occurredAt,
		UUID requestId,
		UUID userId,
		UUID tripId,
		Producer producer) {

	/** {@code event_outbox.aggregate_type} 이 이 값이면 요청 축은 {@code aggregateId} 가 정본이다. */
	private static final String RECOMMENDATION_AGGREGATE_TYPE = "recommendation";

	public OutboxAppendCommand {
		if (eventId == null) {
			throw new IllegalArgumentException("eventId 는 필수다");
		}
		if (eventType == null || eventType.isBlank()) {
			throw new IllegalArgumentException("eventType 은 필수다");
		}
		if (eventVersion < 1) {
			throw new IllegalArgumentException("eventVersion 은 1 이상이어야 한다: " + eventVersion);
		}
		if (aggregateType == null || aggregateType.isBlank()) {
			throw new IllegalArgumentException("aggregateType 은 필수다");
		}
		if (aggregateId == null) {
			throw new IllegalArgumentException("aggregateId 는 필수다");
		}
		if (partitionKey == null || partitionKey.isBlank()) {
			throw new IllegalArgumentException("partitionKey 는 필수다");
		}
		if (occurredAt == null) {
			throw new IllegalArgumentException("occurredAt 은 필수다");
		}
		if (producer == null) {
			throw new IllegalArgumentException("producer(CLIENT/SERVER)는 필수다 (DR-13)");
		}
		// 추천 이벤트는 요청 축이 aggregateId 에 이미 있으므로 requestId 를 또 채우면 안 된다.
		// 반대로 "이 종류에 requestId 가 꼭 있어야 하는가" 는 종류마다 달라 호출자의 몫이다.
		if (RECOMMENDATION_AGGREGATE_TYPE.equals(aggregateType) && requestId != null) {
			throw new IllegalArgumentException(
					"추천 이벤트(aggregateType=recommendation)는 requestId 를 따로 채우지 않는다 — "
							+ "요청 축은 이미 aggregateId 다");
		}
		// LinkedHashMap 으로 복사하는 이유: Map.copyOf 는 null 값을 거부하는데, 이벤트 본문에는
		// trip_id 처럼 조건부로 비는 필드가 있다. "비었다" 를 키 자체를 지워서 표현하면
		// 나중에 "안 보냈다" 와 "없었다" 를 구분할 수 없다.
		payload = (payload == null)
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(payload));
	}

	/**
	 * requestId·userId·tripId 를 모르거나 필요 없는 호출자용. {@code producer} 는
	 * {@link Producer#SERVER} 로 고정한다 — 이 경로로 클라이언트가 들어올 일이 없다.
	 */
	public OutboxAppendCommand(UUID eventId, String eventType, int eventVersion, String aggregateType,
			UUID aggregateId, String partitionKey, Map<String, Object> payload, OffsetDateTime occurredAt) {
		this(eventId, eventType, eventVersion, aggregateType, aggregateId, partitionKey, payload, occurredAt,
				null, null, null, Producer.SERVER);
	}
}
