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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gabolle.backend.event.config.KafkaEventProperties;
import com.gabolle.backend.event.domain.EventConsumption;
import com.gabolle.backend.event.repository.EventConsumptionRepository;
import com.gabolle.backend.preference.application.TasteAttributionService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 토픽에서 받아 <b>한 번만</b> 반영한다 (S15P21E201-561).
 *
 * <p>반영은 둘이다. <b>하나</b> — 장부({@code event_consumption})에 한 줄 적어 같은 이벤트를
 * 두 번 세지 않게 한다. <b>둘</b> — 장소가 실린 행동 이벤트면 취향 벡터를 증분으로 고친다
 * ({@link TasteAttributionService}, S15P21E201-1500).
 *
 * <p>예전에는 장부에 적는 것이 전부였다. 취향으로 옮기는 규칙이 아직 없었기 때문이다 —
 * <b>경로가 뚫렸다는 것과 무게를 정했다는 것은 다른 일이다.</b> 그 규칙이 S15P21E201-1482 로
 * 정해져서 이제 잇는다.
 *
 * <p>🔴 <b>멱등을 조회로 하지 않는다.</b> "이미 처리했나" 를 물어보고 아니면 처리하는 식은
 * 조회와 쓰기 사이의 틈에서 둘 다 통과한다. 그냥 넣어 보고 <b>기본키가 거부하면</b> 이미
 * 반영된 것으로 읽는다. 그 판정은 데이터베이스가 하므로 틈이 없다.
 *
 * <p>처리에 실패하면 예외를 <b>그대로 던진다.</b> 여기서 삼키면 스프링 카프카가 "성공했다" 로
 * 보고 오프셋을 넘겨 버려 그 이벤트가 사라진다. 재시도와 DLQ 는
 * {@link KafkaConsumerErrorConfiguration} 이 건다.
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

	private final EventConsumptionRepository repository;

	private final KafkaEventProperties properties;

	private final Clock clock;

	private final TasteAttributionService attribution;

	private final ObjectMapper objectMapper;

	public KafkaEventConsumer(EventConsumptionRepository repository, KafkaEventProperties properties, Clock clock,
			TasteAttributionService attribution, ObjectMapper objectMapper) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
		this.attribution = attribution;
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

		EventConsumption consumption = new EventConsumption(eventId, headerOrDefault(record, HEADER_EVENT_TYPE),
				record.key() == null ? "" : record.key(), this.properties.getConsumerGroup(), record.partition(),
				record.offset(), OffsetDateTime.now(this.clock));

		try {
			this.repository.saveAndFlush(consumption);
			log.debug("event=EVENT_CONSUMED eventId={} type={}", eventId, consumption.getEventType());
		}
		catch (DataIntegrityViolationException duplicate) {
			// 기본키가 막았다 = 이미 반영했다. 이것은 오류가 아니라 **설계대로 된 것**이다.
			// 릴레이가 "적어도 한 번" 을 보장하므로 여기 오는 것은 정상 경로에 속한다.
			log.debug("event=EVENT_ALREADY_CONSUMED eventId={}", eventId);
			return;
		}

		applyToTaste(record);
	}

	/**
	 * 장부에 처음 적힌 이벤트만 취향에 반영한다 (S15P21E201-1500).
	 *
	 * <p>🔴 <b>장부에 적는 것과 이 반영은 같은 트랜잭션이 아니다.</b> 사이에서 죽으면 그 이벤트는
	 * 「반영했다」고 적힌 채 반영이 안 된다. 지금은 배치가 계속 돌면서 전 이력으로 다시 접으므로
	 * 다음 접기에서 메워진다 — 그래서 지금 단계에서는 잃는 것이 없다.
	 *
	 * <p>배치를 걷어내는 S15P21E201-1501 에서는 이 창이 <b>영구 손실</b>이 된다. 그때 둘을 한
	 * 트랜잭션으로 묶어야 한다. 묶는 방법도 정해져 있다 — 반영을 먼저 하고 장부를 나중에 적으면,
	 * 중복일 때 기본키가 거부하면서 <b>반영까지 함께 되돌아간다.</b> 다만 그러려면 이 메서드
	 * 바깥에 트랜잭션 경계가 하나 필요해서 이번 MR 에는 안 넣었다.
	 *
	 * <p>취향과 무관한 이벤트는 조용히 넘어간다. 여기 오는 것 대부분이 그렇다.
	 */
	private void applyToTaste(ConsumerRecord<String, String> record) {
		String eventType = header(record, HEADER_EVENT_TYPE);
		if (eventType == null) {
			return;
		}
		UUID userId = uuidHeader(record, HEADER_USER_ID);
		UUID placeId = placeIdOf(record);
		if (userId == null || placeId == null) {
			return;
		}
		this.attribution.apply(userId, eventType, placeId, occurredAt(record));
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
