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

	/** 다시 쓸 때 기존 것을 찾아 덮어쓴다. */
	Optional<PlaceReview> findByPlaceIdAndUserIdAndDeletedAtIsNull(UUID placeId, UUID userId);

	/** 그 장소의 리뷰 목록. 최신순. 인증 여부와 무관하게 다 보여준다. */
	@Query("""
			SELECT r FROM PlaceReview r
			WHERE r.placeId = :placeId AND r.deletedAt IS NULL
			ORDER BY r.updatedAt DESC, r.placeReviewId DESC
			""")
	List<PlaceReview> findByPlaceId(@Param("placeId") UUID placeId, Limit limit);

	/**
	 * 로컬 점수 계산이 읽는 것 — <b>인증된 리뷰만</b> (S15P21E201-287 · -408).
	 *
	 * <p>🔴 이 메서드가 미인증 리뷰를 섞으면 위치 권한 없이도 점수를 움직일 수 있게 되고,
	 * 인증을 만든 의미가 사라진다. 이름에 {@code Verified} 를 박아 둔 이유다 — 목록 조회와
	 * 점수 계산이 같은 메서드를 쓰지 않게 한다.
	 */
	@Query("""
			SELECT r FROM PlaceReview r
			WHERE r.placeId = :placeId AND r.verified = true AND r.deletedAt IS NULL
			""")
	List<PlaceReview> findVerifiedByPlaceId(@Param("placeId") UUID placeId);
}
