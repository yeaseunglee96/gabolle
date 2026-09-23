package com.gabolle.backend.event.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 브로커에서 받아 <b>이미 반영한</b> 이벤트 한 건 (S15P21E201-561).
 *
 * <p>{@code eventId} 가 PK 다 — {@link EventOutbox} 와 같은 이유이고, 여기서는 더 중요하다.
 * 릴레이가 "적어도 한 번" 을 보장하므로 같은 이벤트가 두 번 올 수 있는데, 소비자 코드에서
 * "이미 처리했나" 를 조회하고 아니면 처리하는 식으로 막으면 <b>조회와 쓰기 사이의 틈</b>에서
 * 둘 다 통과한다. 그래서 데이터베이스가 PK 로 판정하게 한다.
 *
 * <p>🔴 <b>이 엔티티로 {@code save} 해서 넣지 마라</b> (S15P21E201-1501). 번호를 직접 넣고
 * {@code Persistable}·{@code @Version} 이 없어서 Spring Data 가 {@code merge} 로 저장하고,
 * merge 는 이미 있는 행을 만나면 <b>오류 없이</b> 넘어간다 — 「PK 가 두 번째를 거부한다」가
 * 실제로는 안 일어난다. 예전에 그걸 믿고 중복을 {@code catch} 로 잡으려 했는데 한 번도 안
 * 돌았다. 넣는 것은 {@code EventConsumptionService} 가 {@code ON CONFLICT DO NOTHING} 으로
 * 한다. 이 엔티티는 읽을 때만 쓴다.
 *
 * <p>본문({@code payload})을 들고 있지 않다. 정본은 {@code event_outbox} 다 — 한 벌 더 두면
 * 둘이 어긋날 수 있고, 어긋나도 아무 오류가 안 난다.
 */
@Entity
@Table(name = "event_consumption")
public class EventConsumption {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	private String eventType;

	@Column(name = "partition_key", nullable = false, length = 128, updatable = false)
	private String partitionKey;

	@Column(name = "consumer_group", nullable = false, length = 128, updatable = false)
	private String consumerGroup;

	@Column(name = "kafka_partition", nullable = false, updatable = false)
	private int kafkaPartition;

	@Column(name = "kafka_offset", nullable = false, updatable = false)
	private long kafkaOffset;

	@Column(name = "consumed_at", nullable = false, updatable = false)
	private OffsetDateTime consumedAt;

	/** JPA 용. 직접 부르지 않는다. */
	protected EventConsumption() {
	}

	public EventConsumption(UUID eventId, String eventType, String partitionKey, String consumerGroup,
			int kafkaPartition, long kafkaOffset, OffsetDateTime consumedAt) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.partitionKey = partitionKey;
		this.consumerGroup = consumerGroup;
		this.kafkaPartition = kafkaPartition;
		this.kafkaOffset = kafkaOffset;
		this.consumedAt = consumedAt;
	}

	public UUID getEventId() {
		return this.eventId;
	}

	public String getEventType() {
		return this.eventType;
	}

	public String getPartitionKey() {
		return this.partitionKey;
	}

	public String getConsumerGroup() {
		return this.consumerGroup;
	}

	public int getKafkaPartition() {
		return this.kafkaPartition;
	}

	public long getKafkaOffset() {
		return this.kafkaOffset;
	}

	public OffsetDateTime getConsumedAt() {
		return this.consumedAt;
	}
}
