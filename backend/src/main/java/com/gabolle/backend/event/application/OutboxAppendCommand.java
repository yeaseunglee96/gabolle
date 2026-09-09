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
 * @param requestId {@code event_outbox.request_id} 실컬럼(S15P21E201-352-event-outbox-join-axes).
 *     🔴 {@code aggregateType} 이 {@code "recommendation"} 이면 반드시 {@code null} 이다 —
 *     그 이벤트의 요청 축은 이미 {@code aggregateId} 가 들고 있다. 같은 값을 두 칸에
 *     넣으면 나중에 둘이 어긋날 수 있다(2026-09-03 결정)
 * @param userId {@code event_outbox.user_id} 실컬럼. 없을 수 있다(비로그인 이벤트)
 * @param tripId {@code event_outbox.trip_id} 실컬럼. 없을 수 있다
 * @param producer 이 이벤트를 만든 쪽(DR-13). 항상 있어야 한다 — 클라이언트가 보낸 값과
 *     서버가 만든 값을 구분하지 못하면 신뢰 경계가 무너진다
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
		// 🔴 2026-09-03 회귀 수정(고지혁 님 실측) — Outbox 통합 전에는 OutboxEvent 생성자가
		// requestId 없는 이벤트를 거부했다("requestId 가 없으면 노출과 행동을 이을 수 없다,
		// API-07"). 통합 뒤 그 검사가 없어졌는데, 유일한 진짜 입구인 이 커맨드에도 없었다.
		//
		// 🔴 여기서 되살리는 것은 그 검사 전체가 아니라, 이 계층에서 <b>일반적으로 판정
		// 가능한 하나</b>뿐이다 — 추천 이벤트는 요청 축이 aggregateId 에 이미 있으므로
		// requestId 를 또 채우면 안 된다. "이 이벤트 종류는 requestId 가 꼭 있어야 하는가" 는
		// 이벤트 종류마다 다르다(trip_created 는 추천 요청 없이도 난다 — 여기서 그것까지
		// 강제하면 정당한 이벤트를 거부한다). 그 판단은 이벤트 종류를 아는 호출자
		// (EventIngestService)의 몫으로 남긴다.
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
	 * 하위 호환 생성자 — requestId·userId·tripId 를 모르거나 필요 없는 호출자용
	 * (예: {@code RecommendationService} — 추천 이벤트는 요청 축이 {@code aggregateId} 뿐이다).
	 *
	 * <p>{@code producer} 는 {@link Producer#SERVER} 로 고정한다. 이 생성자를 쓰는 기존
	 * 호출부가 전부 서버 파이프라인이었다 — 클라이언트가 이 경로로 들어올 일이 없다.
	 */
	public OutboxAppendCommand(UUID eventId, String eventType, int eventVersion, String aggregateType,
			UUID aggregateId, String partitionKey, Map<String, Object> payload, OffsetDateTime occurredAt) {
		this(eventId, eventType, eventVersion, aggregateType, aggregateId, partitionKey, payload, occurredAt,
				null, null, null, Producer.SERVER);
	}
}
