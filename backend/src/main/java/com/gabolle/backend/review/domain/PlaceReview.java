package com.gabolle.backend.review.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장소 하나에 대한 한 사람의 평가.
 *
 * 인증하지 않은 사람의 평가도 저장한다. 막으면 위치 권한을 사실상 강제하게 된다. 대신
 * {@link #verified} 가 거짓이고 로컬 점수 계산에서 빠진다.
 *
 * 좌표는 담지 않고 {@link #region} 에 지역 단위 문자열만 남긴다. 정확한 좌표가 시각과 함께
 * 쌓이면 그 사람의 하루 동선이 복원된다.
 *
 * 네 점수는 매기지 않으면 {@code null} 이다. 0 으로 두면 최하점과 구분이 안 되고 평균을
 * 끌어내린다.
 */
@Entity
@Table(name = "place_review")
public class PlaceReview {

	@Id
	@Column(name = "place_review_id", nullable = false, updatable = false)
	private UUID placeReviewId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "food_score")
	private Short foodScore;

	@Column(name = "price_score")
	private Short priceScore;

	@Column(name = "accessibility_score")
	private Short accessibilityScore;

	@Column(name = "onsite_score")
	private Short onsiteScore;

	@Column(name = "body", length = 1000)
	private String body;

	@Column(name = "verified", nullable = false)
	private boolean verified;

	@Column(name = "region", length = 100)
	private String region;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected PlaceReview() {
	}

	/**
	 * @param verified 요청에서 받지 않는다. 서버가 인증 기록을 조회해 정한 값만 넘겨야 한다
	 */
	public static PlaceReview write(UUID placeId, UUID userId, Scores scores, String body, boolean verified,
			String region, Instant now) {
		if (placeId == null || userId == null) {
			throw new IllegalArgumentException("평가에 필요한 값이 없습니다.");
		}
		if (scores == null || (scores.isEmpty() && (body == null || body.isBlank()))) {
			throw new EmptyReviewException();
		}
		PlaceReview review = new PlaceReview();
		review.placeReviewId = UUID.randomUUID();
		review.placeId = placeId;
		review.userId = userId;
		review.apply(scores, body, verified, region, now);
		review.createdAt = now;
		return review;
	}

	/** 다시 쓰면 덮어쓴다. 표의 UNIQUE(place_id, user_id) 와 짝이다. */
	public void rewrite(Scores scores, String body, boolean verified, String region, Instant now) {
		if (scores == null || (scores.isEmpty() && (body == null || body.isBlank()))) {
			throw new EmptyReviewException();
		}
		apply(scores, body, verified, region, now);
	}

	private void apply(Scores scores, String body, boolean verified, String region, Instant now) {
		this.foodScore = scores.food();
		this.priceScore = scores.price();
		this.accessibilityScore = scores.accessibility();
		this.onsiteScore = scores.onsite();
		this.body = body;
		this.verified = verified;
		this.region = region;
		this.updatedAt = now;
	}

	/**
	 * 매기지 않은 항목은 {@code null} 이다. 표의 CHECK 와 중복이지만 범위 검사를 여기서도 한다 —
	 * DB 까지 가서 실패하면 어느 항목이 잘못됐는지 응답에 담기 어렵다.
	 */
	public record Scores(Short food, Short price, Short accessibility, Short onsite) {

		public Scores {
			require(food, "food");
			require(price, "price");
			require(accessibility, "accessibility");
			require(onsite, "onsite");
		}

		public boolean isEmpty() {
			return food == null && price == null && accessibility == null && onsite == null;
		}

		private static void require(Short value, String field) {
			if (value != null && (value < 1 || value > 5)) {
				throw new ScoreOutOfRangeException(field, value);
			}
		}
	}

	/** 400 으로 나간다. */
	public static class ScoreOutOfRangeException extends RuntimeException {

		private final String field;

		public ScoreOutOfRangeException(String field, short value) {
			super("평가 점수는 1에서 5 사이여야 합니다: " + field + "=" + value);
			this.field = field;
		}

		public String field() {
			return this.field;
		}
	}

	/** 400 으로 나간다. */
	public static class EmptyReviewException extends RuntimeException {

		public EmptyReviewException() {
			super("점수나 내용 중 하나는 있어야 합니다.");
		}
	}

	public UUID getPlaceReviewId() {
		return this.placeReviewId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public Short getFoodScore() {
		return this.foodScore;
	}

	public Short getPriceScore() {
		return this.priceScore;
	}

	public Short getAccessibilityScore() {
		return this.accessibilityScore;
	}

	public Short getOnsiteScore() {
		return this.onsiteScore;
	}

	public String getBody() {
		return this.body;
	}

	public boolean isVerified() {
		return this.verified;
	}

	public String getRegion() {
		return this.region;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public Instant getDeletedAt() {
		return this.deletedAt;
	}
}
