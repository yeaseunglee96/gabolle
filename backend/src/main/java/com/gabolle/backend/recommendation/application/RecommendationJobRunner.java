package com.gabolle.backend.recommendation.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 일정 생성 요청을 받아 작업 번호를 즉시 돌려주고, 실제 계산은 뒤에서 돌린다 —
 * S15P21E201-192 · REC-01.
 *
 * <p>{@link RecommendationService#recommend} 는 동기다(REC-04 "지금 갈 곳" 전용). 이
 * 클래스가 그 서비스를 감싸 REC-01 "전체 일정 생성" 을 비동기로 만든다.
 *
 * <p>🔴 <b>{@code @Transactional} 을 여기 달지 않는다.</b> {@link #enqueue} 안에서
 * {@code jobRepository.save(job)} 를 부르는데, 그 저장이 <b>완전히 커밋된 뒤에</b>
 * {@link RecommendationJobWorker#execute} 를 비동기로 넘겨야 한다. 이 메서드를
 * {@code @Transactional} 로 감싸면 트랜잭션이 메서드가 끝날 때(= 비동기 작업을 이미
 * 넘긴 뒤)에야 커밋되므로, 다른 스레드가 아직 커밋 안 된 행을 먼저 건드릴 여지가
 * 생긴다. {@code JpaRepository.save()} 는 그 자체로 이미 트랜잭션이라(Spring Data
 * 기본 동작) 여기서 감쌀 필요가 없다 — 그냥 순서대로 부르는 것으로 충분하다.
 */
@Service
@Profile({ "db", "dev" })
public class RecommendationJobRunner {

	private final TripQueryService tripQueryService;

	private final TripRepository tripRepository;

	private final RecommendationJobRepository jobRepository;

	private final RecommendationService recommendationService;

	private final RecommendationJobWorker worker;

	public RecommendationJobRunner(TripQueryService tripQueryService, TripRepository tripRepository,
			RecommendationJobRepository jobRepository, RecommendationService recommendationService,
			RecommendationJobWorker worker) {
		this.tripQueryService = tripQueryService;
		this.tripRepository = tripRepository;
		this.jobRepository = jobRepository;
		this.recommendationService = recommendationService;
		this.worker = worker;
	}

	/**
	 * Job 을 만들어 PENDING 으로 저장하고 즉시 돌려준다. 실제 계산은 이 메서드가 돌려준
	 * 뒤에 별도 스레드에서 시작된다.
	 *
	 * @param preferenceSnapshotVersion 어느 판의 취향으로 계산할까. {@code null} 이면 최신 판
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 그 여행의 회원이
	 *     아니다 — FR-SEC-01. {@link TripQueryService} 가 이미 하는 검증을 그대로 재사용한다
	 * @throws IllegalArgumentException {@code preferenceSnapshotVersion} 을 지정했는데 그
	 *     판이 없다
	 * @throws IllegalStateException 이 여행이 제약을 하나도 답하지 않아 constraint_snapshot
	 *     자체가 없다 — 지금은 이런 여행에 추천을 요청할 방법이 없다(알려진 한계, 클래스
	 *     상단 참고)
	 */
	public RecommendationJob enqueue(String tripId, String userId, Integer preferenceSnapshotVersion,
			Integer topK) {
		TripQueryService.View view = this.tripQueryService.get(tripId, userId);

		PreferenceSnapshot snapshot = (preferenceSnapshotVersion != null)
				? this.tripRepository.findSnapshot(tripId, preferenceSnapshotVersion)
						.orElseThrow(() -> new IllegalArgumentException(
								"preferenceSnapshotVersion 을 찾을 수 없다: " + preferenceSnapshotVersion))
				: view.snapshot();
		if (snapshot == null) {
			// 🔴 여행 생성이 언제나 preference_snapshot 을 만들어 실제로는 안 일어난다
			//    (JpaTripRepository.save 참고) — 그래도 null 로 명령을 만들면 안 되니 막는다.
			throw new IllegalStateException("여행에 취향 스냅샷이 없다: " + tripId);
		}

		String constraintSnapshotId = this.tripRepository.findLatestConstraintSnapshotId(tripId)
				.orElseThrow(() -> new IllegalStateException(
						"이 여행은 제약을 하나도 답하지 않아 추천을 요청할 수 없다: " + tripId));

		RecommendationCommand command = new RecommendationCommand(
				UUID.fromString(userId),
				JobType.ITINERARY_GENERATION,
				UUID.fromString(tripId),
				// 🔴 tripVersion — Trip 도메인에 아직 버전 칸이 없다(TripJpaEntity 에도 없다).
				//    생기면 채운다. 그때까지는 "이 여행 조건 중 어느 판으로 계산했나" 를
				//    이 값으로는 못 되찾고, preferenceSnapshotId 로만 되찾을 수 있다.
				null,
				UUID.fromString(snapshot.snapshotId()),
				UUID.fromString(constraintSnapshotId),
				null, null, null, // itineraryId · itineraryVersion · baseVersion — 새 일정 생성이라 없다
				null, // appVersion — 아직 헤더로 안 받는다(TripController 도 같은 상태)
				topK);

		RecommendationJob job = this.recommendationService.prepare(command);
		// 🔴 이 save 가 끝나야(=커밋돼야) 아래 execute 를 부른다. 순서를 바꾸지 않는다.
		this.jobRepository.save(job);

		this.worker.execute(job, command);

		return job;
	}

	public Optional<RecommendationJob> findJob(String jobId) {
		return this.jobRepository.findById(UUID.fromString(jobId));
	}
}
