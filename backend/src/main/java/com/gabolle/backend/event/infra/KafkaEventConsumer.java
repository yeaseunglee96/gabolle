package com.gabolle.backend.event.infra;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gabolle.backend.event.application.EventConsumptionService;
import com.gabolle.backend.event.application.EventConsumptionService.ConsumedEvent;
import com.gabolle.backend.event.config.KafkaEventProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 토픽에서 받아 <b>한 번만</b> 반영한다 (S15P21E201-561).
 *
 * <p>반영은 둘이다. <b>하나</b> — 장부({@code event_consumption})에 한 줄 적어 같은 이벤트를
 * 두 번 세지 않게 한다. <b>둘</b> — 장소가 실린 행동 이벤트면 취향 벡터를 증분으로 고친다
 * ({@code TasteAttributionService}, S15P21E201-1500). 둘은 <b>한 트랜잭션</b>이다
 * ({@link EventConsumptionService}, S15P21E201-1501).
 *
 * <p>예전에는 장부에 적는 것이 전부였다. 취향으로 옮기는 규칙이 아직 없었기 때문이다 —
 * <b>경로가 뚫렸다는 것과 무게를 정했다는 것은 다른 일이다.</b> 그 규칙이 S15P21E201-1482 로
 * 정해져서 이제 잇는다.
 *
 * <p>🔴 <b>멱등을 조회로 하지 않는다.</b> "이미 처리했나" 를 물어보고 아니면 처리하는 식은
 * 조회와 쓰기 사이의 틈에서 둘 다 통과한다. {@code INSERT … ON CONFLICT DO NOTHING} 을 보내고
 * <b>넣은 행 수</b>로 판정한다 — 그 판정은 데이터베이스가 하므로 틈이 없다.
 *
 * <p>🔴 예전에는 JPA {@code saveAndFlush} 가 기본키 위반을 던지기를 기대했는데, <b>그게 안
 * 일어났다.</b> 번호를 직접 넣는 엔티티라 Spring Data 가 {@code merge} 로 저장하고, merge 는
 * 이미 있는 행을 만나면 조용히 넘어간다. 중복을 잡는 {@code catch} 는 한 번도 안 도는 코드였고,
 * 재전송된 보기·방문이 취향에 두 번 더해졌다 (S15P21E201-1501).
 *
 * <p>처리에 실패하면 예외를 <b>그대로 던진다.</b> 여기서 삼키면 스프링 카프카가 "성공했다" 로
 * 보고 오프셋을 넘겨 버려 그 이벤트가 사라진다. 재시도와 DLQ 는
 * {@link KafkaConsumerErrorConfiguration} 이 건다.
 *
 * <p>🔴 <b>신뢰 경계 (2026-09-24 보안 감사, S15P21E201-1552).</b> {@link #HEADER_USER_ID} 는
 * <b>메시지 자체에 서명이 없다</b> — 이 리스너는 헤더에 적힌 값을 그대로 믿고 취향 벡터에
 * 반영한다. 지금 이게 안전한 이유는 <b>이 토픽에 쓸 수 있는 프로듀서가 이 백엔드 자기 자신
 * 하나뿐</b>이기 때문이다: HTTP 요청 → {@code Authentication} 통과(본문의 {@code userId} 를
 * 그대로 믿지 않고 인증 주체와 대조 — {@code EventIngestController.resolveSubject}) → DB
 * 아웃박스 행 → {@link com.gabolle.backend.event.application.OutboxRelayService} 가 릴레이 →
 * {@link KafkaEventPublisher} 가 발행. 즉 {@code user_id} 는 카프카에 닿기 전에 이미 인증을
 * 한 번 거친 값이고, 이 리스너는 그 전제 위에서만 안전하다.
 *
 * <p>🔴 <b>이 전제가 깨지는 조건 — 그날은 반드시 메시지 레벨 인증을 먼저 넣는다.</b>
 * (1) 이 브로커에 다른 서비스가 프로듀서로 붙어 같은 토픽에 쓰기 시작하는 순간, 또는
 * (2) 브로커 네트워크 격리가 느슨해져 제3자가 브로커에 직접 메시지를 넣을 수 있게 되는 순간,
 * 이 리스너는 위조된 {@code user_id} 로 남의 취향 벡터를 오염시키는 통로가 된다. 둘 중
 * 하나라도 계획한다면 이 클래스를 건드리기 전에 먼저 (a) 메시지 자체에 HMAC 서명을 얹거나
 * (b) 브로커 접근을 mTLS/ACL 로 프로듀서 신원까지 검증하도록 좁혀야 한다 — 지금은 둘 다 없다.
 */
@Component
@ConditionalOnProperty(prefix = "gabolle.event.kafka", name = "consumer-enabled", havingValue = "true")
public class KafkaEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

	static final String HEADER_EVENT_ID = "event_id";

	static final String HEADER_EVENT_TYPE = "event_type";

	/**
	 * 누구의 사건인가. 🔴 {@code record.key()} 로 대신하지 않는다 — 그건 <b>순서를 지키기 위한</b>
	 * 파티션 키이지 「누구인가」를 담기로 한 자리가 아니다. 지금은 우연히 같지만, 순서 단위를
	 * 바꾸는 날 취향이 엉뚱한 사람에게 조용히 붙는다 (S15P21E201-1500).
	 */
	static final String HEADER_USER_ID = "user_id";

	static final String HEADER_OCCURRED_AT = "occurred_at";

	/** payload 안에서 장소를 가리키는 칸. {@code SavedPlaceService} 가 이 이름으로 싣는다. */
	private static final String PAYLOAD_PLACE_ID = "placeId";

	private final EventConsumptionService consumption;

	private final KafkaEventProperties properties;

	private final Clock clock;

	private final ObjectMapper objectMapper;

	public KafkaEventConsumer(EventConsumptionService consumption, KafkaEventProperties properties, Clock clock,
			ObjectMapper objectMapper) {
		this.consumption = consumption;
		this.properties = properties;
		this.clock = clock;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = "${gabolle.event.kafka.topic:gabolle.events.v1}",
			groupId = "${gabolle.event.kafka.consumer-group:gabolle-event-consumer}")
	public void consume(ConsumerRecord<String, String> record) {
		UUID eventId = eventId(record);
		if (eventId == null) {
			// 🔴 헤더가 없으면 멱등을 걸 수 없다. 조용히 넘기면 같은 것이 몇 번이고 반영된다.
			//    던져서 DLQ 로 보낸다 — 사람이 보고 보낸 쪽을 고쳐야 하는 종류의 잘못이다.
			throw new IllegalStateException(
					"헤더 " + HEADER_EVENT_ID + " 가 없다. 멱등을 걸 수 없어 반영하지 않는다. "
							+ "topic=" + record.topic() + " partition=" + record.partition()
							+ " offset=" + record.offset());
		}

		String eventType = headerOrDefault(record, HEADER_EVENT_TYPE);
		ConsumedEvent event = new ConsumedEvent(eventId, eventType, record.key() == null ? "" : record.key(),
				this.properties.getConsumerGroup(), record.partition(), record.offset(), OffsetDateTime.now(this.clock),
				uuidHeader(record, HEADER_USER_ID), placeIdOf(record), occurredAt(record));

		// 예외는 여기서 삼키지 않는다 — 올라가야 카프카가 다시 보내고, 끝내 안 되면 DLQ 로 간다.
		// 중복은 예외가 아니라 false 로 온다.
		if (this.consumption.recordFirstTime(event)) {
			log.debug("event=EVENT_CONSUMED eventId={} type={}", eventId, eventType);
		}
		else {
			log.debug("event=EVENT_ALREADY_CONSUMED eventId={}", eventId);
		}
	}

	/**
	 * payload 에서 장소를 꺼낸다. 없거나 모양이 아니면 {@code null} 이다.
	 *
	 * <p>🔴 여기서 <b>던지지 않는다.</b> 취향과 무관한 이벤트에도 payload 는 있고, 그 안에
	 * {@code placeId} 가 없는 것이 정상이다. 던지면 멀쩡한 이벤트가 DLQ 로 간다.
	 */
	private UUID placeIdOf(ConsumerRecord<String, String> record) {
		if (record.value() == null || record.value().isBlank()) {
			return null;
		}
		try {
			JsonNode node = this.objectMapper.readTree(record.value()).get(PAYLOAD_PLACE_ID);
			return (node == null || !node.isTextual()) ? null : UUID.fromString(node.asString());
		}
		catch (RuntimeException notAPlaceEvent) {
			return null;
		}
	}

	/** 이벤트가 일어난 시각. 헤더가 없거나 모양이 아니면 지금으로 둔다 — 지어내는 것이 아니라 최선이다. */
	private OffsetDateTime occurredAt(ConsumerRecord<String, String> record) {
		String raw = header(record, HEADER_OCCURRED_AT);
		if (raw == null || raw.isBlank()) {
			return OffsetDateTime.now(this.clock);
		}
		try {
			return OffsetDateTime.parse(raw);
		}
		catch (RuntimeException malformed) {
			return OffsetDateTime.now(this.clock);
		}
	}

	private static UUID uuidHeader(ConsumerRecord<String, String> record, String name) {
		String raw = header(record, name);
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		}
		catch (IllegalArgumentException malformed) {
			return null;
		}
	}

	private static UUID eventId(ConsumerRecord<String, String> record) {
		String raw = header(record, HEADER_EVENT_ID);
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		}
		catch (IllegalArgumentException malformed) {
			// UUID 가 아닌 값이 왔다 — 없는 것과 같게 다룬다(위에서 DLQ 로 간다).
			return null;
		}
	}

	private static String headerOrDefault(ConsumerRecord<String, String> record, String key) {
		String value = header(record, key);
		return value == null ? "UNKNOWN" : value;
	}

	private static String header(ConsumerRecord<String, String> record, String key) {
		Header header = record.headers().lastHeader(key);
		return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
	}
}
