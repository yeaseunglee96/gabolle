package com.gabolle.backend.recommendation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.RecommendationJob;

public interface RecommendationJobRepository extends JpaRepository<RecommendationJob, UUID> {

	/** 분석은 언제나 request_id 에서 시작한다. */
	Optional<RecommendationJob> findByRequestId(UUID requestId);

	boolean existsByRequestId(UUID requestId);

	/**
	 * 그 여행의 추천 작업을 최신순으로 — S15P21E201-1001.
	 *
	 * <p>지금까지 Job 을 되찾는 길은 {@code jobId} 하나뿐이었는데, 그 번호는 생성 응답에 한 번
	 * 실려 나가고 어디에도 안 남는다. 그래서 화면을 다시 열면 이미 만든 추천을 못 찾았다.
	 *
	 * <p>🔴 <b>상한을 부르는 쪽이 {@code Pageable} 로 준다.</b> 한 여행의 Job 수에는 정해진
	 * 천장이 없다 — 사용자가 추천을 다시 요청할 때마다 늘고, 일정 편집 Job 도 같은 여행에
	 * 붙는다. 전부 돌려주는 조회는 지금은 빠르지만 그 사실이 조용히 바뀐다.
	 */
	List<RecommendationJob> findByTripIdOrderByCreatedAtDesc(UUID tripId, Pageable pageable);

	// ── 지표 조회 (S15P21E201-160) ──────────────────────────────────────

	/**
	 * 그 기간에 접수된 Job 을 상태별로 센다 — {@code GET /api/v1/admin/analytics/kpis} 의 근거.
	 *
	 * <p>{@code createdAt} 기준이다 — "그 기간에 접수된 요청이 지금 어느 상태인가" 를 본다.
	 * 아직 {@code PENDING}·{@code RUNNING} 인 것도 그대로 세어진다 — 진행 중인 것을 조용히
	 * 빼면 "그 기간에 접수한 것 중 몇 개가 끝났나" 를 답할 수 없다.
	 */
	@Query("SELECT j.jobStatus AS jobStatus, COUNT(j) AS count FROM RecommendationJob j "
			+ "WHERE j.createdAt >= :from AND j.createdAt < :to GROUP BY j.jobStatus")
	List<JobStatusCount> countByJobStatusBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	/**
	 * 그 기간에 접수돼 성공한 Job 의 평균 처리 시간 — {@code totalLatencyMs} 가 있는 것만 본다.
	 *
	 * <p>값이 없으면(성공한 Job 이 하나도 없거나, 있어도 지연시간을 아무도 안 남겼으면)
	 * {@code null} 이다. {@code 0} 으로 답하면 "쟀는데 0ms 였다" 와 "잴 것이 없었다" 가
	 * 구분되지 않는다.
	 */
	@Query("SELECT AVG(j.totalLatencyMs) FROM RecommendationJob j "
			+ "WHERE j.jobStatus = com.gabolle.backend.recommendation.domain.JobStatus.SUCCEEDED "
			+ "AND j.totalLatencyMs IS NOT NULL AND j.createdAt >= :from AND j.createdAt < :to")
	Double averageLatencyMsForSucceededBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	/** 그 기간에 접수돼 실패한 Job 을 실패 사유({@code errorCode})별로 센다. */
	@Query("SELECT j.errorCode AS errorCode, COUNT(j) AS count FROM RecommendationJob j "
			+ "WHERE j.jobStatus = com.gabolle.backend.recommendation.domain.JobStatus.FAILED "
			+ "AND j.createdAt >= :from AND j.createdAt < :to GROUP BY j.errorCode")
	List<ErrorCodeCount> countFailuresByErrorCodeBetween(@Param("from") OffsetDateTime from,
			@Param("to") OffsetDateTime to);

	/** {@code j.jobStatus} · {@code COUNT(j)} 그룹 결과를 받는 프로젝션. */
	interface JobStatusCount {
		JobStatus getJobStatus();

		long getCount();
	}

	/** {@code j.errorCode} · {@code COUNT(j)} 그룹 결과를 받는 프로젝션. */
	interface ErrorCodeCount {
		String getErrorCode();

		long getCount();
	}
}
