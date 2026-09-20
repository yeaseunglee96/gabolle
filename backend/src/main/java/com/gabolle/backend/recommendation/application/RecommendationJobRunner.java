package com.gabolle.backend.recommendation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobIdempotencyRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 일정 생성 요청을 받아 작업 번호를 즉시 돌려주고, 실제 계산은 뒤에서 돌린다.
 *
 * <p>{@code @Transactional} 을 여기 달지 않는다. {@code jobRepository.save(job)} 가 완전히
 * 커밋된 뒤에야 {@link RecommendationJobWorker#execute} 를 비동기로 넘겨야 하는데,
 * {@code @Transactional} 로 감싸면 메서드가 끝날 때(= 비동기 작업을 이미 넘긴 뒤)에야
 * 커밋된다. {@code JpaRepository.save()} 자체가 이미 트랜잭션이라 순서대로 부르면 충분하다.
 *
 * <p>{@code @ConditionalOnBean(TripQueryService.class)} 인 것은 추천 도메인만 스캔하는 테스트
 * 컨텍스트가 {@code trip} 패키지를 안 올려 그 빈이 없기 때문이다. 조건이 없으면 그 슬라이스를
 * 쓰는, 추천과 무관한 테스트까지 컨텍스트 로딩에서 깨진다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class RecommendationJobRunner {

	private final TripQueryService tripQueryService;

	private final TripRepository tripRepository;

	private final RecommendationJobRepository jobRepository;

	private final RecommendationService recommendationService;

	private final RecommendationJobWorker worker;

	private final RecommendationJobIdempotencyRepository idempotencyRepository;

	public RecommendationJobRunner(TripQueryService tripQueryService, TripRepository tripRepository,
			RecommendationJobRepository jobRepository, RecommendationService recommendationService,
			RecommendationJobWorker worker, RecommendationJobIdempotencyRepository idempotencyRepository) {
		this.tripQueryService = tripQueryService;
		this.tripRepository = tripRepository;
		this.jobRepository = jobRepository;
		this.recommendationService = recommendationService;
		this.worker = worker;
		this.idempotencyRepository = idempotencyRepository;
	}

	/**
	 * Job 을 만들어 PENDING 으로 저장하고 즉시 돌려준다. 실제 계산은 이 메서드가 돌려준
	 * 뒤에 별도 스레드에서 시작된다.
	 *
	 * @param preferenceSnapshotVersion 어느 판의 취향으로 계산할까. {@code null} 이면 최신 판
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 그 여행의 회원이
	 *     아니다. {@link TripQueryService} 가 이미 하는 검증을 그대로 재사용한다
	 * @throws IllegalArgumentException {@code preferenceSnapshotVersion} 을 지정했는데 그
	 *     판이 없다
	 * @throws IllegalStateException 이 여행이 제약을 하나도 답하지 않아 constraint_snapshot
	 *     자체가 없다 — 지금은 이런 여행에 추천을 요청할 방법이 없다(알려진 한계)
	 */
	public RecommendationJob enqueue(String tripId, String userId, Integer preferenceSnapshotVersion,
			Integer topK) {
		return enqueue(buildCommand(tripId, userId, preferenceSnapshotVersion, topK));
	}

	/**
	 * {@link #enqueue(String, String, Integer, Integer)} 와 같지만 {@code Idempotency-Key} 를
	 * 받는다. 키 확보와 Job 저장을 한 트랜잭션으로 묶고 본문 지문을 대조하는 방식은
	 * {@code TripCreationService}·{@code ShareCloneService} 와 같다.
	 *
	 * @param idempotencyKey {@code null}·빈 문자열이면 매번 새 Job 이다 — 클라이언트 opt-in
	 * @throws RecommendationJobIdempotencyConflictException 같은 키가 다른 본문으로 이미
	 *     쓰였을 때
	 */
	public EnqueueOutcome enqueue(String tripId, String userId, Integer preferenceSnapshotVersion, Integer topK,
			String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return new EnqueueOutcome(enqueue(tripId, userId, preferenceSnapshotVersion, topK), true);
		}

		RecommendationCommand command = buildCommand(tripId, userId, preferenceSnapshotVersion, topK);
		String fingerprint = fingerprintOf(tripId, userId, preferenceSnapshotVersion, topK);
		RecommendationJob prepared = this.recommendationService.prepare(command);

		// 이 호출이 커밋돼야 아래 execute 를 부른다. saveWithIdempotency 는 별도 빈의
		// @Transactional 메서드라 여기로 돌아온 시점에 이미 커밋돼 있다.
		RecommendationJobIdempotencyRepository.Claimed claimed = this.idempotencyRepository
				.saveWithIdempotency(UUID.fromString(userId), idempotencyKey, fingerprint, prepared);

		if (claimed.created()) {
			this.worker.execute(claimed.job(), command);
		}
		// created()가 거짓이면(=키 재사용) worker 를 다시 부르지 않는다 — 그 Job 은 이미
		// 실행 중이거나 끝났고, 다시 부르면 같은 계산을 두 번 하는 것이다.
		return new EnqueueOutcome(claimed.job(), claimed.created());
	}

	/** 새로 만들었으면 참, 같은 키로 이미 있던 Job 을 그대로 돌려주는 것이면 거짓. */
	public record EnqueueOutcome(RecommendationJob job, boolean created) {
	}

	private RecommendationCommand buildCommand(String tripId, String userId, Integer preferenceSnapshotVersion,
			Integer topK) {
		TripQueryService.View view = this.tripQueryService.get(tripId, userId);

		PreferenceSnapshot snapshot = (preferenceSnapshotVersion != null)
				? this.tripRepository.findSnapshot(tripId, preferenceSnapshotVersion)
						.orElseThrow(() -> new IllegalArgumentException(
								"preferenceSnapshotVersion 을 찾을 수 없다: " + preferenceSnapshotVersion))
				: view.snapshot();
		if (snapshot == null) {
			// 여행 생성이 언제나 preference_snapshot 을 만들어 실제로는 안 일어난다 — 그래도
			// null 로 명령을 만들면 안 되니 막는다.
			throw new IllegalStateException("여행에 취향 스냅샷이 없다: " + tripId);
		}

		String constraintSnapshotId = this.tripRepository.findLatestConstraintSnapshotId(tripId)
				.orElseThrow(() -> new IllegalStateException(
						"이 여행은 제약을 하나도 답하지 않아 추천을 요청할 수 없다: " + tripId));

		return new RecommendationCommand(
				UUID.fromString(userId),
				JobType.ITINERARY_GENERATION,
				UUID.fromString(tripId),
				// tripVersion — Trip 도메인에 아직 버전 칸이 없다. 그때까지 어느 판으로
				// 계산했나는 preferenceSnapshotId 로만 되찾을 수 있다.
				null,
				UUID.fromString(snapshot.snapshotId()),
				UUID.fromString(constraintSnapshotId),
				null, null, null, // itineraryId · itineraryVersion · baseVersion — 새 일정 생성이라 없다
				null, // appVersion — 아직 헤더로 안 받는다(TripController 도 같은 상태)
				topK,
				null); // edit — 일정 생성은 편집이 아니다
	}

	/**
	 * 요청 본문의 지문 — {@code TripCreationService.fingerprintOf} 와 같은 방식(SHA-256 hex).
	 * 클라이언트가 실제로 보낸 값만 담는다 — 서버가 뒤에 파생한 값은 같은 입력이면 항상 같게
	 * 파생되므로 지문에 넣을 이유가 없다.
	 */
	private String fingerprintOf(String tripId, String userId, Integer preferenceSnapshotVersion, Integer topK) {
		String raw = String.join("|", tripId, userId, String.valueOf(preferenceSnapshotVersion),
				String.valueOf(topK));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 이 없다", e);
		}
	}

	/**
	 * 이미 조립된 명령을 받아 접수만 한다. 장소 제외·재계산 편집 Job 이 쓴다 — 그 경로는
	 * 소유권·스냅샷을 {@code ItineraryRecalculationService} 가 이미 확인하고
	 * {@link RecommendationCommand} 를 조립해 넘기므로 여행을 다시 조회할 필요가 없다.
	 */
	public RecommendationJob enqueue(RecommendationCommand command) {
		RecommendationJob job = this.recommendationService.prepare(command);
		// 이 save 가 커밋돼야 아래 execute 를 부른다. 순서를 바꾸지 않는다.
		this.jobRepository.save(job);

		this.worker.execute(job, command);

		return job;
	}

	public Optional<RecommendationJob> findJob(String jobId) {
		return this.jobRepository.findById(UUID.fromString(jobId));
	}

	/**
	 * 그 여행의 추천 작업을 최신순으로.
	 *
	 * <p>소유권 검사를 새로 짜지 않고 {@code tripQueryService.get} 이라는 같은 관문을 지난다 —
	 * 검사를 따로 만들면 두 경로의 거절 모양이 언젠가 갈라지고, 그 차이 자체가 "있는데 너는
	 * 못 본다" 는 신호가 된다.
	 *
	 * <p>내 여행인데 추천이 없으면 빈 목록이지 404 가 아니다. 화면이 「아직 추천을 안
	 * 만들었다」와 「그런 여행이 없다」를 갈라 그려야 한다.
	 *
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 그 여행의 회원이 아니다
	 */
	public List<RecommendationJob> findJobsByTrip(String tripId, String userId) {
		this.tripQueryService.get(tripId, userId);
		return this.jobRepository.findByTripIdOrderByCreatedAtDesc(UUID.fromString(tripId),
				PageRequest.of(0, MAX_JOBS_PER_TRIP));
	}

	/**
	 * 한 여행에 대해 한 번에 돌려주는 Job 수의 상한. 화면이 실제로 쓰는 것은 맨 앞 하나지만,
	 * 하나만 돌려주면 가장 최근 것이 실패한 Job 일 때 그 앞의 성공한 추천을 못 찾는다 —
	 * 목록으로 주고 무엇을 고를지는 부르는 쪽이 정하게 둔다.
	 */
	private static final int MAX_JOBS_PER_TRIP = 20;
}
