package com.gabolle.backend.recommendation.presentation.dto;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.recommendation.domain.JobStage;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

/** 추천 Job 상태 응답. Job 생성 202 응답과 Job 조회 200 응답이 함께 쓴다. */
public record RecommendationJobResponse(
		UUID jobId,
		/** 이 추천 요청의 정본 키. 앱이 노출·저장·제외 이벤트에 그대로 실어 보낸다. */
		UUID requestId,
		JobType type,
		JobStatus status,
		Progress progress,
		Failure failure,
		boolean retryable,
		Integer pollAfterSeconds) {

	public record Progress(JobStage stage, int percent) {
	}

	/**
	 * @param detail 지금은 failureStage 이름을 그대로 보여준다. 설명 문구 사전이 아직 없다
	 * @param blockedBy <b>어느 조건이 후보를 다 걷어냈나</b>. 막은 후보가 많은 것부터 온다.
	 *     설명할 수 없는 실패면 <b>빈 목록</b>이고, 그때 화면은 예전처럼 갈래를 뭉뚱그려
	 *     말한다 — 지어낸 조건을 넣지 않는다 (S15P21E201-1468)
	 */
	public record Failure(String code, String detail, List<BlockedBy> blockedBy) {
	}

	/**
	 * 후보를 막은 조건 하나. 이 기록이 나가는 모양 그대로다.
	 *
	 * <p>응용 계층의 {@code BlockingConstraintAnalyzer.Blocking} 을 그대로 내보내지 않고 여기
	 * 한 벌을 더 두는 것은, 이 기록이 <b>앱과의 약속</b>이기 때문이다. 안쪽 타입을 그대로
	 * 실으면 계산 쪽 사정으로 칸 이름을 바꾸는 순간 앱이 조용히 깨진다.
	 *
	 * @param constraintType {@code ALLERGY}·{@code DIET}·{@code MOBILITY}. 모르는 코드면
	 *     {@code null} — 지어내지 않는다
	 * @param constraintKey 사용자가 고른 값. 예: {@code PEANUT}. 갈래 전체가 막혔으면 {@code null}
	 * @param reason {@code UNVERIFIED} 확인 못 함 · {@code VIOLATED} 확인했고 안 됨.
	 *     <b>둘을 같은 말로 쓰면 안 된다</b> — 지금 나오는 것은 대부분 앞쪽이고, 뒤쪽으로
	 *     적으면 사용자는 부산에 자기가 먹을 것이 없다고 읽는다
	 * @param code 걸린 원래 코드. 갈래를 모르는 값이 와도 잃지 않게 그대로 싣는다
	 * @param blockedCandidates 이 조건이 막은 후보 수
	 */
	public record BlockedBy(String constraintType, String constraintKey, String reason, String code,
			int blockedCandidates) {
	}

	/**
	 * 막은 조건을 모르는 채로 만든다. 작업을 갓 받은 202 응답처럼 아직 후보가 없는 자리가
	 * 쓴다 — 그 자리에서 빈 목록은 "없다" 가 아니라 "아직 물어볼 것이 없다" 다.
	 */
	public static RecommendationJobResponse of(RecommendationJob job) {
		return of(job, List.of());
	}

	/**
	 * @param blockedBy 막은 조건. {@code BlockingConstraintAnalyzer} 가 낸 것을 부르는 쪽이
	 *     이 기록으로 옮겨 넘긴다 — 이 자리가 응용 계층 타입을 직접 물면, 계산 쪽 사정으로
	 *     칸 이름을 바꾸는 순간 앱과의 약속이 조용히 따라 바뀐다. 실패가 아니면 쓰이지 않는다
	 */
	public static RecommendationJobResponse of(RecommendationJob job, List<BlockedBy> blockedBy) {
		Failure failure = (job.getJobStatus() == JobStatus.FAILED)
				? new Failure(job.getErrorCode(), job.getFailureStage() == null ? null : job.getFailureStage().name(),
						(blockedBy == null) ? List.of() : List.copyOf(blockedBy))
				: null;
		return new RecommendationJobResponse(
				job.getJobId(), job.getRequestId(), job.getJobType(), job.getJobStatus(),
				new Progress(job.getJobStage(), job.getProgressPercent()),
				failure, job.isRetryable(), job.getPollAfterSeconds());
	}
}
