package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.ImpressionFacts;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 노출 이벤트에 채울 사실을 추천이 적어 둔 행에서 읽는다 (S15P21E201-1689) — 순위·이유 코드는 {@code recommendation_candidate},
 * 판·대체 방식은 {@code recommendation_job}. 둘 다 추천을 만들 때 이미 적힌다.
 */
@Component
@Profile({ "db", "dev" })
public class CandidateImpressionFacts implements ImpressionFacts {

	private final RecommendationCandidateRepository candidates;

	private final RecommendationJobRepository jobs;

	public CandidateImpressionFacts(RecommendationCandidateRepository candidates, RecommendationJobRepository jobs) {
		this.candidates = candidates;
		this.jobs = jobs;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Facts> lookup(UUID requestId, UUID placeId) {
		Optional<RecommendationCandidate> candidate = this.candidates.findFirstByRequestIdAndPlaceId(requestId, placeId);
		if (candidate.isEmpty()) {
			return Optional.empty();
		}
		RecommendationCandidate c = candidate.get();
		Optional<RecommendationJob> job = this.jobs.findByRequestId(requestId);
		return Optional.of(new Facts(c.getFinalRank(),
				c.getReasonCodes() == null ? List.of() : List.of(c.getReasonCodes()),
				job.map(RecommendationJob::getFallbackMode).map(Enum::name).orElse(null),
				job.map(RecommendationJob::getModelVersion).orElse(null),
				job.map(RecommendationJob::getFeatureVersion).orElse(null),
				job.map(RecommendationJob::getOntologyVersion).orElse(null),
				job.map(RecommendationJob::getDatasetVersion).orElse(null),
				job.map(RecommendationJob::getPolicyVersion).orElse(null)));
	}
}
