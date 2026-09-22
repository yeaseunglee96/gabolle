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

/**
 * 토픽에서 받아 <b>한 번만</b> 반영한다 (S15P21E201-561).
 *
 * <p>지금 "반영" 은 장부에 한 줄 적는 것이 전부다. 취향 무게로 바꾸는 진짜 정제는
 * S15P21E201-1061 이 규칙을 정한 뒤의 일이다 — 규칙 없이 숫자를 만들면 좋아요 한 번의 무게를
 * 지어내게 된다. <b>경로가 뚫렸다는 것과 무게를 정했다는 것은 다른 일이다.</b>
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

	private final EventConsumptionRepository repository;

	private final KafkaEventProperties properties;

	private final Clock clock;

	public KafkaEventConsumer(EventConsumptionRepository repository, KafkaEventProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
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
