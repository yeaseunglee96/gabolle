package com.gabolle.backend.recommendation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.recommendation.domain.RecommendationCandidate;

public interface RecommendationCandidateRepository extends JpaRepository<RecommendationCandidate, UUID> {

	/**
	 * 한 요청의 후보 전부 — 탈락한 것 포함. final_rank 가 없는(순위를 못 받은) 후보는
	 * PostgreSQL 의 ASC 기본 동작대로 뒤에 온다.
	 */
	List<RecommendationCandidate> findByRequestIdOrderByFinalRankAscPlaceIdAsc(UUID requestId);

	List<RecommendationCandidate> findByRequestIdAndReturnedTrueOrderByFinalRankAsc(UUID requestId);

	long countByRequestId(UUID requestId);
}
