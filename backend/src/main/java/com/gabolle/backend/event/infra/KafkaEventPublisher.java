package com.gabolle.backend.event.infra;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.config.KafkaEventProperties;
import com.gabolle.backend.event.domain.EventOutbox;

/**
 * 아웃박스 한 건을 카프카로 내보낸다 (S15P21E201-561).
 *
 * <p>🔴 <b>메시지 키는 {@code partitionKey} 다. {@code eventId} 가 아니다.</b>
 * {@link EventPublisherPort#publish} 의 옛 주석은 {@code eventId} 를 키로 쓰라고 적고 있었는데,
 * 그렇게 하면 키가 건마다 달라져 <b>한 여행·한 사용자의 이벤트가 파티션에 흩어진다.</b> 카프카는
 * 파티션 안에서만 순서를 지키므로, 그 순간 릴레이가 {@code ORDER BY seq} 와 "실패하면 멈춘다"
 * 로 어렵게 지켜 온 순서가 소비자 쪽에서 무너진다. {@code partition_key} 칼럼의 뜻이
 * {@code OutboxAppendCommand} 에 그대로 적혀 있다 — <i>"브로커로 보낼 때 순서를 지켜야 하는 단위"</i>.
 *
 * <p>중복 제거는 키가 아니라 <b>헤더 {@code event_id}</b> 로 한다. 릴레이는 "정확히 한 번" 이
 * 아니라 "적어도 한 번" 을 보장하므로(보낸 뒤 적기 전에 죽는 구간이 있다) 받는 쪽이 그 값으로
 * 같은 것을 두 번 처리하지 않게 걸러야 한다. 순서(키)와 멱등(헤더)은 다른 일이고, 한 칸에
 * 둘을 태우면 둘 다 잃는다.
 *
 * <p>🔴 <b>보내기는 동기다.</b> {@code KafkaTemplate.send} 는 비동기라 그대로 두면 브로커가
 * 받았는지 모르는 채로 릴레이가 {@code published_at} 을 찍는다. 그러면 브로커가 죽어 있어도
 * 표에는 "보냈다" 로 남고 <b>그 이벤트는 영영 사라진다.</b> 그래서 결과를 기다렸다가, 실패는
 * {@link EventPublisherPort.EventPublishException} 으로 바꿔 릴레이가 재시도 대상으로 남기게 한다.
 */
@Component
@ConditionalOnProperty(prefix = "gabolle.event.kafka", name = "enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisherPort {

	/**
	 * 한 건 보내고 기다리는 상한.
	 *
	 * <p>릴레이는 배치로 도는 백그라운드 작업이라 사람을 기다리게 하지 않는다. 그래도 상한을
	 * 두는 이유는, 브로커가 "죽지는 않았는데 응답도 안 하는" 상태일 때 워커 스레드가 영원히
	 * 붙잡히면 다음 차례가 영영 안 오기 때문이다.
	 */
	private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

	private static final String HEADER_EVENT_ID = "event_id";

	private static final String HEADER_EVENT_TYPE = "event_type";

	private static final String HEADER_EVENT_VERSION = "event_version";

	private static final String HEADER_OCCURRED_AT = "occurred_at";

	/**
	 * 누구의 사건인가 (S15P21E201-1500).
	 *
	 * <p>🔴 <b>키로 대신하지 않는다.</b> 파티션 키가 지금은 사용자이지만
	 * ({@code EventIngestService.partitionKeyOf}) 그건 <b>순서를 지키기 위한 것</b>이지
	 * 「누구인가」를 담기로 한 자리가 아니다. 비로그인 이벤트에서는 aggregate 가 들어가고,
	 * 나중에 순서 단위를 바꾸면 키의 뜻도 바뀐다. 그때 취향이 <b>엉뚱한 사람에게 조용히</b>
	 * 붙는다.
	 *
	 * <p>사용자가 없는 이벤트도 있으므로 값이 있을 때만 싣는다 — 빈 문자열을 넣으면 받는 쪽이
	 * 「모른다」와 「없다」를 구분할 수 없다.
	 */
	private static final String HEADER_USER_ID = "user_id";

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final KafkaEventProperties properties;

	public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, KafkaEventProperties properties) {
		this.kafkaTemplate = kafkaTemplate;
		this.properties = properties;
	}

	@Override
	public void publish(EventOutbox event) {
		ProducerRecord<String, String> record = new ProducerRecord<>(this.properties.getTopic(),
				event.getPartitionKey(), event.getPayload());
		addHeader(record, HEADER_EVENT_ID, event.getEventId().toString());
		addHeader(record, HEADER_EVENT_TYPE, event.getEventType());
		addHeader(record, HEADER_EVENT_VERSION, Integer.toString(event.getEventVersion()));
		addHeader(record, HEADER_OCCURRED_AT, event.getOccurredAt().toString());
		if (event.getUserId() != null) {
			addHeader(record, HEADER_USER_ID, event.getUserId().toString());
		}

		try {
			this.kafkaTemplate.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException interrupted) {
			// 🔴 인터럽트 표시를 되살린다. 삼키면 이 스레드를 멈추려는 종료 절차가 안 먹는다.
			Thread.currentThread().interrupt();
			throw new EventPublishException("보내는 중에 중단됐다: " + event.getEventId(), interrupted);
		}
		catch (ExecutionException | TimeoutException | RuntimeException failure) {
			throw new EventPublishException("카프카 발행 실패: " + event.getEventId(), failure);
		}
	}

	private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
		record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>브로커에 미리 물어보지 않고 항상 {@code true} 를 낸다. 릴레이가 한 차례 돌 때마다
	 * 메타데이터를 조회하면 보낼 것이 없을 때도 브로커를 두드리게 되고, 그 조회가 성공했다고
	 * 다음 {@code publish} 가 성공한다는 보장도 없다. <b>죽었는지는 실제로 보내 보고 안다</b> —
	 * 실패는 재시도 상한({@code gabolle.event.kafka.max-attempts})이 받아 준다.
	 *
	 * <p>이 빈 자체가 {@code gabolle.event.kafka.enabled=true} 일 때만 만들어진다. 꺼져 있으면
	 * {@link NoOpEventPublisher} 가 꽂혀 {@code false} 를 내고 릴레이가 조용히 물러난다.
	 */
	@Override
	public boolean isAvailable() {
		return true;
	}
}
