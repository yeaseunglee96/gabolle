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

/** {@code trip_seed_place} 표 매핑. 기본키는 (trip_id, place_id). */
@Entity
@Table(name = "trip_seed_place")
public class TripSeedPlaceJpaEntity {

	@EmbeddedId
	private Key key;

	@Column(name = "sequence", nullable = false, updatable = false)
	private int sequence;

	@Column(name = "source_trip_id", updatable = false)
	private UUID sourceTripId;

	@Column(name = "source_share_link_id", updatable = false)
	private UUID sourceShareLinkId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected TripSeedPlaceJpaEntity() {
		// JPA 전용
	}

	TripSeedPlaceJpaEntity(UUID tripId, UUID placeId, int sequence, UUID sourceTripId, UUID sourceShareLinkId,
			OffsetDateTime createdAt) {
		this.key = new Key(tripId, placeId);
		this.sequence = sequence;
		this.sourceTripId = sourceTripId;
		this.sourceShareLinkId = sourceShareLinkId;
		this.createdAt = createdAt;
	}

	Key key() { return key; }
	int sequence() { return sequence; }
	UUID sourceTripId() { return sourceTripId; }
	UUID sourceShareLinkId() { return sourceShareLinkId; }
	OffsetDateTime createdAt() { return createdAt; }

	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "trip_id", nullable = false, updatable = false)
		private UUID tripId;

		@Column(name = "place_id", nullable = false, updatable = false)
		private UUID placeId;

		protected Key() {
			// JPA 전용
		}

		Key(UUID tripId, UUID placeId) {
			this.tripId = tripId;
			this.placeId = placeId;
		}

		UUID tripId() { return tripId; }
		UUID placeId() { return placeId; }

		@Override
		public boolean equals(Object o) {
			return o instanceof Key other && tripId.equals(other.tripId) && placeId.equals(other.placeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(tripId, placeId);
		}
	}
}
