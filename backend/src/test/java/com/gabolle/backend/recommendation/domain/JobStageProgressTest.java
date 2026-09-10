package com.gabolle.backend.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 진행률이 오르는 규칙 — S15P21E201-193.
 *
 * <p>화면에 밀어 보낼 값이 여기서 정해진다. DB 도 Spring 도 필요 없는 규칙이라 그것들 없이
 * 잰다.
 */
class JobStageProgressTest {

	private RecommendationJob newJob() {
		return RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, OffsetDateTime.now());
	}

	@Test
	@DisplayName("파이프라인이 실제로 지나가는 순서대로 진행률이 오른다")
	void percentRisesAlongThePipelineOrder() {
		// 🔴 선언 순서가 아니라 RecommendationService.continueJob 이 지나가는 순서다.
		//    후보 생성이 버전 확인보다 앞이다 — 그 반대로 매기면 진행률이 내려간다.
		assertThat(JobStage.CREATED.percent())
				.isLessThan(JobStage.CANDIDATE_GENERATION.percent());
		assertThat(JobStage.CANDIDATE_GENERATION.percent())
				.isLessThan(JobStage.VERSION_RESOLUTION.percent());
		assertThat(JobStage.VERSION_RESOLUTION.percent())
				.isLessThan(JobStage.RANKING.percent());
		assertThat(JobStage.RANKING.percent())
				.isLessThan(JobStage.ROUTE_OPTIMIZATION.percent());
		assertThat(JobStage.ROUTE_OPTIMIZATION.percent())
				.isLessThan(JobStage.COMPLETED.percent());
		assertThat(JobStage.COMPLETED.percent()).isEqualTo(100);
	}

	@Test
	@DisplayName("실패 지점으로만 쓰이는 단계도 앞뒤 사이에 있다")
	void failureOnlyStagesSitBetweenTheirNeighbours() {
		// 지금은 진행률 단계로 지나가지 않지만, 나중에 지나가게 되어도 순서가 깨지지 않아야 한다.
		assertThat(JobStage.CONSTRAINT_EVALUATION.percent())
				.isBetween(JobStage.VERSION_RESOLUTION.percent(), JobStage.RANKING.percent());
		assertThat(JobStage.FEATURE_LOOKUP.percent())
				.isBetween(JobStage.CONSTRAINT_EVALUATION.percent(), JobStage.RANKING.percent());
		assertThat(JobStage.PERSISTENCE.percent())
				.isBetween(JobStage.ROUTE_OPTIMIZATION.percent(), JobStage.COMPLETED.percent());
	}

	@Test
	@DisplayName("시작하면 첫 단계의 진행률이 함께 들어간다")
	void markRunningSetsThePercentOfItsStage() {
		RecommendationJob job = newJob();

		job.markRunning(JobStage.CANDIDATE_GENERATION);

		assertThat(job.getJobStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(job.getJobStage()).isEqualTo(JobStage.CANDIDATE_GENERATION);
		assertThat(job.getProgressPercent()).isEqualTo(JobStage.CANDIDATE_GENERATION.percent());
	}

	@Test
	@DisplayName("단계가 넘어가면 진행률이 그 단계의 값으로 오른다")
	void markStageRaisesThePercent() {
		RecommendationJob job = newJob();
		job.markRunning(JobStage.CANDIDATE_GENERATION);

		job.markStage(JobStage.RANKING);

		assertThat(job.getJobStage()).isEqualTo(JobStage.RANKING);
		assertThat(job.getProgressPercent()).isEqualTo(JobStage.RANKING.percent());
	}

	@Test
	@DisplayName("진행률은 뒤로 가지 않는다 — 단계 이름만 바뀐다")
	void markStageNeverLowersThePercent() {
		RecommendationJob job = newJob();
		job.markRunning(JobStage.CANDIDATE_GENERATION);
		job.markStage(JobStage.ROUTE_OPTIMIZATION);

		// 앞 단계를 다시 보고했다 — 화면에서 진행률이 내려가면 사용자는 고장으로 읽는다.
		job.markStage(JobStage.VERSION_RESOLUTION);

		assertThat(job.getJobStage()).isEqualTo(JobStage.VERSION_RESOLUTION);
		assertThat(job.getProgressPercent()).isEqualTo(JobStage.ROUTE_OPTIMIZATION.percent());
	}

	@Test
	@DisplayName("끝난 작업에는 단계 보고가 아무것도 하지 않는다")
	void markStageIsIgnoredAfterTheJobEnded() {
		RecommendationJob job = newJob();
		job.markRunning(JobStage.CANDIDATE_GENERATION);
		job.markFailed("ENGINE_UNAVAILABLE", JobStage.CANDIDATE_GENERATION, OffsetDateTime.now(), false, true);

		job.markStage(JobStage.COMPLETED);

		assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getProgressPercent()).isNotEqualTo(100);
	}

	@Test
	@DisplayName("끝 상태는 더 바뀌지 않는 상태로 판정된다")
	void terminalStatusesAreRecognised() {
		assertThat(JobStatus.SUCCEEDED.isTerminal()).isTrue();
		assertThat(JobStatus.FAILED.isTerminal()).isTrue();
		assertThat(JobStatus.CANCELLED.isTerminal()).isTrue();
		assertThat(JobStatus.EXPIRED.isTerminal()).isTrue();
		assertThat(JobStatus.PENDING.isTerminal()).isFalse();
		assertThat(JobStatus.RUNNING.isTerminal()).isFalse();
	}
}
