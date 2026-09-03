package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;

/**
 * 이벤트 적재 — 응용 계층 (S15P21E201-352).
 *
 * <p>🔴 이 서비스의 완료 기준은 <b>"중계 서버가 꺼져 있어도 정상 응답한다"</b> 다.
 * 그러려면 여기서 Kafka 를 부르지 않아야 한다. 적기만 하고 끝낸다.
 *
 * <h2>🔴 2026-09-03 — 이 클래스가 메모리에 적고 있었다</h2>
 * 같은 {@code event} 패키지 안에 <b>이름이 같은 리포지토리 인터페이스가 둘</b> 있었다.
 * <ul>
 *   <li>{@code event.domain.EventOutboxRepository} (S15P21E201-352) — 구현이 메모리 하나뿐</li>
 *   <li>{@code event.repository.EventOutboxRepository} (S15P21E201-543) — JPA, DB 로 감</li>
 * </ul>
 * 이 서비스가 앞의 것을 물고 있어서 <b>POST /api/v1/events 로 받은 이벤트가 DB 가 아니라
 * 메모리에 쌓였고, 서버를 끄면 사라졌다.</b> 표는 멀쩡해 보이는데 안이 비는 종류의 고장이라
 * 아무도 몰랐다 — 고지혁 님이 S15P21E201-554 표를 만들다 실측으로 찾았다.
 *
 * <p>메모리 구현을 지우고 {@link OutboxService} 하나로 합쳤다. 그래야 개인정보 검사
 * ({@code SensitivePayloadGuard}) 와 envelope 규칙과 멱등 처리가 한 군데에만 있다.
 *
 * <h2>🔴 no-db 프로필에는 이 빈이 없다</h2>
 * {@code application.properties} 가 정한 팀 규칙 그대로다 — "DB 를 쓰는 추천·Outbox 빈은
 * 인증과 같은 방식으로 {@code @Profile({"db","dev"})} 가 가른다".
 * DB 없이 띄우면 {@code /api/v1/events} 는 <b>아예 없다.</b> 있는 척하면서 메모리에 적는 것보다
 * 없는 편이 낫다 — 있는 척하는 쪽이 방금 그 고장이었다.
 *
 * <h2>🔴 아직 못 하는 것 — 탈퇴 익명화 (NFR-08)</h2>
 * 탈퇴 정책은 "계정은 삭제, 이벤트는 익명화" 다. 지우면 과거 추천 평가를 재현할 수 없다.
 * 익명화는 {@code UPDATE event_outbox SET user_id = NULL WHERE user_id = ?} 한 줄이어야 하는데,
 * <b>{@code user_id} 가 아직 컬럼이 아니라 JSONB 안에 있어서 그 한 줄을 쓸 수 없다.</b>
 * 메모리 구현에 있던 {@code anonymizeUser} 는 <b>어느 탈퇴 흐름에도 연결돼 있지 않았고</b>
 * DB 로 옮길 수 없어 함께 지웠다. 컬럼이 생기면 여기 다시 만든다.
 *
 * <p>🔴 이 표에는 {@code user_id} 외래키를 걸지 않는다. FK 가 있으면 CASCADE 로 이벤트가 같이
 * 지워지거나 RESTRICT 로 계정 삭제가 막힌다 — 둘 다 정책 위반이다.
 */
@Service
@Profile({ "db", "dev" })
public class EventIngestService {

	/**
	 * 🔴 envelope 축인데 {@code event_outbox} 에 <b>아직 컬럼이 없어서</b> payload 로 넣는 것들.
	 *
	 * <p>{@code request_id} 가 실컬럼이 아니면 S15P21E201-542 체크리스트의 "후보 → 노출을
	 * {@code request_id + place_id} 로 조인하는 검증 쿼리" 를 쓸 수 없다. JSONB 에서 뽑아
	 * 조인하면 타입이 없고 색인도 못 탄다.
	 *
	 * <p>{@code ALTER TABLE} 은 고지혁 님(S15P21E201-554) 자리다. 컬럼이 생기면
	 * <b>이 상수를 지우고 {@link OutboxAppendCommand} 에 칸으로 옮긴다.</b>
	 */
	static final List<String> ENVELOPE_KEYS_STILL_IN_PAYLOAD = List.of("request_id", "user_id", "trip_id", "producer");

	private final OutboxService outboxService;

	private final Clock clock;

	public EventIngestService(OutboxService outboxService, Clock clock) {
		this.outboxService = outboxService;
		this.clock = clock;
	}

	/**
	 * 클라이언트가 보낸 이벤트를 적는다.
	 *
	 * <p>🔴 이미 받은 {@code eventId} 면 <b>조용히 성공으로 응답한다.</b>
	 * 재전송은 오류가 아니다 — 앱이 400 을 받으면 사용자에게 오류를 띄운다.
	 *
	 * @return 새로 적혔으면 true, 이미 있었으면 false (둘 다 성공 응답)
	 */
	@Transactional
	public boolean ingestFromClient(UUID eventId, EventType type, int eventVersion, UUID userId, UUID tripId,
			UUID requestId, OffsetDateTime occurredAt, Map<String, Object> payload) {

		return append(eventId, type, eventVersion, Producer.CLIENT, userId, tripId, requestId, occurredAt, payload);
	}

	/**
	 * 서버 비즈니스 로직이 이벤트를 적는다 — <b>Outbox 패턴의 핵심</b> (DR-05).
	 *
	 * <p>🔴 {@code @Transactional(propagation = REQUIRED)} 가 기본값이므로, 부르는 쪽
	 * (예: 좋아요 저장)이 이미 트랜잭션 안이면 <b>그 트랜잭션에 합류한다.</b> 그래서
	 * "좋아요는 저장됐는데 이벤트는 없는" 상태가 생기지 않는다.
	 *
	 * <p>🔴 <b>여기서 새 트랜잭션을 열면(REQUIRES_NEW) Outbox 패턴이 깨진다.</b>
	 * 비즈니스 저장이 롤백돼도 이벤트만 남는다.
	 */
	@Transactional
	public void recordFromServer(UUID eventId, EventType type, int eventVersion, UUID userId, UUID tripId,
			UUID requestId, Map<String, Object> payload) {

		OffsetDateTime now = OffsetDateTime.now(this.clock);
		append(eventId, type, eventVersion, Producer.SERVER, userId, tripId, requestId, now, payload);
	}

	private boolean append(UUID eventId, EventType type, int eventVersion, Producer producer, UUID userId, UUID tripId,
			UUID requestId, OffsetDateTime occurredAt, Map<String, Object> payload) {

		if (eventId == null) {
			throw new IllegalArgumentException("eventId 는 멱등 키다. 비울 수 없다");
		}
		if (requestId == null) {
			// API-07 — requestId 로 연결되지 않으면 분석용 정상 데이터로 승인하지 않는다.
			throw new IllegalArgumentException("requestId 가 없으면 노출과 행동을 이을 수 없다 (API-07): " + type);
		}
		if (!type.allowsProducer(producer)) {
			// DR-13 — 생산 책임이 뒤바뀌면 신뢰할 수 없는 값이 들어온다.
			throw new IllegalArgumentException(
					type + " 은 " + type.expectedProducer() + " 가 만들어야 한다 (받은 값: " + producer + ")");
		}
		if (occurredAt == null) {
			throw new IllegalArgumentException("occurredAt 은 필수다: " + type);
		}

		OffsetDateTime receivedAt = OffsetDateTime.now(this.clock);
		if (occurredAt.isAfter(receivedAt)) {
			// 발생이 수신보다 뒤일 수는 없다. 기기 시계가 틀렸거나 조작된 값이다.
			throw new IllegalArgumentException("occurredAt(" + occurredAt + ") 이 수신 시각(" + receivedAt + ") 보다 뒤다");
		}

		UUID aggregateId = aggregateIdOf(type, tripId, requestId);

		OutboxAppendCommand command = new OutboxAppendCommand(
				eventId,
				type.wireName(),
				eventVersion,
				type.aggregateType(),
				aggregateId,
				partitionKeyOf(userId, aggregateId),
				withEnvelopeFields(payload, requestId, userId, tripId, producer),
				occurredAt);

		return this.outboxService.appendReportingDuplicate(command).created();
	}

	/**
	 * 이 이벤트가 붙는 대상의 ID.
	 *
	 * <p>축은 {@link EventType} 이 정한다 — 추천 요청이면 {@code request_id}, 여행이면
	 * {@code trip_id}. {@code aggregate_id} 컬럼이 {@code UUID NOT NULL} 이라 <b>축이 가리키는
	 * 값이 비어 있으면 적을 수 없다.</b> 임의로 다른 값을 넣지 않고 그 자리에서 거부한다.
	 */
	private UUID aggregateIdOf(EventType type, UUID tripId, UUID requestId) {
		// 🔴 축이 null 인 경우를 switch 안에서 다루지 않는다 — Java 17 에서 case null 은 아직
		//    프리뷰 기능이라 컴파일되지 않는다. 스위치에 들어가기 전에 거른다.
		if (!type.hasAggregateAxis()) {
			throw new IllegalStateException(type + " 의 aggregate 축이 아직 정해지지 않았다");
		}
		return switch (type.aggregateAxis()) {
			case RECOMMENDATION_REQUEST -> requestId;
			case TRIP -> {
				if (tripId == null) {
					throw new IllegalArgumentException(type + " 은 여행에 붙는 이벤트다. tripId 없이는 적을 수 없다");
				}
				yield tripId;
			}
		};
	}

	/**
	 * 브로커로 보낼 때 순서를 지켜야 하는 단위.
	 *
	 * <p>기본은 사용자다 — 한 사람의 행동은 순서대로 읽혀야 한다. 🔴 비로그인 이벤트는
	 * 사용자가 없으므로 <b>aggregate 를 순서 단위로 쓴다.</b> 고정 문자열("anonymous")을 쓰면
	 * 비로그인 이벤트 전부가 한 칸에 몰려서, 나중에 브로커를 켰을 때 그 칸 하나가 병목이 된다.
	 */
	private String partitionKeyOf(UUID userId, UUID aggregateId) {
		return (userId != null ? userId : aggregateId).toString();
	}

	/**
	 * envelope 축을 payload 에 얹는다 — 🔴 <b>컬럼이 생길 때까지의 임시 자리다.</b>
	 *
	 * <p>부르는 쪽 payload 에 같은 키가 이미 있으면 <b>덮어쓰지 않고 거부한다.</b> 조용히
	 * 덮어쓰면 두 값 중 어느 것이 맞는지 나중에 알 수 없고, 그 행은 멀쩡해 보인다.
	 */
	private Map<String, Object> withEnvelopeFields(Map<String, Object> payload, UUID requestId, UUID userId,
			UUID tripId, Producer producer) {

		Map<String, Object> merged = new LinkedHashMap<>();
		if (payload != null) {
			for (String reserved : ENVELOPE_KEYS_STILL_IN_PAYLOAD) {
				if (payload.containsKey(reserved)) {
					throw new IllegalArgumentException(
							"payload 에 envelope 키가 들어 있다: " + reserved + ". 이 값은 칸으로 받는다");
				}
			}
			merged.putAll(payload);
		}

		// 🔴 null 이어도 키를 지우지 않는다. 키가 없으면 "안 보냈다" 와 "없었다" 를 구분할 수 없다.
		merged.put("request_id", requestId.toString());
		merged.put("user_id", userId == null ? null : userId.toString());
		merged.put("trip_id", tripId == null ? null : tripId.toString());
		merged.put("producer", producer.name());
		return merged;
	}
}
