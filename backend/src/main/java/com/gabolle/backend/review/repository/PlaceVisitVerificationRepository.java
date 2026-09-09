package com.gabolle.backend.review.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.review.domain.PlaceVisitVerification;

public interface PlaceVisitVerificationRepository extends JpaRepository<PlaceVisitVerification, UUID> {

	/**
	 * 이 사람이 이 장소를 인증했는가 — 리뷰 저장이 이것으로 인증 여부를 가른다 (S15P21E201-287).
	 *
	 * <p>🔴 가장 최근 인증만 보지 않고 <b>있는지만</b> 본다. "언제 인증했는지" 로 유효기간을
	 * 두면 그 기간을 얼마로 할지가 새 결정이 되고, 지금 그것을 정할 근거가 없다. 필요해지면
	 * 그때 이 메서드 옆에 기간을 받는 것을 하나 더 만든다.
	 */
	boolean existsByUserIdAndPlaceId(UUID userId, UUID placeId);
}
