package com.gabolle.backend.trip.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code trip_budget} 표 매핑 — 여행마다 예산 하나(S15P21E201-1935). 안 정했으면 줄이 없다. */
@Entity
@Table(name = "trip_budget")
public class TripBudgetJpaEntity {

	/** DB 의 {@code ck_trip_budget_amount} 와 같다. */
	public static final int MAX_AMOUNT_KRW = 1_000_000_000;

	@Id
	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	@Column(name = "amount_krw", nullable = false)
	private int amountKrw;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected TripBudgetJpaEntity() {
		// JPA 전용
	}

	public TripBudgetJpaEntity(UUID tripId, int amountKrw, UUID updatedBy, OffsetDateTime now) {
		this.tripId = tripId;
		this.amountKrw = requireAmount(amountKrw);
		this.updatedBy = updatedBy;
		this.updatedAt = now;
	}

	public void change(int amountKrw, UUID updatedBy, OffsetDateTime now) {
		this.amountKrw = requireAmount(amountKrw);
		this.updatedBy = updatedBy;
		this.updatedAt = now;
	}

	public static int requireAmount(int amountKrw) {
		if (amountKrw < 1 || amountKrw > MAX_AMOUNT_KRW) {
			throw new IllegalArgumentException("예산은 1원~" + MAX_AMOUNT_KRW + "원 사이여야 합니다: " + amountKrw);
		}
		return amountKrw;
	}

	public UUID getTripId() {
		return this.tripId;
	}

	public int getAmountKrw() {
		return this.amountKrw;
	}
}
