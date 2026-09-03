package com.gabolle.backend.event.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 업무 데이터와 <b>같은 트랜잭션</b>에 실려 저장되는 이벤트 한 건.
 *
 * <p>Outbox(**업무 저장과 같은 DB 트랜잭션에서 이벤트를 같은 DB 에 먼저 적어 두고, 나중에
 * 별도 프로세스가 그것을 읽어 메시지 브로커로 보내는 방식**). 업무는 저장됐는데 이벤트만
 * 사라지는 경우를 구조적으로 없앤다 — 둘이 한 트랜잭션이라 같이 남거나 같이 롤백된다.
 *
 * <p>🔴 {@code eventId} 가 PK 다. 멱등(**같은 것을 두 번 처리해도 결과가 한 번과 같은 성질**)
 * 을 애플리케이션 조회가 아니라 DB 가 보장하게 하려는 것이다.
 */
@Entity
@Table(name = "event_outbox")
public class EventOutbox {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	private String eventType;

	@Column(name = "event_version", nullable = false, updatable = false)
	private int eventVersion;

	@Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;

	@Column(name = "partition_key", nullable = false, length = 128, updatable = false)
	private String partitionKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, updatable = false)
	private String payload;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private OffsetDateTime occurredAt;

	@Column(name = "received_at", nullable = false, updatable = false)
	private OffsetDateTime receivedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "publish_status", nullable = false, length = 20)
	private OutboxPublishStatus publishStatus;

	@Column(name = "published_at")
	private OffsetDateTime publishedAt;

	@Column(name = "publish_attempts", nullable = false)
	private int publishAttempts;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	/**
	 * 🔴 aggregate_type 이 recommendation 이면 항상 null 이다 — 그 이벤트의 요청 축은
	 * aggregateId 가 이미 들고 있다(2026-09-03 결정, S15P21E201-352-event-outbox-join-axes).
	 */
	@Column(name = "request_id", updatable = false)
	private UUID requestId;

	@Column(name = "user_id", updatable = false)
	private UUID userId;

	@Column(name = "trip_id", updatable = false)
	private UUID tripId;

	/** CLIENT · SERVER (DR-13). */
	@Enumerated(EnumType.STRING)
	@Column(name = "producer", length = 16, updatable = false)
	private Producer producer;

	/**
	 * 🔴 DB 가 BIGSERIAL 로 채운다 — 여기서 값을 넣지 않는다({@code insertable = false}).
	 * 발행 대기 조회가 이 값으로 정렬한다({@code EventOutboxRepository}) —
	 * occurred_at 만으로는 같은 시각의 순서가 확정되지 않는다(S15P21E201-354 실측).
	 */
	@Column(name = "seq", insertable = false, updatable = false)
	private Long seq;

	protected EventOutbox() {
		// JPA 전용
	}

	public EventOutbox(UUID eventId, String eventType, int eventVersion, String aggregateType, UUID aggregateId,
			String partitionKey, String payload, OffsetDateTime occurredAt, OffsetDateTime receivedAt,
			UUID requestId, UUID userId, UUID tripId, Producer producer) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.eventVersion = eventVersion;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.requestId = requestId;
		this.userId = userId;
		this.tripId = tripId;
		this.producer = producer;
		this.partitionKey = partitionKey;
		this.payload = payload;
		this.occurredAt = occurredAt;
		this.receivedAt = receivedAt;
		this.publishStatus = OutboxPublishStatus.PENDING;
	}

	/**
	 * 아직 안 보냈는가. 릴레이가 이걸로 고른다.
	 *
	 * <p>{@code publishedAt} 을 기준으로 본다 — {@code publishStatus} 는 FAILED 로도 갈 수
	 * 있는데, 실패한 것은 <b>다시 보내야 하므로 여전히 대기</b>다.
	 */
	public boolean isPending() {
		return this.publishedAt == null;
	}

	/**
	 * 전송 성공.
	 *
	 * <p>🔴 이미 보낸 것을 다시 성공 처리하지 않는다. 릴레이가 두 번 돌아도 발행 시각이
	 * 덮어써지면 "언제 보냈나" 가 흐려진다.
	 */
	public void markPublished(OffsetDateTime at) {
		if (this.publishedAt != null) {
			return;
		}
		this.publishedAt = at;
		this.publishStatus = OutboxPublishStatus.PUBLISHED;
		this.lastError = null;
	}

	/**
	 * 전송 실패.
	 *
	 * <p>🔴 실패한 것을 표에서 지우지 않는다. 다음 차례에 다시 시도한다 — 행동 기록은
	 * 나중에 다시 물어볼 수 없는 종류의 데이터다.
	 */
	public void markFailed(String error) {
		this.publishAttempts++;
		this.publishStatus = OutboxPublishStatus.FAILED;
		this.lastError = error;
	}

	public UUID getEventId() {
		return eventId;
	}

	public String getEventType() {
		return eventType;
	}

	public int getEventVersion() {
		return eventVersion;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getTripId() {
		return tripId;
	}

	public Producer getProducer() {
		return producer;
	}

	public Long getSeq() {
		return seq;
	}

	public String getPartitionKey() {
		return partitionKey;
	}

	public String getPayload() {
		return payload;
	}

	public OffsetDateTime getOccurredAt() {
		return occurredAt;
	}

	public OffsetDateTime getReceivedAt() {
		return receivedAt;
	}

	public OutboxPublishStatus getPublishStatus() {
		return publishStatus;
	}

	public OffsetDateTime getPublishedAt() {
		return publishedAt;
	}

	public int getPublishAttempts() {
		return publishAttempts;
	}

	public String getLastError() {
		return lastError;
	}
}
