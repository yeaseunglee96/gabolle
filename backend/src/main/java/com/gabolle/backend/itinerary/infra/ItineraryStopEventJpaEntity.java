package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itinerary_stop_event} 표 매핑 — S15P21E201-1325.
 *
 * <p>🔴 이 표는 <b>쌓기만 한다.</b> 고치는 경로가 없어서 {@code updatable=false} 를 전부
 * 붙였다 — 나중에 누가 변경 감지로 값을 바꾸면 「그때 무슨 일이 있었나」의 기록이 조용히
 * 달라진다. 기록은 고치는 것이 아니라 하나 더 쌓는 것이다.
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
