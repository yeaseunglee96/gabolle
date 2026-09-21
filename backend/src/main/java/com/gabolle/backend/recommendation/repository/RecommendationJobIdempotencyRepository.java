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
 * 추천 Job 생성의 {@code Idempotency-Key} 처리. {@code JpaTripRepository.saveWithIdempotency}
 * 와 같은 모양이다.
 *
 * 키 확보와 Job 저장을 한 트랜잭션으로 묶는다 — 나누면 키는 확보됐는데 Job 은 아직 없는
 * 순간이 생기고, 그때 들어온 동시 요청이 전부 Job 을 만든다. {@code SAVEPOINT}
 * (PROPAGATION_NESTED)가 아니라 {@code ON CONFLICT DO NOTHING} 인 것은 이 환경의 트랜잭션
 * 매니저에서 SAVEPOINT 가 실제로 안 먹히기 때문이다.
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
	 * @throws RecommendationJobIdempotencyConflictException 같은 키가 다른 본문으로 이미
	 *     쓰였을 때
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
