package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.gabolle.backend.itinerary.domain.ItineraryItem;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itinerary_leg} 표 매핑.
 * {@code travelMode} 는 자바에서 열거값으로 검증하지 않는다. DB {@code ck_itinerary_leg_mode}
 * 가 저장 시점에 막는다 — 같은 목록을 자바 enum 으로 또 들고 있으면 한쪽만 바뀌는 날 조용히
 * 갈라진다.
 */
@Entity
@Table(name = "itinerary_leg")
public class ItineraryLegJpaEntity {

	@Id
	@Column(name = "itinerary_leg_id")
	private UUID itineraryLegId;

	@Column(name = "itinerary_version_id", nullable = false, updatable = false)
	private UUID itineraryVersionId;

	@Column(name = "day_index", nullable = false, updatable = false)
	private int dayIndex;

	@Column(name = "sequence", nullable = false, updatable = false)
	private int sequence;

	@Column(name = "from_place_id", updatable = false)
	private UUID fromPlaceId;

	@Column(name = "to_place_id", nullable = false, updatable = false)
	private UUID toPlaceId;

	@Column(name = "travel_mode", nullable = false, length = 30, updatable = false)
	private String travelMode;

	@Column(name = "distance_m", updatable = false)
	private Integer distanceM;

	@Column(name = "duration_min", updatable = false)
	private Integer durationMin;

	@Column(name = "walking_meters", updatable = false)
	private Integer walkingMeters;

	@Column(name = "ascent_m", updatable = false)
	private Integer ascentM;

	@Column(name = "stair_steps", updatable = false)
	private Integer stairSteps;

	/** 위의 거리·시간이 실제 응답인지 어림값인지. 이 칸이 생기기 전 행은 NULL 이다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "data_status", length = 20, updatable = false)
	private ItineraryItem.DataStatus dataStatus;

	/**
	 * 이 구간의 이동 요금(원). {@code null} 은 "얼마인지 모른다" 이고 {@code 0} 은 "공짜다" 다.
	 * DB 쪽 {@code ck_itinerary_leg_fare_krw} 가 음수를 막는다.
	 */
	@Column(name = "fare_krw", updatable = false)
	private Integer fareKrw;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ItineraryLegJpaEntity() {
		// JPA 전용
	}

	ItineraryLegJpaEntity(UUID itineraryLegId, UUID itineraryVersionId, int dayIndex, int sequence,
			UUID fromPlaceId, UUID toPlaceId, String travelMode, Integer distanceM, Integer durationMin,
			Integer walkingMeters, Integer ascentM, Integer stairSteps,
			ItineraryItem.DataStatus dataStatus, Integer fareKrw, OffsetDateTime createdAt) {
		this.itineraryLegId = itineraryLegId;
		this.itineraryVersionId = itineraryVersionId;
		this.dayIndex = dayIndex;
		this.sequence = sequence;
		this.fromPlaceId = fromPlaceId;
		this.toPlaceId = toPlaceId;
		this.travelMode = travelMode;
		this.distanceM = distanceM;
		this.durationMin = durationMin;
		this.walkingMeters = walkingMeters;
		this.ascentM = ascentM;
		this.stairSteps = stairSteps;
		this.dataStatus = dataStatus;
		this.fareKrw = fareKrw;
		this.createdAt = createdAt;
	}

	UUID itineraryLegId() { return itineraryLegId; }
	UUID itineraryVersionId() { return itineraryVersionId; }
	int dayIndex() { return dayIndex; }
	int sequence() { return sequence; }
	UUID fromPlaceId() { return fromPlaceId; }
	UUID toPlaceId() { return toPlaceId; }
	String travelMode() { return travelMode; }
	Integer distanceM() { return distanceM; }
	Integer durationMin() { return durationMin; }
	Integer walkingMeters() { return walkingMeters; }
	Integer ascentM() { return ascentM; }
	Integer stairSteps() { return stairSteps; }
	ItineraryItem.DataStatus dataStatus() { return dataStatus; }
	Integer fareKrw() { return fareKrw; }
	OffsetDateTime createdAt() { return createdAt; }
}
