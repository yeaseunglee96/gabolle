package com.gabolle.backend.review.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 위치로 확인한 방문.
 *
 * 좌표를 담는 칸이 의도적으로 없다. 인증에 쓴 위치를 쌓으면 그 사람이 언제 어디 있었는지의
 * 기록이 되는데, 인증은 "가까이 있었다" 는 판정만 필요하고 그 판정은 요청 처리 중에 끝난다.
 *
 * {@link #distanceM} 은 좌표가 아니라 판정 결과라 남긴다 — 이 값 하나로는 위치를 복원할 수
 * 없고, 나중에 인증 기준이 느슨했는지 볼 수 있어야 한다.
 */
@Entity
@Table(name = "place_visit_verification")
public class PlaceVisitVerification {

	@Id
	@Column(name = "place_visit_verification_id", nullable = false, updatable = false)
	private UUID placeVisitVerificationId;

	@Column(name = "place_id", nullable = false, updatable = false)
	private UUID placeId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "verified_at", nullable = false, updatable = false)
	private Instant verifiedAt;

	@Column(name = "distance_m", nullable = false, updatable = false)
	private int distanceM;

	protected PlaceVisitVerification() {
	}

	private PlaceVisitVerification(UUID id, UUID placeId, UUID userId, Instant verifiedAt, int distanceM) {
		this.placeVisitVerificationId = id;
		this.placeId = placeId;
		this.userId = userId;
		this.verifiedAt = verifiedAt;
		this.distanceM = distanceM;
	}

	/**
	 * 좌표를 인자로도 받지 않는다. 받아서 안 쓰는 것보다 받지 않는 편이 나중에 로그로 새는 자리를
	 * 없앤다.
	 */
	public static PlaceVisitVerification record(UUID placeId, UUID userId, int distanceM, Instant now) {
		if (placeId == null || userId == null) {
			throw new IllegalArgumentException("인증에 필요한 값이 없습니다.");
		}
		if (distanceM < 0) {
			throw new IllegalArgumentException("거리는 음수일 수 없습니다: " + distanceM);
		}
		return new PlaceVisitVerification(UUID.randomUUID(), placeId, userId, now, distanceM);
	}

	public UUID getPlaceVisitVerificationId() {
		return this.placeVisitVerificationId;
	}

	public UUID getPlaceId() {
		return this.placeId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public Instant getVerifiedAt() {
		return this.verifiedAt;
	}

	public int getDistanceM() {
		return this.distanceM;
	}
}
