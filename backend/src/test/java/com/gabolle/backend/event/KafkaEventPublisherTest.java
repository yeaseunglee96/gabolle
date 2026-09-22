package com.gabolle.backend.event;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.config.KafkaEventProperties;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.infra.KafkaEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 카프카 어댑터가 <b>순서</b>와 <b>멱등</b>을 각자 제자리에 싣는가 (S15P21E201-561).
 *
 * <p>브로커를 띄우지 않는다. 여기서 검사할 것은 "무엇을 어느 칸에 넣는가" 라는 계약이고,
 * 그것은 {@link KafkaTemplate} 에 넘긴 레코드만 보면 전부 드러난다. 브로커를 띄우면 검사
 * 시간만 늘고 이 계약은 더 검증되지 않는다.
 */
class KafkaEventPublisherTest {

	private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

	private static final String PARTITION_KEY = "trip:8f14e45f-ea8d-4b9c-8f2a-1b0d3c5e7a91";

	private KafkaTemplate<String, String> kafkaTemplate;

	private KafkaEventProperties properties;

	private KafkaEventPublisher publisher;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		this.kafkaTemplate = mock(KafkaTemplate.class);
		this.properties = new KafkaEventProperties();
		this.publisher = new KafkaEventPublisher(this.kafkaTemplate, this.properties);
		given(this.kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
	}

	private EventOutbox event() {
		OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
		return new EventOutbox(UUID.randomUUID(), "recommendation_impression", 1, "trip", UUID.randomUUID(),
				PARTITION_KEY, "{\"placeId\":\"abc\"}", at, at, null, null, null, Producer.CLIENT);
	}

	@SuppressWarnings("unchecked")
	private ProducerRecord<String, String> captureSentRecord() {
		ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
		verify(this.kafkaTemplate).send(captor.capture());
		return captor.getValue();
	}

	private static String headerValue(ProducerRecord<String, String> record, String key) {
		Header header = record.headers().lastHeader(key);
		return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("🔴 메시지 키는 partitionKey 다 — eventId 를 키로 쓰면 한 여행의 이벤트가 파티션에 흩어진다")
	void messageKeyIsThePartitionKeyNotTheEventId() {
		EventOutbox event = event();

		this.publisher.publish(event);

		ProducerRecord<String, String> record = captureSentRecord();
		assertThat(record.key()).isEqualTo(PARTITION_KEY);
		assertThat(record.key()).isNotEqualTo(event.getEventId().toString());
	}

	@Test
	@DisplayName("🔴 eventId 는 헤더로 간다 — 받는 쪽이 중복을 거르는 근거다")
	void eventIdRidesInAHeaderForDeduplication() {
		EventOutbox event = event();

		this.publisher.publish(event);

		ProducerRecord<String, String> record = captureSentRecord();
		assertThat(headerValue(record, "event_id")).isEqualTo(event.getEventId().toString());
		assertThat(headerValue(record, "event_type")).isEqualTo("recommendation_impression");
		assertThat(headerValue(record, "event_version")).isEqualTo("1");
	}

	@Test
	@DisplayName("본문은 아웃박스에 적힌 JSON 그대로 — 여기서 한 번 더 감싸지 않는다")
	void payloadGoesThroughUnchanged() {
		this.publisher.publish(event());

		assertThat(captureSentRecord().value()).isEqualTo("{\"placeId\":\"abc\"}");
	}

	@Test
	@DisplayName("토픽은 설정에서 온다")
	void topicComesFromProperties() {
		this.properties.setTopic("gabolle.events.v2");

		this.publisher.publish(event());

		assertThat(captureSentRecord().topic()).isEqualTo("gabolle.events.v2");
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("🔴 전송이 실패하면 EventPublishException 으로 바꾼다 — 삼키면 릴레이가 안 보낸 것을 보냈다고 적는다")
	void sendFailureBecomesEventPublishException() {
		given(this.kafkaTemplate.send(any(ProducerRecord.class)))
			.willReturn(CompletableFuture.failedFuture(new IllegalStateException("브로커 없음")));

		assertThatThrownBy(() -> this.publisher.publish(event()))
			.isInstanceOf(EventPublisherPort.EventPublishException.class)
			.hasMessageContaining("카프카 발행 실패");
	}

	@Test
	@DisplayName("발행자가 꽂혀 있으면 내보낼 수 있는 상태다 — 죽었는지는 실제로 보내 보고 안다")
	void isAvailableWhenTheBeanExists() {
		assertThat(this.publisher.isAvailable()).isTrue();
	}

	@Test
	@DisplayName("기본 토픽 이름에 판 번호가 붙어 있다 — 규약을 바꾸면 새 토픽으로 간다")
	void defaultTopicIsVersioned() {
		assertThat(new KafkaEventProperties().getTopic()).isEqualTo("gabolle.events.v1");
		// 기본은 꺼짐 — 의존성을 더한 것만으로 운영 동작이 바뀌면 안 된다.
		assertThat(new KafkaEventProperties().isEnabled()).isFalse();
	}
}
