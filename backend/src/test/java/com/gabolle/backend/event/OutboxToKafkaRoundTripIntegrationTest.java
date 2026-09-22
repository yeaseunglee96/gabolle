package com.gabolle.backend.event;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxRelayService;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.application.port.EventPublisherPort;
import com.gabolle.backend.event.config.KafkaEventProperties;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.Producer;
import com.gabolle.backend.event.infra.KafkaEventPublisher;
import com.gabolle.backend.event.repository.EventConsumptionRepository;
import com.gabolle.backend.event.repository.EventOutboxRepository;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.RecommendationSliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * <b>Outbox → Kafka → 소비자 최소 경로를 한 번 재현한다</b> (S15P21E201-561, 마일스톤 M3).
 *
 * <p>개발계획서의 M3 통과 조건이 <i>"Outbox → Kafka → Spark 최소 경로 1회 재현"</i> 이다.
 * 🔴 <b>Spark 는 이 저장소에 없다</b>({@code infra/personalization} 에 있는 것은 Airflow·MLflow
 * 뿐이다). 그래서 받는 쪽을 백엔드 소비자로 두고 그 자리까지를 재현한다 — 없는 것을 있는 척
 * 흉내 내는 것보다, 어디까지 되고 어디부터 안 되는지가 드러나는 편이 낫다.
 *
 * <p>브로커도 데이터베이스도 <b>진짜를 띄운다.</b> 임베디드 브로커로 바꾸면 이 시험이 증명하려는
 * 것("진짜로 오갔는가") 자체가 증명되지 않는다.
 *
 * <p>릴레이 스케줄러는 꺼 두고 {@link OutboxRelayService#relayOnce()} 를 손으로 부른다 — cron 을
 * 기다리면 시험이 시계에 기대게 되고, 그런 시험은 느리면서 가끔 이유 없이 빨개진다.
 */
@SpringBootTest(classes = RecommendationSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true",
		"gabolle.recommendation.service-version=test-service-0.0.1",
		"gabolle.recommendation.deployment-environment=test",
		"gabolle.recommendation.default-top-k=5",
		// 보내는 쪽·받는 쪽 둘 다 켠다. 운영 기본값은 둘 다 꺼짐이다.
		"gabolle.event.kafka.enabled=true",
		"gabolle.event.kafka.consumer-enabled=true",
		"gabolle.event.kafka.topic=gabolle.events.roundtrip-test",
		// 묶음 이름을 시험 전용으로 둔다 — 운영 이름을 쓰면 오프셋 기억이 섞인다.
		"gabolle.event.kafka.consumer-group=roundtrip-test-group",
		// 스케줄러는 끈다. relayOnce() 를 손으로 부른다.
		"gabolle.event.outbox-relay.enabled=false"
})
@ExtendWith(PostgresAvailableCondition.class)
@Testcontainers
class OutboxToKafkaRoundTripIntegrationTest {

	/**
	 * compose 에 적은 것과 <b>같은 이미지</b>다 — 시험과 운영이 다른 판을 쓰면 시험의 뜻이 준다.
	 *
	 * <p>🔴 3.9.0 을 쓰면 안 된다. Testcontainers 1.21.3 이 그 판을 띄울 때
	 * {@code advertised.listeners} 에 {@code 0.0.0.0} 을 넣는데, 카프카가 기동 전에 그것을
	 * 거부한다({@code cannot use the nonroutable meta-address}). 컨테이너가 종료 코드 1 로
	 * 죽고 시험은 {@code initializationError} 로만 보여서 원인이 안 드러난다.
	 */
	@Container
	static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

	private static final Duration PATIENCE = Duration.ofSeconds(30);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
	}

	@Autowired
	private OutboxService outboxService;

	@Autowired
	private OutboxRelayService relayService;

	@Autowired
	private EventPublisherPort publisher;

	@Autowired
	private EventOutboxRepository outboxRepository;

	@Autowired
	private EventConsumptionRepository consumptionRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private EventOutbox appendOne(UUID eventId, String partitionKey) {
		UUID tripId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		this.transactionTemplate.executeWithoutResult((status) -> this.outboxService
			.append(new OutboxAppendCommand(eventId, "trip_created", 1, "trip", tripId, partitionKey,
					Map.of("event_kind", "roundtrip-test"), OffsetDateTime.now(), UUID.randomUUID(), userId, tripId,
					Producer.SERVER)));
		return this.outboxRepository.findById(eventId).orElseThrow();
	}

	@Test
	@DisplayName("🔴 M3 최소 경로 — 아웃박스에 적은 것이 브로커를 지나 소비자 장부까지 한 번에 닿는다")
	void outboxRowTravelsThroughKafkaAndLandsInTheLedger() {
		UUID eventId = UUID.randomUUID();
		String partitionKey = "trip:" + UUID.randomUUID();

		EventOutbox appended = appendOne(eventId, partitionKey);
		assertThat(appended.isPending()).as("적자마자는 아직 안 나간 상태여야 한다").isTrue();
		assertThat(this.consumptionRepository.existsById(eventId)).isFalse();

		// 🔴 정확히 1 을 기대하면 안 된다. 릴레이는 **밀려 있는 것을 전부** 내보내고, 이
		//    데이터베이스는 다른 시험들과 함께 쓴다(TestDatabase 가 JVM 당 하나를 띄운다).
		//    이 클래스만 돌리면 1 이지만 전체를 돌리면 2 이상이 된다 — 실제로 그렇게 빨개졌다.
		//    "내 것이 갔는가" 는 아래 장부 확인이 답한다. 개수는 그 질문의 답이 아니다.
		int sent = this.relayService.relayOnce();
		assertThat(sent).as("릴레이가 적어도 이번 건은 내보내야 한다").isGreaterThanOrEqualTo(1);

		await().atMost(PATIENCE)
			.untilAsserted(() -> assertThat(this.consumptionRepository.existsById(eventId))
				.as("소비자가 받아 장부에 적어야 한다")
				.isTrue());

		// 보낸 쪽 표에도 흔적이 남아야 한다 — 안 남으면 다음 차례에 같은 것을 또 보낸다.
		EventOutbox afterRelay = this.outboxRepository.findById(eventId).orElseThrow();
		assertThat(afterRelay.getPublishedAt()).as("발행 시각이 찍혀야 한다").isNotNull();
		assertThat(afterRelay.isPending()).isFalse();

		// 순서를 지켜야 하는 단위가 그대로 실려 갔는가 — 메시지 키가 partitionKey 라는 계약.
		assertThat(this.consumptionRepository.findById(eventId).orElseThrow().getPartitionKey())
			.as("메시지 키는 partitionKey 여야 한다 — eventId 를 키로 쓰면 순서가 흩어진다")
			.isEqualTo(partitionKey);
	}

	@Test
	@DisplayName("🔴 같은 것이 두 번 와도 장부에는 한 줄이다 — 릴레이는 '적어도 한 번' 만 보장한다")
	void redeliveryIsAppliedOnlyOnce() {
		UUID eventId = UUID.randomUUID();
		EventOutbox appended = appendOne(eventId, "trip:" + UUID.randomUUID());

		this.relayService.relayOnce();
		await().atMost(PATIENCE)
			.untilAsserted(() -> assertThat(this.consumptionRepository.existsById(eventId)).isTrue());

		OffsetDateTime firstConsumedAt = this.consumptionRepository.findById(eventId).orElseThrow().getConsumedAt();

		// 릴레이가 보낸 뒤 published_at 을 적기 전에 죽은 상황과 같다 — 같은 건을 다시 보낸다.
		this.publisher.publish(appended);

		// 두 번째가 처리될 시간을 준 뒤에도 장부가 안 늘어야 한다. "아직 안 왔을 뿐" 과
		// 구별하려고, 세 번째 이벤트가 도착하는 것을 기준선으로 쓴다.
		UUID laterEventId = UUID.randomUUID();
		appendOne(laterEventId, "trip:" + UUID.randomUUID());
		this.relayService.relayOnce();
		await().atMost(PATIENCE)
			.untilAsserted(() -> assertThat(this.consumptionRepository.existsById(laterEventId)).isTrue());

		assertThat(this.consumptionRepository.findById(eventId).orElseThrow().getConsumedAt())
			.as("두 번째 전달이 장부를 덮어쓰지 않아야 한다")
			.isEqualTo(firstConsumedAt);
	}

	@Test
	@DisplayName("🔴 브로커가 죽어 있으면 아웃박스에 남는다 — 살아난 뒤에 나간다")
	void eventSurvivesWhileTheBrokerIsUnreachable() {
		UUID eventId = UUID.randomUUID();
		EventOutbox appended = appendOne(eventId, "trip:" + UUID.randomUUID());

		// 컨테이너를 내리지 않는다 — 내리면 이 클래스의 다른 시험까지 말린다. 대신 아무도
		// 안 듣고 있는 주소를 향한 발행자를 따로 만들어 "죽은 브로커" 를 그대로 재현한다.
		KafkaEventPublisher deadBrokerPublisher = new KafkaEventPublisher(deadBrokerTemplate(),
				new KafkaEventProperties());

		assertThatThrownBy(() -> deadBrokerPublisher.publish(appended))
			.as("죽은 브로커로 보내면 반드시 실패해야 한다 — 조용히 성공하면 이벤트가 사라진다")
			.isInstanceOf(EventPublisherPort.EventPublishException.class);

		// 실패했어도 적힌 것은 그대로다. 이것이 아웃박스를 두는 이유 전부다.
		EventOutbox stillThere = this.outboxRepository.findById(eventId).orElseThrow();
		assertThat(stillThere.isPending()).isTrue();
		assertThat(stillThere.getPublishedAt()).isNull();

		// 살아 있는 브로커로 다시 돌리면 그때 나간다.
		assertThat(this.relayService.relayOnce()).isGreaterThanOrEqualTo(1);
		await().atMost(PATIENCE)
			.untilAsserted(() -> assertThat(this.consumptionRepository.existsById(eventId)).isTrue());
	}

	/**
	 * 아무도 안 듣는 주소를 향한 {@link KafkaTemplate}.
	 *
	 * <p>🔴 {@code max.block.ms} 를 반드시 짧게 준다. 기본값은 60초라, 안 주면 이 시험 하나가
	 * 1분을 통째로 기다린다 — 그런 시험은 곧 아무도 안 돌리게 된다.
	 */
	private static KafkaTemplate<String, String> deadBrokerTemplate() {
		Map<String, Object> config = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
				ProducerConfig.MAX_BLOCK_MS_CONFIG, 2_000, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1_000,
				ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2_000);
		return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
	}
}
