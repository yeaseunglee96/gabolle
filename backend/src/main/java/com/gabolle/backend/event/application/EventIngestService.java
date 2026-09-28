package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.domain.Producer;

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
 * 이유는 {@link BehaviorConsent} 에 있다.
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

	/**
	 * 기기 시계가 서버보다 이만큼까지 빠른 것은 받아 준다 (S15P21E201-1730, 사용자 결정 2026-09-26).
	 *
	 * <p>앱은 보내는 순간의 기기 시각을 찍고, 실패하면 다시 보내지 않는다. 그래서 받은 시각과의 차이는
	 * 네트워크 지연(운영 실측 64~181ms)뿐이고, 기기 시계가 그보다 조금만 빨라도 그 기기의 이벤트는
	 * <b>매번</b> 버려졌다 — 전에는 허용 폭이 0 이었다. 이 PC 로컬 웹에서 15ms 차이로 거절됐고,
	 * 운영에서는 안드로이드 기기 하나의 이벤트가 한 건도 안 들어왔다.
	 *
	 * <p>이보다 더 빠르면 시계가 틀렸거나 조작된 값으로 보고 지금처럼 거절한다.
	 */
	public static final Duration MAX_CLIENT_CLOCK_AHEAD = Duration.ofMinutes(5);

	private final OutboxService outboxService;

	private final BehaviorConsent consent;

	private final Clock clock;

	/** 노출의 순위·이유·판을 채울 곳(S15P21E201-1689). 추천 쪽이 없는 슬라이스에서는 없다 — 그때는 앱이 보낸 그대로 적는다. */
	private ImpressionFacts impressionFacts;

	public EventIngestService(OutboxService outboxService, BehaviorConsent consent, Clock clock) {
		this.outboxService = outboxService;
		this.consent = consent;
		this.clock = clock;
	}

	@Autowired(required = false)
	public void setImpressionFacts(ImpressionFacts impressionFacts) {
		this.impressionFacts = impressionFacts;
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

		Map<String, Object> canonical = ClientPayloadKeys.canonical(payload);
		if (type == EventType.RECOMMENDATION_IMPRESSION) {
			canonical = withImpressionFacts(requestId, canonical);
		}
		return append(eventId, type, eventVersion, Producer.CLIENT, userId, tripId, requestId, occurredAt, canonical);
	}

	/**
	 * 노출에 순위 · 이유 코드 · 판이 비어 있으면 서버가 추천을 만들 때 적어 둔 것으로 채운다 (S15P21E201-1689). 앱은 요청 번호 ·
	 * 장소 번호 · 화면 이름만 보내면 된다. 앱이 보낸 값이 있으면 건드리지 않는다. 그 요청이 그 장소를 낸 기록이 없으면(모르는
	 * 요청 · 잘못 붙은 장소) 채우지 않고 그대로 적는다 — 읽는 쪽({@code recommendation_exposure})이 순위 없는 노출로 본다.
	 */
	private Map<String, Object> withImpressionFacts(UUID requestId, Map<String, Object> payload) {
		if (this.impressionFacts == null || requestId == null || payload == null) {
			return payload;
		}
		UUID placeId = uuidOrNull(payload.get("placeId"));
		if (placeId == null) {
			return payload;
		}
		Optional<ImpressionFacts.Facts> found = this.impressionFacts.lookup(requestId, placeId);
		if (found.isEmpty()) {
			return payload;
		}
		ImpressionFacts.Facts facts = found.get();
		Map<String, Object> filled = new LinkedHashMap<>(payload);
		putIfMissing(filled, "finalRank", facts.finalRank());
		putIfMissing(filled, "reasonCodes", facts.reasonCodes());
		putIfMissing(filled, "fallbackMode", facts.fallbackMode());
		putIfMissing(filled, "modelVersion", facts.modelVersion());
		putIfMissing(filled, "featureVersion", facts.featureVersion());
		putIfMissing(filled, "ontologyVersion", facts.ontologyVersion());
		putIfMissing(filled, "datasetVersion", facts.datasetVersion());
		putIfMissing(filled, "policyVersion", facts.policyVersion());
		return filled;
	}

	private static void putIfMissing(Map<String, Object> payload, String key, Object value) {
		if (value != null && payload.get(key) == null) {
			payload.put(key, value);
		}
	}

	private static UUID uuidOrNull(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value.toString());
		}
		catch (IllegalArgumentException notAUuid) {
			return null;
		}
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
	 * 이 사람의 행동을 지금 적어도 되는가. 규칙은 {@link BehaviorConsent} 에 있다 — 여기에
	 * 다시 쓰지 않는다.
	 *
	 * <p>🔴 <b>이것이 유일한 방어선이 아니다.</b> 진짜 방어선은 Outbox 입구인
	 * {@code OutboxService} 이고(S15P21E201-1096), 여기서 한 번 더 보는 것은 <b>형식 검사를
	 * 다 지난 뒤에</b> 물어서 {@link Outcome#NOT_COLLECTED} 를 이 API 의 답으로 돌려주기
	 * 위해서다. 두 자리가 같은 {@code BehaviorConsent} 를 부르므로 규칙이 갈라지지 않는다.
	 */
	private boolean collectsBehaviorOf(UUID userId) {
		return this.consent.collects(userId);
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
		if (occurredAt.isAfter(receivedAt.plus(MAX_CLIENT_CLOCK_AHEAD))) {
			// 허용 폭보다 더 미래다. 기기 시계가 크게 틀렸거나 조작된 값이다.
			throw new IllegalArgumentException("occurredAt(" + occurredAt + ") 이 수신 시각(" + receivedAt + ") 보다 "
					+ MAX_CLIENT_CLOCK_AHEAD.toMinutes() + "분 넘게 뒤다");
		}
		// 허용 폭 안에서 미래인 값은 받은 시각으로 맞춰 적는다. 발생이 수신보다 뒤일 수는 없으므로, 그 불변식은
		// 적힌 값에서 그대로 지켜진다 — 뒤에서 두 값을 빼 보는 쪽이 음수를 만나지 않는다.
		OffsetDateTime recordedOccurredAt = occurredAt.isAfter(receivedAt) ? receivedAt : occurredAt;

		UUID aggregateId = aggregateIdOf(type, userId, tripId, requestId);

		OutboxAppendCommand command = new OutboxAppendCommand(
				eventId,
				type.wireName(),
				eventVersion,
				type.aggregateType(),
				aggregateId,
				partitionKeyOf(userId, aggregateId),
				withoutEnvelopeFields(payload),
				recordedOccurredAt,
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

		// 입구가 한 번 더 본다. 여기서 이미 걸렀으므로 보통은 같은 답이지만, 「입구가 낸 판정을
		// 그대로 옮긴다」 로 적어 두면 이 앞의 검사를 나중에 지워도 답이 안 바뀐다.
		return switch (this.outboxService.appendReportingDuplicate(command).outcome()) {
			case STORED -> Outcome.STORED;
			case DUPLICATE -> Outcome.DUPLICATE;
			case NOT_COLLECTED -> Outcome.NOT_COLLECTED;
		};
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
