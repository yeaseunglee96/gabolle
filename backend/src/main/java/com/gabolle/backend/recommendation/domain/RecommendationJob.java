package com.gabolle.backend.recommendation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 추천 요청 한 건. {@code request_id} 는 요청부터 후보·노출·행동까지를 잇는 하나뿐인 열쇠다.
 *
 * <p>후보 개수는 후보 행마다 반복하지 않고 여기 단계별로 한 번만 적는다.
 */
@Entity
@Table(name = "recommendation_job")
public class RecommendationJob {

	@Id
	@Column(name = "job_id", nullable = false, updatable = false)
	private UUID jobId;

	@Column(name = "request_id", nullable = false, unique = true, updatable = false)
	private UUID requestId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "trip_id")
	private UUID tripId;

	@Column(name = "trip_version")
	private Integer tripVersion;

	/** S15P21E201-542 의 취향 스냅샷 ID. 542 병합 뒤 FK 를 추가한다. */
	@Column(name = "preference_snapshot_id")
	private UUID preferenceSnapshotId;

	/** S15P21E201-542 의 제약 스냅샷 ID. 542 병합 뒤 FK 를 추가한다. */
	@Column(name = "constraint_snapshot_id")
	private UUID constraintSnapshotId;

	@Column(name = "itinerary_id")
	private UUID itineraryId;

	@Column(name = "itinerary_version")
	private Integer itineraryVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 40, updatable = false)
	private JobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", length = 20)
	private ResourceType resourceType;

	@Column(name = "resource_id")
	private UUID resourceId;

	/** 편집 기준이 된 일정 버전. 완료 시 최신과 같을 때만 새 버전을 게시한다 (FR-ITN-09). */
	@Column(name = "base_version")
	private Integer baseVersion;

	@Column(name = "retryable", nullable = false)
	private boolean retryable;

	/**
	 * 🔴 아래 둘은 비동기 Job 러너(REC-01 · JOB-01)가 채운다. 그 러너는 S15P21E201-543
	 * 범위가 아니라 여기서는 비어 있다. 기본값을 넣으면 클라이언트가 지켜지지도 않을
	 * 폴링 주기를 믿게 된다.
	 */
	@Column(name = "poll_after_seconds")
	private Integer pollAfterSeconds;

	@Column(name = "expires_at")
	private OffsetDateTime expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_status", nullable = false, length = 20)
	private JobStatus jobStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_stage", length = 30)
	private JobStage jobStage;

	@Column(name = "progress_percent", nullable = false)
	private int progressPercent;

	@Column(name = "generated_candidate_count", nullable = false)
	private int generatedCandidateCount;

	@Column(name = "eligible_candidate_count", nullable = false)
	private int eligibleCandidateCount;

	@Column(name = "returned_candidate_count", nullable = false)
	private int returnedCandidateCount;

	@Column(name = "model_version", length = 100)
	private String modelVersion;

	@Column(name = "feature_version", length = 100)
	private String featureVersion;

	@Column(name = "ontology_version", length = 100)
	private String ontologyVersion;

	/**
	 * 온톨로지 판정에 쓰인 <b>정책</b> 버전. {@code ontologyVersion} 과 다르다 — 어휘·규칙이
	 * 그대로여도 정책(무엇을 REQUIRED 로 볼 것인가)이 바뀌면 같은 장소의 판정이 바뀐다.
	 * GB-API-001 4.3 이 추천 응답의 필수 항목으로 요구한다.
	 */
	@Column(name = "policy_version", length = 100)
	private String policyVersion;

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;

	@Column(name = "service_version", length = 100)
	private String serviceVersion;

	@Column(name = "app_version", length = 50)
	private String appVersion;

	@Column(name = "deployment_environment", length = 30)
	private String deploymentEnvironment;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "generated_at")
	private OffsetDateTime generatedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "total_latency_ms")
	private Long totalLatencyMs;

	@Column(name = "candidate_generation_latency_ms")
	private Long candidateGenerationLatencyMs;

	@Column(name = "ontology_latency_ms")
	private Long ontologyLatencyMs;

	@Column(name = "feature_lookup_latency_ms")
	private Long featureLookupLatencyMs;

	@Column(name = "ranking_latency_ms")
	private Long rankingLatencyMs;

	@Column(name = "optimization_latency_ms")
	private Long optimizationLatencyMs;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "failure_stage", length = 30)
	private JobStage failureStage;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "timeout_occurred", nullable = false)
	private boolean timeoutOccurred;

	@Column(name = "fallback_reason", length = 64)
	private String fallbackReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "fallback_mode", length = 20)
	private FallbackMode fallbackMode;

	protected RecommendationJob() {
		// JPA 전용
	}

	private RecommendationJob(UUID jobId, UUID requestId, UUID userId, JobType jobType,
			OffsetDateTime createdAt) {
		this.jobId = jobId;
		this.requestId = requestId;
		this.userId = userId;
		this.jobType = jobType;
		this.createdAt = createdAt;
		this.jobStatus = JobStatus.QUEUED;
		this.jobStage = JobStage.CREATED;
	}

	/**
	 * 🔴 {@code jobId} 와 {@code requestId} 는 서버가 만든다. 클라이언트가 정하게 두면
	 * 같은 request_id 로 남의 요청을 덮어쓰거나 분석 키를 조작할 수 있다.
	 */
	public static RecommendationJob start(UUID jobId, UUID requestId, UUID userId, JobType jobType,
			OffsetDateTime createdAt) {
		if (jobType == null) {
			throw new IllegalArgumentException("jobType 은 필수다 (GB-API-001 4.2 JobDto.type)");
		}
		return new RecommendationJob(jobId, requestId, userId, jobType, createdAt);
	}

	public void applyRequestContext(UUID tripId, Integer tripVersion, UUID preferenceSnapshotId,
			UUID constraintSnapshotId, UUID itineraryId, Integer itineraryVersion, Integer baseVersion,
			String appVersion) {
		this.tripId = tripId;
		this.tripVersion = tripVersion;
		this.preferenceSnapshotId = preferenceSnapshotId;
		this.constraintSnapshotId = constraintSnapshotId;
		this.itineraryId = itineraryId;
		this.itineraryVersion = itineraryVersion;
		this.baseVersion = baseVersion;
		this.appVersion = appVersion;

		// 일정에 붙은 Job 이면 일정이, 아니면 여행이 대상이다. 둘 다 없으면 비워 둔다 —
		// 명세서에 없는 resourceType 값을 지어내지 않는다.
		if (itineraryId != null) {
			this.resourceType = ResourceType.ITINERARY;
			this.resourceId = itineraryId;
		}
		else if (tripId != null) {
			this.resourceType = ResourceType.TRIP;
			this.resourceId = tripId;
		}
	}

	public void applyVersions(String modelVersion, String featureVersion, String ontologyVersion,
			String policyVersion, String datasetVersion, String serviceVersion, String deploymentEnvironment) {
		this.modelVersion = modelVersion;
		this.featureVersion = featureVersion;
		this.ontologyVersion = ontologyVersion;
		this.policyVersion = policyVersion;
		this.datasetVersion = datasetVersion;
		this.serviceVersion = serviceVersion;
		this.deploymentEnvironment = deploymentEnvironment;
	}

	public void applyCounts(int generated, int eligible, int returned) {
		this.generatedCandidateCount = generated;
		this.eligibleCandidateCount = eligible;
		this.returnedCandidateCount = returned;
	}

	public void applyLatencies(Long totalMs, Long candidateGenerationMs, Long ontologyMs, Long featureLookupMs,
			Long rankingMs, Long optimizationMs) {
		this.totalLatencyMs = totalMs;
		this.candidateGenerationLatencyMs = candidateGenerationMs;
		this.ontologyLatencyMs = ontologyMs;
		this.featureLookupLatencyMs = featureLookupMs;
		this.rankingLatencyMs = rankingMs;
		this.optimizationLatencyMs = optimizationMs;
	}

	/**
	 * 결과를 만들고 끝났다. 항상 {@link JobStatus#SUCCEEDED} 다.
	 *
	 * <p>🔴 대체 경로로 만들었어도 성공이다. 그 사실은 상태가 아니라 {@code fallbackMode} 와
	 * {@code fallbackReason} 이 나타낸다 — GB-API-001 5장의 JobStatus 에는 FALLBACK 이 없다.
	 */
	public void markCompleted(OffsetDateTime generatedAt, OffsetDateTime completedAt, FallbackMode fallbackMode,
			String fallbackReason) {
		this.generatedAt = generatedAt;
		this.completedAt = completedAt;
		this.fallbackMode = fallbackMode;
		this.fallbackReason = fallbackReason;
		this.jobStatus = JobStatus.SUCCEEDED;
		this.jobStage = JobStage.COMPLETED;
		this.progressPercent = 100;
	}

	/**
	 * 실패 종료. 🔴 {@code errorCode} 없이 FAILED 로 남길 수 없다 — DB CHECK 도 같은 것을 요구한다.
	 * 원인 없는 실패 기록은 나중에 아무것도 설명하지 못한다.
	 *
	 * @param retryable 같은 요청을 다시 보내면 달라질 여지가 있는가. JobDto 가 이 값을 그대로
	 *     내보내므로, 버전 누락처럼 재시도해도 같은 결과인 실패에 {@code true} 를 주면
	 *     클라이언트가 헛되이 반복한다
	 */
	public void markFailed(String errorCode, JobStage failureStage, OffsetDateTime completedAt,
			boolean timeoutOccurred, boolean retryable) {
		if (errorCode == null || errorCode.isBlank()) {
			throw new IllegalArgumentException("errorCode 없이 Job 을 FAILED 로 남길 수 없다");
		}
		this.jobStatus = JobStatus.FAILED;
		this.errorCode = errorCode;
		this.failureStage = failureStage;
		this.jobStage = failureStage;
		this.completedAt = completedAt;
		this.timeoutOccurred = timeoutOccurred;
		this.retryable = retryable;
	}

	public void increaseRetryCount() {
		this.retryCount++;
	}

	public UUID getJobId() {
		return jobId;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getTripId() {
		return tripId;
	}

	public Integer getTripVersion() {
		return tripVersion;
	}

	public UUID getPreferenceSnapshotId() {
		return preferenceSnapshotId;
	}

	public UUID getConstraintSnapshotId() {
		return constraintSnapshotId;
	}

	public UUID getItineraryId() {
		return itineraryId;
	}

	public Integer getItineraryVersion() {
		return itineraryVersion;
	}

	public JobType getJobType() {
		return jobType;
	}

	public ResourceType getResourceType() {
		return resourceType;
	}

	public UUID getResourceId() {
		return resourceId;
	}

	public Integer getBaseVersion() {
		return baseVersion;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public Integer getPollAfterSeconds() {
		return pollAfterSeconds;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public JobStatus getJobStatus() {
		return jobStatus;
	}

	public JobStage getJobStage() {
		return jobStage;
	}

	public int getProgressPercent() {
		return progressPercent;
	}

	public int getGeneratedCandidateCount() {
		return generatedCandidateCount;
	}

	public int getEligibleCandidateCount() {
		return eligibleCandidateCount;
	}

	public int getReturnedCandidateCount() {
		return returnedCandidateCount;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public String getFeatureVersion() {
		return featureVersion;
	}

	public String getOntologyVersion() {
		return ontologyVersion;
	}

	public String getPolicyVersion() {
		return policyVersion;
	}

	public String getDatasetVersion() {
		return datasetVersion;
	}

	public String getServiceVersion() {
		return serviceVersion;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public String getDeploymentEnvironment() {
		return deploymentEnvironment;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getGeneratedAt() {
		return generatedAt;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public Long getTotalLatencyMs() {
		return totalLatencyMs;
	}

	public Long getCandidateGenerationLatencyMs() {
		return candidateGenerationLatencyMs;
	}

	public Long getOntologyLatencyMs() {
		return ontologyLatencyMs;
	}

	public Long getFeatureLookupLatencyMs() {
		return featureLookupLatencyMs;
	}

	public Long getRankingLatencyMs() {
		return rankingLatencyMs;
	}

	public Long getOptimizationLatencyMs() {
		return optimizationLatencyMs;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public JobStage getFailureStage() {
		return failureStage;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public boolean isTimeoutOccurred() {
		return timeoutOccurred;
	}

	public String getFallbackReason() {
		return fallbackReason;
	}

	public FallbackMode getFallbackMode() {
		return fallbackMode;
	}
}
