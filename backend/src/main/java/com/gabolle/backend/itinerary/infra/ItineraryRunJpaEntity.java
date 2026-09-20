package com.gabolle.backend.itinerary.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code itinerary_run} 표 매핑. 읽기 전용이다 — 쓰기는 {@link JpaItineraryRunRepository} 의
 * {@code INSERT ... ON CONFLICT DO UPDATE} 가 맡는다. 출발을 두 번 누르는 것이 정상 경로라
 * 키가 반드시 겹치고, PostgreSQL 은 문장 하나가 실패하면 트랜잭션 전체를 못 쓰게 만든다.
 */
@Entity
@Table(name = "itinerary_run")
public class ItineraryRunJpaEntity {

	@Id
	@Column(name = "itinerary_id")
	private UUID itineraryId;

	@Column(name = "trip_id", nullable = false)
	private UUID tripId;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "current_stop_index", nullable = false)
	private int currentStopIndex;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ItineraryRunJpaEntity() {
		// JPA 전용
	}

	public UUID itineraryId() {
		return this.itineraryId;
	}

	public UUID tripId() {
		return this.tripId;
	}

	public String status() {
		return this.status;
	}

	public int currentStopIndex() {
		return this.currentStopIndex;
	}

	public OffsetDateTime startedAt() {
		return this.startedAt;
	}

	public OffsetDateTime updatedAt() {
		return this.updatedAt;
	}
}
