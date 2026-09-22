package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itinerary_excluded_place} 표 매핑.
 * 모든 칸이 {@code updatable=false} 다. 판은 덮어쓰지 않는 스냅샷이라 이 행도 한 번 쓰면 다시
 * 고치지 않는다 — 편집은 새 판에 새 행을 만드는 것이지 이 행을 갱신하는 것이 아니다.
 */
@Entity
@Table(name = "itinerary_excluded_place")
public class ItineraryExclusionJpaEntity {

	@Id
	@Column(name = "itinerary_excluded_place_id")
	private UUID itineraryExclusionId;

	@Column(name = "itinerary_version_id", nullable = false, updatable = false)
	private UUID itineraryVersionId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "item_key", updatable = false)
	private UUID itemKey;

	@Column(name = "excluded_by", nullable = false, updatable = false)
	private UUID excludedBy;

	@Column(name = "reason_code", nullable = false, length = 64, updatable = false)
	private String reasonCode;

	@Column(name = "operational_reason", length = 200, updatable = false)
	private String operationalReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ItineraryExclusionJpaEntity() {
		// JPA 전용
	}

	ItineraryExclusionJpaEntity(UUID itineraryExclusionId, UUID itineraryVersionId, UUID placeId, UUID itemKey,
			UUID excludedBy, String reasonCode, String operationalReason, OffsetDateTime createdAt) {
		this.itineraryExclusionId = itineraryExclusionId;
		this.itineraryVersionId = itineraryVersionId;
		this.placeId = placeId;
		this.itemKey = itemKey;
		this.excludedBy = excludedBy;
		this.reasonCode = reasonCode;
		this.operationalReason = operationalReason;
		this.createdAt = createdAt;
	}

	UUID itineraryExclusionId() { return itineraryExclusionId; }
	UUID itineraryVersionId() { return itineraryVersionId; }
	UUID placeId() { return placeId; }
	UUID itemKey() { return itemKey; }
	UUID excludedBy() { return excludedBy; }
	String reasonCode() { return reasonCode; }
	String operationalReason() { return operationalReason; }
	OffsetDateTime createdAt() { return createdAt; }
}
