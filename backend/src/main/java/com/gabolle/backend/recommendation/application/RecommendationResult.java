package com.gabolle.backend.recommendation.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.SourceMode;

/**
 * 추천 결과 — <b>응용 계층 내부용</b>이다.
 *
 * <p>🔴 이것은 공개 DTO 가 아니다. GB-API-001 6장이 <i>"Python 응답의 scoreInternal 은 Spring
 * 저장·분석에는 사용할 수 있으나 <b>공개 DTO 에서 제거한다</b>"</i> 라고 정하므로, 여기서는
 * 점수를 들고 있고 공개 계층(REC-01 · REC-04)이 그것을 떼고 내보낸다.
 *
 * <p>🔴 {@code requestId} 가 결과에 들어가는 것이 이 티켓의 핵심이다. S15P21E201-544 의 실제
 * 노출 이벤트({@code recommendation_impression})가 이 값을 되돌려 보내야 후보 행과 이어진다.
 * requestId 없는 반응은 추천 성과 분석에서 버려진다 (API-07).
 *
 * @param requestId 이 요청의 정본 키
 * @param jobId 추천 작업 ID
 * @param jobType 무엇을 한 Job 이었나
 * @param jobStatus 처리 결과 상태
 * @param generatedAt 결과 생성 시각
 * @param items 실제로 반환된 추천 목록
 * @param generatedCandidateCount 만들어진 후보 전체 수
 * @param eligibleCandidateCount 랭킹 대상이 된 후보 수
 * @param returnedCandidateCount 실제로 반환된 후보 수
 * @param fallbackMode 무엇이 순위를 매겼는가
 * @param fallbackReason 정상 경로를 못 쓴 이유
 * @param sourceMode 개인화 추천인가 Editor's Pick 인가 (S15P21E201-555). 🔴
 *     {@code fallbackMode} 와 다른 질문에 답한다 — 자세한 것은 {@link SourceMode}.
 *     <b>공개 응답에는 나가지 않는다</b>: {@code RecommendationResultResponse} 는 "필드를
 *     추가·삭제하지 않는다" 를 원칙으로 두고 있고, -555 의 완료 기준도 "<b>분석에서</b>
 *     구분할 수 있다" 라서 {@code recommendation_job}·{@code recommendation_candidate} 의
 *     {@code source_mode} 칸으로 충족된다. 화면이 이 값을 요청하면 그때 넣는다
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
