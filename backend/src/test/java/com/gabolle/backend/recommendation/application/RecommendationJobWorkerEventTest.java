package com.gabolle.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 추천이 일정을 만들며 성공하면 코스 2·3안을 미리 짜라는 알림을 낸다 (S15P21E201-1604). 추천이 끝났다는 것을 화면에
 * 먼저 알린 뒤이고, 무엇이 실패해도 추천 작업 자체는 흔들리지 않는다.
 */
class RecommendationJobWorkerEventTest {

	private final RecommendationJobRepository jobRepository = mock(RecommendationJobRepository.class);

	private final RecommendationService recommendationService = mock(RecommendationService.class);

	private final JobProgressReporter progress = mock(JobProgressReporter.class);

	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final RecommendationJobWorker worker = new RecommendationJobWorker(this.jobRepository,
			this.recommendationService, mock(RecommendationRecorder.class), Clock.systemUTC(), this.progress,
			this.events);

	private static RecommendationJob job(JobType type, JobStatus status, UUID itineraryId) {
		RecommendationJob job = mock(RecommendationJob.class);
		when(job.getJobType()).thenReturn(type);
		when(job.getJobStatus()).thenReturn(status);
		when(job.getItineraryId()).thenReturn(itineraryId);
		when(job.getRequestId()).thenReturn(UUID.randomUUID());
		return job;
	}

	@Test
	@DisplayName("🔴 일정 생성이 성공하면 알림이 나간다 — 화면에 끝을 알린 뒤에")
	void aSuccessfulGenerationIsAnnouncedAfterProgress() {
		RecommendationJob job = job(JobType.ITINERARY_GENERATION, JobStatus.SUCCEEDED, UUID.randomUUID());

		this.worker.execute(job, null);

		ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
		InOrder order = inOrder(this.progress, this.events);
		order.verify(this.progress, org.mockito.Mockito.atLeastOnce()).publishCurrent(job);
		order.verify(this.events).publishEvent(event.capture());
		assertThat(event.getValue()).isEqualTo(new RecommendationJobSucceeded(job.getRequestId()));
	}

	@Test
	@DisplayName("실패했거나 일정 생성이 아닌 작업(하루 다시 짜기 등)은 알리지 않는다")
	void failuresAndOtherJobsAreNotAnnounced() {
		this.worker.execute(job(JobType.ITINERARY_GENERATION, JobStatus.FAILED, null), null);
		this.worker.execute(job(JobType.ITINERARY_RECALCULATE, JobStatus.SUCCEEDED, UUID.randomUUID()), null);

		verify(this.events, never()).publishEvent(any());
	}

	@Test
	@DisplayName("🔴 알림이 실패해도 추천 작업은 흔들리지 않는다 — 예외가 새지 않는다")
	void aFailingAnnouncementDoesNotBreakTheJob() {
		RecommendationJob job = job(JobType.ITINERARY_GENERATION, JobStatus.SUCCEEDED, UUID.randomUUID());
		doThrow(new IllegalStateException("받는 쪽 실행기가 거부")).when(this.events).publishEvent(any(Object.class));

		this.worker.execute(job, null);

		verify(this.recommendationService).continueJob(job, null);
		verify(this.progress, org.mockito.Mockito.atLeastOnce()).publishCurrent(job);
	}
}
