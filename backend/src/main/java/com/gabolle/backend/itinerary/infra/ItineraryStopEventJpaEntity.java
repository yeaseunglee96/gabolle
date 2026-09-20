package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itinerary_stop_event} 표 매핑. 이 표는 쌓기만 하고 고치지 않는다 — 모든 칼럼에
 * {@code updatable=false} 를 붙여 변경 감지로 기록이 바뀌는 길을 막는다.
 */
@Entity
@Table(name = "itinerary_stop_event")
public class ItineraryStopEventJpaEntity {

	@Id
	@Column(name = "itinerary_stop_event_id", nullable = false, updatable = false)
	private UUID eventId;

	@Column(name = "itinerary_id", nullable = false, updatable = false)
	private UUID itineraryId;

	/** {@code START}·{@code PAUSE} 는 정차지가 없는 사건이라 비어 있다. */
	@Column(name = "item_key", updatable = false)
	private UUID itemKey;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private OffsetDateTime occurredAt;

	@Column(name = "recorded_by", nullable = false, updatable = false)
	private UUID recordedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ItineraryStopEventJpaEntity() {
		// JPA 전용
	}

	public ItineraryStopEventJpaEntity(UUID eventId, UUID itineraryId, UUID itemKey, String eventType,
			OffsetDateTime occurredAt, UUID recordedBy, OffsetDateTime createdAt) {
		this.eventId = eventId;
		this.itineraryId = itineraryId;
		this.itemKey = itemKey;
		this.eventType = eventType;
		this.occurredAt = occurredAt;
		this.recordedBy = recordedBy;
		this.createdAt = createdAt;
	}

	public UUID eventId() {
		return this.eventId;
	}

	public UUID itineraryId() {
		return this.itineraryId;
	}

	public UUID itemKey() {
		return this.itemKey;
	}

	public String eventType() {
		return this.eventType;
	}

	public OffsetDateTime occurredAt() {
		return this.occurredAt;
	}

	public UUID recordedBy() {
		return this.recordedBy;
	}

	public OffsetDateTime createdAt() {
		return this.createdAt;
	}
}
