package com.gabolle.backend.review.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.review.domain.PlaceVisitVerification;

public interface PlaceVisitVerificationRepository extends JpaRepository<PlaceVisitVerification, UUID> {

	/**
	 * 인증 기록이 있는지만 보고 시점은 따지지 않는다 — 유효기간을 두려면 그 기간을 얼마로 할지
	 * 정해야 하는데 지금 근거가 없다. 필요해지면 기간을 받는 메서드를 따로 만든다.
	 */
	boolean existsByUserIdAndPlaceId(UUID userId, UUID placeId);
}
