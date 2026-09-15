package com.gabolle.backend.recommendation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;

public interface RecommendationPlaceActionRepository extends JpaRepository<RecommendationPlaceAction, UUID> {

	/**
	 * 이 여행에서 내가 내린 판단 전부 — 화면이 다시 열릴 때 하트와 가림 표시를 되살린다.
	 *
	 * <p>상한을 두지 않는다. 한 사람이 한 여행에서 판단할 수 있는 장소 수는 <b>그 여행의
	 * 추천 후보 수</b>가 상한이라 저절로 묶여 있다 — 끝없이 자라는 목록이 아니다
	 * (S15P21E201-1011 이 상한을 붙인 목록들과 그 점이 다르다).
	 */
	List<RecommendationPlaceAction> findByUserIdAndTripId(UUID userId, UUID tripId);

	Optional<RecommendationPlaceAction> findByUserIdAndTripIdAndPlaceId(UUID userId, UUID tripId, UUID placeId);

	void deleteByUserIdAndTripIdAndPlaceId(UUID userId, UUID tripId, UUID placeId);
}
