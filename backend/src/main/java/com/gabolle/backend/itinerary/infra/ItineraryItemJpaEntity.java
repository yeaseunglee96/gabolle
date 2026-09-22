package com.gabolle.backend.itinerary.infra;

import java.time.LocalDate;
import java.time.LocalTime;
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

import com.gabolle.backend.itinerary.domain.ItineraryItem;

/**
 * {@code itinerary_item} 표 매핑.
 * {@code reasonCodes}·{@code warningCodes} 는 {@code @JdbcTypeCode(SqlTypes.ARRAY)} 로 매핑한다.
 */
@Entity
@Table(name = "itinerary_item")
public class ItineraryItemJpaEntity {

	@Id
	@Column(name = "itinerary_item_id")
	private UUID itineraryItemId;

	@Column(name = "itinerary_version_id", nullable = false, updatable = false)
	private UUID itineraryVersionId;

	@Column(name = "item_key", nullable = false, updatable = false)
	private UUID itemKey;

	@Column(name = "day_index", nullable = false, updatable = false)
	private int dayIndex;

	@Column(name = "visit_date", nullable = false, updatable = false)
	private LocalDate visitDate;

	@Column(name = "sequence", nullable = false, updatable = false)
	private int sequence;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "start_time", updatable = false)
	private LocalTime startTime;

	@Column(name = "end_time", updatable = false)
	private LocalTime endTime;

	@Column(name = "stay_minutes", updatable = false)
	private Integer stayMinutes;

	@Column(name = "locked", nullable = false, updatable = false)
	private boolean locked;

	@Column(name = "estimated_cost_krw", updatable = false)
	private Integer estimatedCostKrw;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_status", nullable = false, length = 20, updatable = false)
	private ItineraryItem.DataStatus dataStatus;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "reason_codes", nullable = false, updatable = false)
	private String[] reasonCodes;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "warning_codes", nullable = false, updatable = false)
	private String[] warningCodes;

	@Column(name = "source_request_id", updatable = false)
	private UUID sourceRequestId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ItineraryItemJpaEntity() {
		// JPA 전용
	}

	ItineraryItemJpaEntity(UUID itineraryItemId, UUID itineraryVersionId, UUID itemKey, int dayIndex,
			LocalDate visitDate, int sequence, UUID placeId, LocalTime startTime, LocalTime endTime,
			Integer stayMinutes, boolean locked, Integer estimatedCostKrw, ItineraryItem.DataStatus dataStatus,
			String[] reasonCodes, String[] warningCodes, UUID sourceRequestId, OffsetDateTime createdAt) {
		this.itineraryItemId = itineraryItemId;
		this.itineraryVersionId = itineraryVersionId;
		this.itemKey = itemKey;
		this.dayIndex = dayIndex;
		this.visitDate = visitDate;
		this.sequence = sequence;
		this.placeId = placeId;
		this.startTime = startTime;
		this.endTime = endTime;
		this.stayMinutes = stayMinutes;
		this.locked = locked;
		this.estimatedCostKrw = estimatedCostKrw;
		this.dataStatus = dataStatus;
		this.reasonCodes = reasonCodes;
		this.warningCodes = warningCodes;
		this.sourceRequestId = sourceRequestId;
		this.createdAt = createdAt;
	}

	UUID itineraryItemId() { return itineraryItemId; }
	UUID itineraryVersionId() { return itineraryVersionId; }
	UUID itemKey() { return itemKey; }
	int dayIndex() { return dayIndex; }
	LocalDate visitDate() { return visitDate; }
	int sequence() { return sequence; }
	UUID placeId() { return placeId; }
	LocalTime startTime() { return startTime; }
	LocalTime endTime() { return endTime; }
	Integer stayMinutes() { return stayMinutes; }
	boolean locked() { return locked; }
	Integer estimatedCostKrw() { return estimatedCostKrw; }
	ItineraryItem.DataStatus dataStatus() { return dataStatus; }
	String[] reasonCodes() { return reasonCodes; }
	String[] warningCodes() { return warningCodes; }
	UUID sourceRequestId() { return sourceRequestId; }
	OffsetDateTime createdAt() { return createdAt; }
}
