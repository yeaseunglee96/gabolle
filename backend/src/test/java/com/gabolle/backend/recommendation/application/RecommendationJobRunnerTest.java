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
import com.gabolle.backend.recommendation.repository.RecommendationJobIdempotencyRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConditionRules;
import com.gabolle.backend.trip.domain.TripMember;
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
 * 도커 없이 도는 단위 테스트. 실제 저장소·엔진은 전부 mock 이다. 확인하는 것은 조립 순서다 —
 * job 이 PENDING 으로 저장된 뒤에 비동기 실행이 넘겨지는가, 그리고 제약 스냅샷이 없는 여행을
 * 거부하는가.
 */
class RecommendationJobRunnerTest {

	private TripQueryService tripQueryService;
	private TripRepository tripRepository;
	private RecommendationJobRepository jobRepository;
	private RecommendationService recommendationService;
	private RecommendationJobWorker worker;
	private RecommendationJobIdempotencyRepository idempotencyRepository;
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
		this.idempotencyRepository = mock(RecommendationJobIdempotencyRepository.class);
		this.runner = new RecommendationJobRunner(this.tripQueryService, this.tripRepository, this.jobRepository,
				this.recommendationService, this.worker, this.idempotencyRepository);

		// 1박이라 숙소가 있어야 추천을 받는다(S15P21E201-1585) — 숙소 동네로 채운다.
		stubTrip(tripOneNight().accommodationArea("HAEUNDAE").build());
	}

	private Trip.Builder tripOneNight() {
		return Trip.builder().tripId(this.tripId).createdBy(this.userId)
				.startDate(java.time.LocalDate.of(2026, 9, 10)).finishDate(java.time.LocalDate.of(2026, 9, 11))
				.partySize(1).timezone("Asia/Seoul").createdAt(Instant.now());
	}

	private void stubTrip(Trip trip) {
		PreferenceSnapshot snapshot = new PreferenceSnapshot(UUID.randomUUID().toString(), this.tripId, 1,
				List.of(), PersonalizationScope.TRIP, List.of(), Instant.now());
		when(this.tripQueryService.get(this.tripId, this.userId))
				.thenReturn(new TripQueryService.View(trip, List.of(), snapshot, TripMember.Role.OWNER));
	}

	/** 규칙이 생기기 전에 만든 숙소 없는 여러 날 여행 — 여행 만들기와 같은 칸 이름으로 거부된다. */
	@Test
	@DisplayName("🔴 S15P21E201-1585 — 숙소 필수 스위치가 켜지면 숙소 없는 1박 이상 옛 여행은 추천을 요청할 수 없다")
	void rejectsAMultiDayTripWithoutLodging() {
		this.runner.setLodgingRequired(true);
		stubTrip(tripOneNight().build());

		assertThatThrownBy(() -> this.runner.enqueue(this.tripId, this.userId, null, null))
				.isInstanceOf(TripConditionRules.TripConditionRejectedException.class)
				.hasMessageStartingWith("accommodation: ");

		verify(this.jobRepository, Mockito.never()).save(any());
		verify(this.worker, Mockito.never()).execute(any(), any());
	}

	@Test
	@DisplayName("🔴 스위치가 꺼져 있으면(기본, S15P21E201-1596) 숙소 없는 1박 이상 여행도 추천을 요청할 수 있다")
	void aMultiDayTripWithoutLodgingIsRecommendedWhileTheSwitchIsOff() {
		stubTrip(tripOneNight().build());
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId))
				.thenReturn(Optional.of(UUID.randomUUID().toString()));
		when(this.recommendationService.prepare(any())).thenReturn(RecommendationJob.start(UUID.randomUUID(),
				UUID.randomUUID(), UUID.fromString(this.userId), JobType.ITINERARY_GENERATION, OffsetDateTime.now()));

		assertThat(this.runner.enqueue(this.tripId, this.userId, null, null)).isNotNull();
	}

	@Test
	@DisplayName("당일치기는 숙소 없이도 추천을 요청할 수 있다")
	void aDayTripNeedsNoLodging() {
		stubTrip(tripOneNight().finishDate(java.time.LocalDate.of(2026, 9, 10)).build());
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId))
				.thenReturn(Optional.of(UUID.randomUUID().toString()));
		when(this.recommendationService.prepare(any())).thenReturn(RecommendationJob.start(UUID.randomUUID(),
				UUID.randomUUID(), UUID.fromString(this.userId), JobType.ITINERARY_GENERATION, OffsetDateTime.now()));

		assertThat(this.runner.enqueue(this.tripId, this.userId, null, null)).isNotNull();
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

		// save 가 execute 보다 먼저다. 거꾸로면 비동기 스레드가 아직 없는 행을 건드린다.
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

	@Test
	@DisplayName("🔴 S15P21E201-944 — Idempotency-Key 가 없으면 예전처럼 매번 새 Job 을 만든다")
	void withoutIdempotencyKeyAlwaysCreatesANewJob() {
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId))
				.thenReturn(Optional.of(UUID.randomUUID().toString()));
		RecommendationJob preparedJob = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.fromString(this.userId), JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		when(this.recommendationService.prepare(any())).thenReturn(preparedJob);

		RecommendationJobRunner.EnqueueOutcome outcome = this.runner.enqueue(this.tripId, this.userId, null, null,
				null);

		assertThat(outcome.created()).isTrue();
		verify(this.jobRepository).save(preparedJob);
		verify(this.idempotencyRepository, Mockito.never()).saveWithIdempotency(any(), anyString(), anyString(),
				any());
	}

	@Test
	@DisplayName("🔴 S15P21E201-944 — 같은 Idempotency-Key 로 재시도하면 새 Job 을 만들지 않는다")
	void sameIdempotencyKeyDoesNotCreateASecondJob() {
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId))
				.thenReturn(Optional.of(UUID.randomUUID().toString()));
		RecommendationJob preparedJob = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.fromString(this.userId), JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		when(this.recommendationService.prepare(any())).thenReturn(preparedJob);
		when(this.idempotencyRepository.saveWithIdempotency(any(), anyString(), anyString(), any()))
				.thenReturn(new RecommendationJobIdempotencyRepository.Claimed(preparedJob, false));

		RecommendationJobRunner.EnqueueOutcome outcome = this.runner.enqueue(this.tripId, this.userId, null, null,
				"retry-key");

		assertThat(outcome.created()).isFalse();
		assertThat(outcome.job()).isSameAs(preparedJob);
		// 재시도로 확인된 기존 Job 은 다시 실행에 넘기지 않는다 — 이미 실행 중이거나 끝났다.
		verify(this.worker, Mockito.never()).execute(any(), any());
		verify(this.jobRepository, Mockito.never()).save(any());
	}

	@Test
	@DisplayName("🔴 S15P21E201-944 — 같은 키를 다른 본문으로 재사용하면 충돌 예외가 그대로 올라온다")
	void conflictingIdempotencyKeyPropagatesTheException() {
		when(this.tripRepository.findLatestConstraintSnapshotId(this.tripId))
				.thenReturn(Optional.of(UUID.randomUUID().toString()));
		RecommendationJob preparedJob = RecommendationJob.start(UUID.randomUUID(), UUID.randomUUID(),
				UUID.fromString(this.userId), JobType.ITINERARY_GENERATION, OffsetDateTime.now());
		when(this.recommendationService.prepare(any())).thenReturn(preparedJob);
		when(this.idempotencyRepository.saveWithIdempotency(any(), anyString(), anyString(), any()))
				.thenThrow(new RecommendationJobIdempotencyConflictException("dup-key"));

		assertThatThrownBy(() -> this.runner.enqueue(this.tripId, this.userId, null, null, "dup-key"))
				.isInstanceOf(RecommendationJobIdempotencyConflictException.class);

		verify(this.worker, Mockito.never()).execute(any(), any());
	}
}
