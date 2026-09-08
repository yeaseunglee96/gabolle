package com.gabolle.backend.review.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 위치로 확인한 방문 — S15P21E201-279.
 *
 * <h2>🔴 좌표를 담는 칸이 없다</h2>
 * 이것이 이 클래스의 가장 중요한 성질이고 티켓 완료 기준이 "인증 뒤 데이터베이스 어디에도
 * 좌표 값이 없다" 다.
 *
 * <p>인증하려고 보낸 위치를 쌓으면 <b>그 사람이 언제 어디 있었는지의 기록</b>이 된다. 인증은
 * "가까이 있었다" 는 판정만 필요하고 그 판정은 요청 처리 중에 끝난다. 그래서 좌표는 받아서
 * 쓰고 버린다 — <b>칸이 없는 것이 그 약속의 구현</b>이다. 기록 표({@code story})가 좌표 칸
 * 없이 지역 문자열만 두는 것과 같은 판단이다.
 *
 * <p>{@link #distanceM} 은 남긴다. 그건 좌표가 아니라 <b>판정 결과</b>이고, 이 값 하나로는
 * 위치를 복원할 수 없다. 남기는 이유는 나중에 "인증 기준이 너무 느슨했나" 를 볼 수 있어야
 * 하기 때문이다.
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
	 * 인증을 남긴다.
	 *
	 * <p>🔴 좌표를 <b>인자로도 받지 않는다.</b> 받아서 안 쓰는 것보다 받지 않는 편이 낫다 —
	 * 나중에 누가 "이 값 어차피 넘어오는데 로그에 찍어 두자" 를 하는 자리가 없어진다.
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
