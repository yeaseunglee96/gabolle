package com.gabolle.backend.event.application;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 에 이벤트 한 건을 적어 달라는 요청.
 *
 * <p>{@code eventId} 는 <b>부르는 쪽</b>이 만든다. 그래야 같은 요청을 재시도할 때 같은
 * eventId 로 다시 부를 수 있고, 그때 두 번째 호출이 새 행을 만들지 않는다.
 *
 * @param eventId 이벤트 멱등 키. 서버가 만든 UUID
 * @param eventType 예: {@code recommendation_requested}
 * @param eventVersion 이벤트 스키마 버전. 1 부터
 * @param aggregateType 이 이벤트가 속한 대상 종류. 예: {@code recommendation}
 * @param aggregateId 그 대상의 ID. 예: request_id
 * @param partitionKey 나중에 브로커로 보낼 때 순서를 지켜야 하는 단위. 예: user_id 문자열
 * @param payload envelope 밖의 본문. 🔴 개인정보 검사를 통과해야 저장된다
 * @param occurredAt 실제 발생 시각
 */
public record OutboxAppendCommand(
		UUID eventId,
		String eventType,
		int eventVersion,
		String aggregateType,
		UUID aggregateId,
		String partitionKey,
		Map<String, Object> payload,
		OffsetDateTime occurredAt) {

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
		// LinkedHashMap 으로 복사하는 이유: Map.copyOf 는 null 값을 거부하는데, 이벤트 본문에는
		// trip_id 처럼 조건부로 비는 필드가 있다. "비었다" 를 키 자체를 지워서 표현하면
		// 나중에 "안 보냈다" 와 "없었다" 를 구분할 수 없다.
		payload = (payload == null)
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(payload));
	}
}
