package com.gabolle.backend.trip.infra;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.gabolle.backend.trip.domain.Trip;

/**
 * {@code trip} 표 매핑 — S15P21E201-461.
 *
 * <p>🔴 {@link Trip}(도메인) 은 JPA 를 모른다. 이 클래스가 그 경계를 대신 진다 —
 * {@link JpaTripRepository} 가 여기서만 변환한다.
 *
 * <p>🔴 {@code version}·{@code origin_source}·{@code origin_area_code}·
 * {@code travel_modes} 컬럼은 여기서 <b>일부러</b> 매핑하지 않는다. 도메인
 * {@link Trip} 에 아직 그 값이 없고, 지어낸 값을 넣으면 그 결정을 여기서 대신
 * 내리는 셈이 된다(V120000 마이그레이션과 같은 원칙). 매핑하지 않으면 INSERT 문에
 * 그 칸이 아예 안 실리고, DB 의 DEFAULT('{}' · 1)가 대신 채운다.
 */
@Entity
@Table(name = "trip")
public class TripJpaEntity {

	@Id
	@Column(name = "trip_id")
	private UUID tripId;

	@Column(name = "owner_user_id", nullable = false, updatable = false)
	private UUID ownerUserId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "origin_lat")
	private Double originLat;

	@Column(name = "origin_lng")
	private Double originLng;

	@Column(name = "budget_krw")
	private Long budgetKrw;

	@Column(name = "party_size")
	private Integer partySize;

	/** 하루 활동 시간대 프리셋. 예: {@code MORNING_TO_EVENING} — V160000 이 추가한 칸. */
	@Column(name = "time_window")
	private String timeWindow;

	@Column(name = "timezone", nullable = false)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private Trip.Status status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	protected TripJpaEntity() {
		// JPA 전용
	}

	TripJpaEntity(UUID tripId, UUID ownerUserId, LocalDate startDate, LocalDate endDate,
			Double originLat, Double originLng, Long budgetKrw, Integer partySize,
			String timeWindow, String timezone, Trip.Status status,
			OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime deletedAt) {
		this.tripId = tripId;
		this.ownerUserId = ownerUserId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.originLat = originLat;
		this.originLng = originLng;
		this.budgetKrw = budgetKrw;
		this.partySize = partySize;
		this.timeWindow = timeWindow;
		this.timezone = timezone;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.deletedAt = deletedAt;
	}

	UUID tripId() { return tripId; }
	UUID ownerUserId() { return ownerUserId; }
	LocalDate startDate() { return startDate; }
	LocalDate endDate() { return endDate; }
	Double originLat() { return originLat; }
	Double originLng() { return originLng; }
	Long budgetKrw() { return budgetKrw; }
	Integer partySize() { return partySize; }
	String timeWindow() { return timeWindow; }
	String timezone() { return timezone; }
	Trip.Status status() { return status; }
	OffsetDateTime createdAt() { return createdAt; }
	OffsetDateTime updatedAt() { return updatedAt; }
	OffsetDateTime deletedAt() { return deletedAt; }
}
