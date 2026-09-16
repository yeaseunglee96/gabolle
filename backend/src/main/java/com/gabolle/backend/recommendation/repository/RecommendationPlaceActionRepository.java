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

import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;

public interface RecommendationPlaceActionRepository extends JpaRepository<RecommendationPlaceAction, UUID> {

	/**
	 * 이 여행의 판단 전부. 🔴 {@code Pageable} 로 <b>상한을 받는다</b> (S15P21E201-1037) —
	 * 그전에는 전부 돌려줬다. 후보를 많이 돌려 본 여행일수록 이 조회만 무거워진다.
	 */
	List<RecommendationPlaceAction> findByTripId(UUID tripId, Pageable pageable);

	Optional<RecommendationPlaceAction> findByTripIdAndPlaceId(UUID tripId, UUID placeId);

	void deleteByTripIdAndPlaceId(UUID tripId, UUID placeId);

	/**
	 * 판단을 적는다. 이미 적혀 있으면 <b>덮어쓴다</b> — S15P21E201-1037.
	 *
	 * <h2>🔴 왜 「찾아보고 없으면 넣는다」가 아닌가</h2>
	 *
	 * 그 방식은 두 요청 사이가 벌어진다. 동행자 둘이 같은 후보를 동시에 누르면 둘 다
	 * {@code findByTripIdAndPlaceId} 에서 빈 값을 받고 둘 다 넣으려 하며,
	 * {@code uk_recommendation_place_action} 에 걸린 쪽이 500 이 된다. 이 경로는 동행자가
	 * 함께 보는 자리라 동시에 눌리는 것이 예외가 아니라 정상이다.
	 *
	 * <p>덮어쓰기가 맞는 이유 — 이 표의 뜻은 「이 여행에서 이 장소를 어떻게 하기로 했나」
	 * 하나뿐이고, 마지막에 누른 사람의 판단이 지금의 판단이다. 누가 눌렀는지는
	 * {@code decided_by_user_id} 에 함께 갱신해서 남긴다.
	 *
	 * <p>{@code created_at} 은 덮어쓰지 않는다 — 처음 판단한 시각은 바뀌지 않는다.
	 */
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
