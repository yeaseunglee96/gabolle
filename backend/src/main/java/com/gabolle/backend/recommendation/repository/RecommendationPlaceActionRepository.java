package com.gabolle.backend.recommendation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;

public interface RecommendationPlaceActionRepository extends JpaRepository<RecommendationPlaceAction, UUID> {

	/** 이 여행의 판단 전부. 후보를 많이 돌려 본 여행일수록 무거워져 상한을 받는다. */
	List<RecommendationPlaceAction> findByTripId(UUID tripId, Pageable pageable);

	Optional<RecommendationPlaceAction> findByTripIdAndPlaceId(UUID tripId, UUID placeId);

	void deleteByTripIdAndPlaceId(UUID tripId, UUID placeId);

	/**
	 * 판단을 적는다. 이미 적혀 있으면 덮어쓴다. 찾아보고 없으면 넣는 방식은 두 요청 사이가
	 * 벌어져, 동행자 둘이 같은 후보를 동시에 누르면 {@code uk_recommendation_place_action}
	 * 에 걸린 쪽이 500 이 된다 — 동행자가 함께 보는 자리라 동시에 눌리는 것이 정상이다.
	 *
	 * 이 표의 뜻은 이 여행에서 이 장소를 어떻게 하기로 했나 하나뿐이라 마지막 판단이 곧
	 * 지금의 판단이다. {@code created_at} 은 덮어쓰지 않는다.
	 */
	// @Modifying 질의는 트랜잭션을 요구하는데 Spring Data 는 기본 CRUD 에만 걸어 주므로 여기
	// 직접 붙인다. 운영에서는 부모 트랜잭션에 합류하므로(REQUIRED) 동작이 달라지지 않고,
	// 리포지토리를 곧장 부르는 시험에서만 차이가 난다.
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO recommendation_place_action (
				recommendation_place_action_id, trip_id, place_id, action, decided_by_user_id,
				created_at, updated_at)
			VALUES (:actionId, :tripId, :placeId, :action, :decidedBy, :now, :now)
			ON CONFLICT (trip_id, place_id) DO UPDATE SET
				action             = EXCLUDED.action,
				decided_by_user_id = EXCLUDED.decided_by_user_id,
				updated_at         = EXCLUDED.updated_at
			""", nativeQuery = true)
	int upsert(@Param("actionId") UUID actionId, @Param("tripId") UUID tripId, @Param("placeId") UUID placeId,
			@Param("action") String action, @Param("decidedBy") UUID decidedBy,
			@Param("now") OffsetDateTime now);
}
