package com.gabolle.backend.review.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.review.domain.PlaceReview;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, UUID> {

	Optional<PlaceReview> findByPlaceIdAndUserIdAndDeletedAtIsNull(UUID placeId, UUID userId);

	/** 최신순. 인증 여부와 무관하게 다 돌려준다. */
	@Query("""
			SELECT r FROM PlaceReview r
			WHERE r.placeId = :placeId AND r.deletedAt IS NULL
			ORDER BY r.updatedAt DESC, r.placeReviewId DESC
			""")
	List<PlaceReview> findByPlaceId(@Param("placeId") UUID placeId, Limit limit);

	/**
	 * 점수 계산이 읽는 것. 미인증 리뷰를 섞으면 위치 권한 없이도 점수를 움직일 수 있게 되므로
	 * 목록 조회와 일부러 다른 메서드로 둔다.
	 */
	@Query("""
			SELECT r FROM PlaceReview r
			WHERE r.placeId = :placeId AND r.verified = true AND r.deletedAt IS NULL
			""")
	List<PlaceReview> findVerifiedByPlaceId(@Param("placeId") UUID placeId);
}
