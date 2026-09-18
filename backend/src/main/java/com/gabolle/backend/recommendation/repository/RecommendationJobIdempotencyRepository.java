package com.gabolle.backend.recommendation.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.recommendation.application.RecommendationJobIdempotencyConflictException;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 추천 Job 생성의 {@code Idempotency-Key} 처리 — S15P21E201-944.
 *
 * <p>{@code JpaTripRepository.saveWithIdempotency}와 정확히 같은 모양이다 — 같은 문제를
 * 이미 그 클래스가 풀어 뒀다. 키 확보(이 표에 행 하나 넣기)와 Job 저장을 <b>한
 * 트랜잭션</b>으로 묶는다. 나누면 "키는 확보됐는데 Job 은 아직 없는" 순간에 다른
 * 요청이 끼어들 수 있고, 그 순간의 동시 요청은 전부 Job 을 만들게 된다 — 그 레이스를
 * {@code TripCreationTest}가 이미 한 번 잡았다(같은 클래스 주석 참고).
 *
 * <p>🔴 {@code SAVEPOINT}(PROPAGATION_NESTED) 대신 {@code ON CONFLICT DO NOTHING}을
 * 쓰는 이유도 같다 — 이 환경의 트랜잭션 매니저에서 SAVEPOINT 가 실제로 안 먹히는 것을
 * {@code JpaItineraryRepository} 작업 중 CI 로 확인했다.
 */
@Component
@Profile({ "db", "dev" })
public class RecommendationJobIdempotencyRepository {

	private final RecommendationJobRepository jobRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public RecommendationJobIdempotencyRepository(RecommendationJobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	/** @param created 새로 만들었으면 참, 기존 Job 을 그대로 돌려주는 것이면 거짓 */
	public record Claimed(RecommendationJob job, boolean created) {
	}

	/**
	 * 키를 확보하면 넘겨받은 {@code job}을 그대로 저장한다. 이미 같은 키가 있으면 그
	 * 지문을 대조해, 같으면 기존 Job을, 다르면 예외를 던진다.
	 *
	 * @throws RecommendationJobIdempotencyConflictException 같은 키가 <b>다른 본문</b>으로
	 *     이미 쓰였을 때
	 */
	@Transactional
	public Claimed saveWithIdempotency(UUID userId, String idempotencyKey, String fingerprint,
			RecommendationJob job) {
		int inserted = this.entityManager.createNativeQuery(
				"INSERT INTO recommendation_job_idempotency "
						+ "(user_id, idempotency_key, request_fingerprint, job_id, created_at) "
						+ "VALUES (?1, ?2, ?3, ?4, ?5) ON CONFLICT (user_id, idempotency_key) DO NOTHING")
				.setParameter(1, userId)
				.setParameter(2, idempotencyKey)
				.setParameter(3, fingerprint)
				.setParameter(4, job.getJobId())
				.setParameter(5, OffsetDateTime.now())
				.executeUpdate();

		if (inserted == 1) {
			this.jobRepository.save(job);
			return new Claimed(job, true);
		}

		Object[] row = (Object[]) this.entityManager.createNativeQuery(
				"SELECT job_id, request_fingerprint FROM recommendation_job_idempotency "
						+ "WHERE user_id = ?1 AND idempotency_key = ?2")
				.setParameter(1, userId)
				.setParameter(2, idempotencyKey)
				.getSingleResult();

		String existingFingerprint = (String) row[1];
		if (!existingFingerprint.equals(fingerprint)) {
			throw new RecommendationJobIdempotencyConflictException(idempotencyKey);
		}

		UUID existingJobId = (UUID) row[0];
		RecommendationJob existingJob = this.jobRepository.findById(existingJobId).orElseThrow(
				() -> new IllegalStateException("멱등 키가 가리키는 Job 이 없다: " + existingJobId));
		return new Claimed(existingJob, false);
	}
}
