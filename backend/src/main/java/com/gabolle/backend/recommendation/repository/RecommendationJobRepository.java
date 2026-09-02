package com.gabolle.backend.recommendation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gabolle.backend.recommendation.domain.RecommendationJob;

public interface RecommendationJobRepository extends JpaRepository<RecommendationJob, UUID> {

	/** 분석은 언제나 request_id 에서 시작한다. */
	Optional<RecommendationJob> findByRequestId(UUID requestId);

	boolean existsByRequestId(UUID requestId);
}
