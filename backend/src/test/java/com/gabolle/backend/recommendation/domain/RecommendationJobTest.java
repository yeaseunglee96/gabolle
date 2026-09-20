package com.gabolle.backend.recommendation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Job 하나가 스스로 지키는 것들. 도커 없이 도는 단위 테스트다. */
class RecommendationJobTest {

	private final OffsetDateTime now = OffsetDateTime.now();

	@Test
	@DisplayName("🔴 원인 없이 실패로 남길 수 없다")
	void failureAlwaysCarriesAReason() {
		RecommendationJob job = newJob();

		assertThatThrownBy(() -> job.markFailed(" ", JobStage.RANKING, this.now, false, false))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("jobType 없이 Job 을 시작할 수 없다 — 공개 JobDto 가 요구하는 값이다")
	void jobTypeIsRequired() {
		assertThatThrownBy(() -> RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), null, this.now))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("🔴 대체 경로로 만들어도 상태는 SUCCEEDED 다 — fallback 은 상태가 아니라 모드다")
	void fallbackIsAModeNotAStatus() {
		RecommendationJob job = newJob();
		job.applyVersions("m", "f", "o", "p", "d", "s", "test");
		job.applyCounts(3, 3, 1);
		job.markCompleted(this.now, this.now, FallbackMode.BASELINE, "MODEL_TIMEOUT");

		assertThat(job.getJobStatus()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job.getFallbackMode()).isEqualTo(FallbackMode.BASELINE);
		assertThat(job.getFallbackReason()).isEqualTo("MODEL_TIMEOUT");
		assertThat(job.getProgressPercent()).isEqualTo(100);
	}

	@Test
	@DisplayName("일정에 붙은 Job 은 resource 가 ITINERARY, 여행에만 붙으면 TRIP 이다")
	void resourceFollowsWhatTheJobIsAttachedTo() {
		UUID tripId = UUID.randomUUID();
		UUID itineraryId = UUID.randomUUID();

		RecommendationJob tripJob = newJob();
		tripJob.applyRequestContext(tripId, 1, null, null, null, null, null, null);
		assertThat(tripJob.getResourceType()).isEqualTo(ResourceType.TRIP);
		assertThat(tripJob.getResourceId()).isEqualTo(tripId);

		RecommendationJob itineraryJob = newJob();
		itineraryJob.applyRequestContext(tripId, 1, null, null, itineraryId, 4, 4, null);
		assertThat(itineraryJob.getResourceType()).isEqualTo(ResourceType.ITINERARY);
		assertThat(itineraryJob.getResourceId()).isEqualTo(itineraryId);
		assertThat(itineraryJob.getBaseVersion()).isEqualTo(4);
	}

	@Test
	@DisplayName("여행에도 일정에도 안 붙은 Job 은 resource 를 비워 둔다 — 없는 값을 지어내지 않는다")
	void aResourcelessJobLeavesTheFieldsEmpty() {
		RecommendationJob job = newJob();
		job.applyRequestContext(null, null, null, null, null, null, null, "app-1.0.0");

		assertThat(job.getResourceType()).isNull();
		assertThat(job.getResourceId()).isNull();
	}

	@Test
	@DisplayName("재시도해도 소용없는 실패는 retryable=false 로 남는다")
	void retryabilityIsRecorded() {
		RecommendationJob job = newJob();
		job.markFailed("VERSION_UNRESOLVED", JobStage.VERSION_RESOLUTION, this.now, false, false);

		assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.isRetryable()).isFalse();
	}

	@Test
	@DisplayName("S15P21E201-192 — 새 Job 은 PENDING 으로 시작하고, RUNNING 으로 넘어가면 단계가 함께 남는다")
	void newJobIsPendingUntilRunningIsMarked() {
		RecommendationJob job = newJob();
		assertThat(job.getJobStatus()).isEqualTo(JobStatus.PENDING);

		job.markRunning(JobStage.CANDIDATE_GENERATION);

		assertThat(job.getJobStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(job.getJobStage()).isEqualTo(JobStage.CANDIDATE_GENERATION);
	}

	@Test
	@DisplayName("🔴 PENDING 이 아닌 Job 은 다시 RUNNING 으로 못 간다 — 이미 끝난 계산을 또 돌리지 않는다")
	void runningCanOnlyBeReachedFromPending() {
		RecommendationJob job = newJob();
		job.markRunning(JobStage.CANDIDATE_GENERATION);

		assertThatThrownBy(() -> job.markRunning(JobStage.RANKING))
				.isInstanceOf(IllegalStateException.class);
	}

	/**
	 * {@code ITINERARY_GENERATION} 뿐 아니라 {@code ITEM_REMOVE} 도 같은 검사를 받는다 —
	 * 제외는 성공했는데 그 결과가 어느 판인지 안 남은 상태는 같은 종류의 결함이다.
	 */
	@Test
	@DisplayName("🔴 SUCCEEDED 인 ITEM_REMOVE Job 이 itinerary 없이 assertItineraryAttachedIfRequired 를 부르면 예외")
	void itemRemoveAlsoRequiresItineraryWhenSucceeded() {
		RecommendationJob job = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				JobType.ITEM_REMOVE, this.now);
		job.applyCounts(1, 1, 1);
		job.markCompleted(this.now, this.now, null, null);

		assertThatThrownBy(job::assertItineraryAttachedIfRequired)
				.isInstanceOf(IllegalStateException.class);
	}

	private RecommendationJob newJob() {
		return RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				JobType.ITINERARY_GENERATION, this.now);
	}
}
