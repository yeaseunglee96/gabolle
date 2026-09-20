package com.gabolle.backend.trip.infra;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** {@code trip_travel_area} 표 매핑. 기본키는 (trip_id, area_code). */
@Entity
@Table(name = "trip_travel_area")
public class TripTravelAreaJpaEntity {

	@EmbeddedId
	private Key key;

	@Column(name = "sequence", nullable = false, updatable = false)
	private int sequence;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected TripTravelAreaJpaEntity() {
		// JPA 전용
	}

	TripTravelAreaJpaEntity(UUID tripId, String areaCode, int sequence, OffsetDateTime createdAt) {
		this.key = new Key(tripId, areaCode);
		this.sequence = sequence;
		this.createdAt = createdAt;
	}

	Key key() {
		return this.key;
	}

	int sequence() {
		return this.sequence;
	}

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "trip_id", nullable = false, updatable = false)
		private UUID tripId;

		@Column(name = "area_code", nullable = false, updatable = false, length = 30)
		private String areaCode;

		protected Key() {
			// JPA 전용
		}

		Key(UUID tripId, String areaCode) {
			this.tripId = tripId;
			this.areaCode = areaCode;
		}

		UUID tripId() {
			return this.tripId;
		}

		String areaCode() {
			return this.areaCode;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Key that)) {
				return false;
			}
			return Objects.equals(this.tripId, that.tripId) && Objects.equals(this.areaCode, that.areaCode);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.tripId, this.areaCode);
		}
	}
}
