package com.gabolle.backend.recommendation.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.SourceMode;

/**
 * 추천 결과 — 응용 계층 내부용이고 공개 DTO 가 아니다. 여기서는 내부 점수를 들고 있고 공개
 * 계층이 그것을 떼고 내보낸다.
 *
 * {@code requestId} 는 노출 이벤트({@code recommendation_impression})가 되돌려 보내야 후보
 * 행과 이어지는 값이다 — 이것이 없는 반응은 추천 성과 분석에서 버려진다.
 *
 * @param sourceMode 개인화 추천인가 Editor's Pick 인가({@link SourceMode}).
 *     {@code fallbackMode} 와 다른 질문에 답하며, 분석용이라 공개 응답에는 나가지 않는다
 */
public record RecommendationResult(
		UUID requestId,
		UUID jobId,
		JobType jobType,
		JobStatus jobStatus,
		OffsetDateTime generatedAt,
		List<RecommendedPlace> items,
		int generatedCandidateCount,
		int eligibleCandidateCount,
		int returnedCandidateCount,
		FallbackMode fallbackMode,
		String fallbackReason,
		SourceMode sourceMode,
		String modelVersion,
		String featureVersion,
		String ontologyVersion,
		String policyVersion,
		String datasetVersion,
		String serviceVersion,
		String deploymentEnvironment) {

	public RecommendationResult {
		items = (items == null) ? List.of() : List.copyOf(items);
	}
}
