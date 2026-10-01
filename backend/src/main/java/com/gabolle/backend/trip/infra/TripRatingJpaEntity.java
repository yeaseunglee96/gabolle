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

/**
 * {@code trip_rating} 표 매핑. 여행 하나에 사람마다 한 줄이라 (여행, 사람)이 곧 기본키다 —
 * 따로 id 칸을 두면 같은 사람의 별점이 둘 들어갈 자리가 생긴다.
 */
@Entity
@Table(name = "trip_rating")
public class TripRatingJpaEntity {

	/** 1~5. DB 의 {@code ck_trip_rating_score} 도 같은 범위를 지킨다. */
	public static final int MIN_SCORE = 1;

	public static final int MAX_SCORE = 5;

	@EmbeddedId
	private Key id;

	@Column(name = "score", nullable = false)
	private short score;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected TripRatingJpaEntity() {
		// JPA 전용
	}

	public TripRatingJpaEntity(UUID tripId, UUID userId, int score, OffsetDateTime now) {
		this.id = new Key(tripId, userId);
		this.score = requireScore(score);
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 다시 매긴다. {@code created_at} 은 그대로 둔다 — 처음 매긴 시각이다. */
	public void rescore(int score, OffsetDateTime now) {
		this.score = requireScore(score);
		this.updatedAt = now;
	}

	public static short requireScore(int score) {
		if (score < MIN_SCORE || score > MAX_SCORE) {
			throw new IllegalArgumentException("별점은 " + MIN_SCORE + "~" + MAX_SCORE + " 사이여야 합니다: " + score);
		}
		return (short) score;
	}

	public Key getId() {
		return this.id;
	}

	public int getScore() {
		return this.score;
	}

	/** (여행, 사람). */
	@Embeddable
	public static class Key implements Serializable {

		@Column(name = "trip_id", nullable = false, updatable = false)
		private UUID tripId;

		@Column(name = "user_id", nullable = false, updatable = false)
		private UUID userId;

		protected Key() {
			// JPA 전용
		}

		public Key(UUID tripId, UUID userId) {
			this.tripId = tripId;
			this.userId = userId;
		}

		public UUID getTripId() {
			return this.tripId;
		}

		public UUID getUserId() {
			return this.userId;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && Objects.equals(this.tripId, k.tripId) && Objects.equals(this.userId, k.userId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.tripId, this.userId);
		}
	}
}
