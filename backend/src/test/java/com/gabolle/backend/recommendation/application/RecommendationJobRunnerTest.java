package com.gabolle.backend.recommendation.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S15P21E201-192 — 도커 없이 도는 단위 테스트. 실제 저장소·엔진은 전부 mock 이다.
 *
 * <p>확인하는 것은 조립 순서다 — job 이 PENDING 으로 <b>저장된 뒤에</b> 비동기 실행이
 * 넘겨지는가, 그리고 제약 스냅샷이 없는 여행을 거부하는가.
 */
class RecommendationJobRunnerTest {

	private TripQueryService tripQueryService;
	private TripRepository tripRepository;
	private RecommendationJobRepository jobRepository;
	private RecommendationService recommendationService;
	private RecommendationJobWorker worker;
	private RecommendationJobRunner runner;

	private final String tripId = UUID.randomUUID().toString();
	private final String userId = UUID.randomUUID().toString();

	@BeforeEach
	void setUp() {
		this.tripQueryService = mock(TripQueryService.class);
		this.tripRepository = mock(TripRepository.class);
		this.jobRepository = mock(RecommendationJobRepository.class);
		this.recommendationService = mock(RecommendationService.class);
		this.worker = mock(RecommendationJobWorker.class);
		this.runner = new RecommendationJobRunner(this.tripQueryService, this.tripRepository, this.jobRepository,
				this.recommendationService, this.worker);

		Trip trip = new Trip(this.tripId, this.userId, java.time.LocalDate.of(2026, 9, 10),
				java.time.LocalDate.of(2026, 9, 11), null, null, null, 1, null, "Asia/Seoul", Instant.now());
		PreferenceSnapshot snapshot = new PreferenceSnapshot(UUID.randomUUID().toString(), this.tripId, 1,
				List.of(), PersonalizationScope.TRIP, List.of(), Instant.now());
		when(this.tripQueryService.get(this.tripId, this.userId))
				.thenReturn(new TripQueryService.View(trip, List.of(), snapshot));
	}

	@Test
	@DisplayName("job 을 PENDING 으로 저장한 뒤에야 비동기 실행을 넘긴다")
	void savesBeforeDispatchingAsyncWork() {
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId))
				.thenReturn(Optional.of(UUID.randomUUID().toString()));
		RecommendationJob preparedJob = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.fromString(this.userId), JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		when(this.recommendationService.prepare(any())).thenReturn(preparedJob);

		RecommendationJob result = this.runner.enqueue(this.tripId, this.userId, null, null);

		assertThat(result).isSameAs(preparedJob);
		assertThat(result.getJobStatus()).isEqualTo(JobStatus.PENDING);

		// 🔴 순서 검증 — save 가 execute 보다 먼저다. 거꾸로면 비동기 스레드가 아직 없는
		//    행을 먼저 건드릴 수 있다(RecommendationJobRunner 클래스 javadoc 참고).
		InOrder order = Mockito.inOrder(this.jobRepository, this.worker);
		order.verify(this.jobRepository).save(preparedJob);
		order.verify(this.worker).execute(any(), any());
	}

	@Test
	@DisplayName("🔴 제약을 하나도 안 답한 여행은 추천을 요청할 수 없다 — constraint_snapshot 이 없다")
	void rejectsTripsWithoutAConstraintSnapshot() {
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.runner.enqueue(this.tripId, this.userId, null, null))
				.isInstanceOf(IllegalStateException.class);

		verify(this.jobRepository, Mockito.never()).save(any());
		verify(this.worker, Mockito.never()).execute(any(), any());
	}

	@Test
	@DisplayName("preferenceSnapshotVersion 을 지정했는데 그 판이 없으면 거부한다")
	void rejectsAMissingPreferenceSnapshotVersion() {
		when(this.tripRepository.findSnapshot(anyString(), anyInt())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.runner.enqueue(this.tripId, this.userId, 7, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
