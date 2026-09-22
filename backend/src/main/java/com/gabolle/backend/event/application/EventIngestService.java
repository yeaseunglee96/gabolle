package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 이벤트 적재 — 응용 계층.
 *
 * <p>중계 서버가 꺼져 있어도 정상 응답해야 하므로 여기서 Kafka 를 부르지 않는다. 적기만 한다.
 * envelope 규칙·개인정보 검사·멱등은 {@link OutboxService} 한 군데에 있다.
 *
 * <p>{@code no-db} 프로필에는 이 빈이 없어 {@code /api/v1/events} 가 아예 없다.
 * 있는 척하면서 휘발성 저장소에 적는 것보다 없는 편이 낫다.
 *
 * <p>탈퇴 정책은 "계정은 삭제, 이벤트는 익명화" 다 — 지우면 과거 추천 평가를 재현할 수 없다.
 * 행동 기반 개인화를 끈 사람은 다르게 다룬다: 행동 관찰 이벤트를 아예 적지 않는다.
 * 이유는 {@link #collectsBehaviorOf(java.util.UUID)} 에 있다.
 *
 * <p>{@code event_outbox} 에는 {@code user_id} 외래키를 걸지 않는다. FK 가 있으면 CASCADE 로
 * 이벤트가 같이 지워지거나 RESTRICT 로 계정 삭제가 막힌다 — 둘 다 정책 위반이다.
 */
@Service
@Profile({ "db", "dev" })
public class EventIngestService {

	/**
	 * envelope 축으로 쓰는 실컬럼 이름. payload 안에 같은 키를 허용하면 컬럼과 JSON 값이
	 * 서로 달라질 수 있으므로 입구에서 거부한다.
	 */
	static final Set<String> ENVELOPE_KEYS = Set.of("request_id", "user_id", "trip_id", "producer");

	private final OutboxService outboxService;

	private final AppUserRepository users;

	private final Clock clock;

	public EventIngestService(OutboxService outboxService, AppUserRepository users, Clock clock) {
		this.outboxService = outboxService;
		this.users = users;
		this.clock = clock;
	}

	/** 이 이벤트가 어떻게 됐는가. 셋 다 오류가 아니고, 셋을 뭉치면 왜 안 쌓이는지 조사할 수 없다. */
	public enum Outcome {

		/** 새로 적혔다. */
		STORED,

		/** 이미 같은 {@code eventId} 가 있었다. 재전송이고, 오류가 아니다. */
		DUPLICATE,

		/** 일부러 안 적었다 — 이 사람이 행동 기반 개인화를 껐고 이 이벤트는 행동 관찰이다. */
		NOT_COLLECTED
	}

	/**
	 * 클라이언트가 보낸 이벤트를 적는다. 이미 받은 {@code eventId} 면 조용히 성공으로 응답한다.
	 *
	 * <p>payload 의 키는 {@link ClientPayloadKeys} 가 서버 어휘로 옮긴 뒤에 적힌다. 앱은
	 * {@code place_id} 로 보내는데 읽는 쪽({@code recommendation_exposure} 뷰)은
	 * {@code placeId} 로만 찾기 때문이다 (S15P21E201-1481). 앱을 고쳐도 옛 판을 쓰는 사람이
	 * 남으므로 받는 자리에서 옮긴다 — 왜 앱이 아니라 여기인지는 그 클래스에 적혀 있다.
	 */
	@Transactional
	public Outcome ingestFromClient(UUID eventId, EventType type, int eventVersion, UUID userId, UUID tripId,
			UUID requestId, OffsetDateTime occurredAt, Map<String, Object> payload) {

		return append(eventId, type, eventVersion, Producer.CLIENT, userId, tripId, requestId, occurredAt,
				ClientPayloadKeys.canonical(payload));
	}

	/**
	 * 서버 업무 로직이 이벤트를 적는다. 기본 전파(REQUIRED)라서 부르는 쪽이 이미 트랜잭션
	 * 안이면 그 트랜잭션에 합류한다 — 여기서 새 트랜잭션을 열면(REQUIRES_NEW) 업무 저장이
	 * 롤백돼도 이벤트만 남아 Outbox 패턴이 깨진다.
	 */
	@Transactional
	public void recordFromServer(UUID eventId, EventType type, int eventVersion, UUID userId, UUID tripId,
			UUID requestId, Map<String, Object> payload) {

		OffsetDateTime now = OffsetDateTime.now(this.clock);
		append(eventId, type, eventVersion, Producer.SERVER, userId, tripId, requestId, now, payload);
	}

	/**
	 * 이 사람의 행동을 지금 적어도 되는가.
	 *
	 * <p>탈퇴처럼 {@code user_id} 만 비우는 방법은 여기서 쓸 수 없다. 행동 이벤트는 대부분 축이
	 * 여행이라 {@code aggregate_id} 에 {@code trip_id} 가 들어 있고 그 여행에는 주인이 있다 —
	 * 비워도 여행을 거쳐 그 사람으로 되돌아갈 수 있다. 되돌릴 수 있는 가리기는 가린 것이
	 * 아니므로 아예 적지 않는다.
	 *
	 * <p>없는 사람과 모르는 사람을 다르게 다룬다. {@code userId == null} 이면 적는다 — 이미
	 * 익명이라 막아도 지켜지는 개인정보가 없고 집계만 사라진다. 사람은 있는데 계정을 못 찾으면
	 * 안 적는다 — 동의를 확인할 수 없는 상태이고, 실패는 조용한 수집이 아니라 빈 자리로
	 * 나타나야 한다.
	 *
	 * <p>public 인 것은 {@code OutboxService} 를 직접 부르는 경로가 이 클래스 밖에 있어서다.
	 * 그쪽이 규칙을 다시 쓰지 않고 이것을 부르게 한다. 임시 방편이고, 제대로 된 자리는
	 * 입구인 {@code OutboxService} 다.
	 */
	public boolean collectsBehaviorOf(UUID userId) {
		if (userId == null) {
			return true;
		}
		return this.users.findPersonalizationMode(userId)
				.filter(PersonalizationMode.BEHAVIOR_ENABLED::equals)
				.isPresent();
	}

	private Outcome append(UUID eventId, EventType type, int eventVersion, Producer producer, UUID userId, UUID tripId,
			UUID requestId, OffsetDateTime occurredAt, Map<String, Object> payload) {

		if (eventId == null) {
			throw new IllegalArgumentException("eventId 는 멱등 키다. 비울 수 없다");
		}
		if (!type.allowsProducer(producer)) {
			// 생산 책임이 뒤바뀌면 신뢰할 수 없는 값이 들어온다.
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

		UUID aggregateId = aggregateIdOf(type, userId, tripId, requestId);

		OutboxAppendCommand command = new OutboxAppendCommand(
				eventId,
				type.wireName(),
				eventVersion,
				type.aggregateType(),
				aggregateId,
				partitionKeyOf(userId, aggregateId),
				withoutEnvelopeFields(payload),
				occurredAt,
				requestIdColumnOf(type, requestId),
				userId,
				tripId,
				producer);

		// 순서가 중요하다 — 동의는 형식 검사를 전부 지난 뒤에 본다. 축 판정과 envelope 검사도
		// 형식 검사이고, 그 앞에서 끊으면 개인화를 끈 사람이 보낸 잘못된 이벤트가 202 를 받아
		// 앱의 계측 버그가 "그 사람만 안 쌓인다" 로 보인다. 명령을 만드는 것은 부작용이 없다.
		if (type.isBehaviorSignal() && !collectsBehaviorOf(userId)) {
			return Outcome.NOT_COLLECTED;
		}

		return this.outboxService.appendReportingDuplicate(command).created() ? Outcome.STORED : Outcome.DUPLICATE;
	}

	/**
	 * 이 이벤트가 붙는 대상의 ID.
	 *
	 * <p>축은 {@link EventType} 이 정한다. {@code aggregate_id} 컬럼이 {@code UUID NOT NULL} 이라
	 * 축이 가리키는 값이 비어 있으면 적을 수 없다 — 임의로 다른 값을 넣지 않고 그 자리에서
	 * 거부한다.
	 *
	 * <p>{@code requestId} 필수 검사가 여기 있는 것은 추천 축에서만 필수이기 때문이다.
	 * 다른 축에서는 있으면 {@code request_id} 칸으로 이어 붙이고 없으면 비운다 — 모든 이벤트에
	 * 요구하면 그 값을 앱에 알려주는 응답이 없어서 저장·제외·방문이 전부 튕긴다.
	 */
	private UUID aggregateIdOf(EventType type, UUID userId, UUID tripId, UUID requestId) {
		// 축이 null 인 경우를 switch 안에서 다루지 않는다 — Java 17 에서 case null 은 프리뷰
		// 기능이라 컴파일되지 않는다. 스위치에 들어가기 전에 거른다.
		if (!type.hasAggregateAxis()) {
			throw new IllegalStateException(type + " 의 aggregate 축이 아직 정해지지 않았다");
		}
		return switch (type.aggregateAxis()) {
			case RECOMMENDATION_REQUEST -> {
				if (requestId == null) {
					throw new IllegalArgumentException(
							type + " 은 추천 요청에 붙는 이벤트다. requestId 없이는 노출과 행동을 이을 수 없다 (API-07)");
				}
				yield requestId;
			}
			case TRIP -> {
				if (tripId == null) {
					throw new IllegalArgumentException(type + " 은 여행에 붙는 이벤트다. tripId 없이는 적을 수 없다");
				}
				yield tripId;
			}
			case USER -> {
				if (userId == null) {
					throw new IllegalArgumentException(type + " 은 사용자에 붙는 이벤트다. userId 없이는 적을 수 없다");
				}
				yield userId;
			}
		};
	}

	/**
	 * {@code event_outbox.request_id} 실컬럼에 넣을 값 — 축에 따라 다르다.
	 *
	 * <p>추천 이벤트는 요청 축이 이미 {@code aggregateId} 라 여기서는 {@code null} 을 보낸다.
	 * 또 채우면 같은 값이 두 칸에 있고 나중에 하나만 고쳐지면 어긋난다
	 * ({@link OutboxAppendCommand} 가 같은 규칙을 한 번 더 강제한다).
	 */
	private UUID requestIdColumnOf(EventType type, UUID requestId) {
		return type.aggregateAxis() == EventType.AggregateAxis.RECOMMENDATION_REQUEST ? null : requestId;
	}

	/**
	 * 브로커로 보낼 때 순서를 지켜야 하는 단위.
	 *
	 * <p>기본은 사용자다. 비로그인 이벤트는 사용자가 없으므로 aggregate 를 쓴다 — 고정
	 * 문자열을 쓰면 비로그인 이벤트 전부가 한 칸에 몰려 브로커를 켰을 때 병목이 된다.
	 */
	private String partitionKeyOf(UUID userId, UUID aggregateId) {
		return (userId != null ? userId : aggregateId).toString();
	}

	/** envelope 값이 JSON payload에 중복 저장되지 않도록 검사한다. */
	private Map<String, Object> withoutEnvelopeFields(Map<String, Object> payload) {
		if (payload != null) {
			for (String reserved : ENVELOPE_KEYS) {
				if (payload.containsKey(reserved)) {
					throw new IllegalArgumentException(
							"payload 에 envelope 키가 들어 있다: " + reserved + ". 이 값은 컬럼으로 받는다");
				}
			}
		}
		return payload;
	}
}
