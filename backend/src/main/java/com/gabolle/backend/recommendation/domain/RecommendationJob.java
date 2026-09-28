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

	/** 취향 스냅샷 ID. FK 는 아직 없다. */
	@Column(name = "preference_snapshot_id")
	private UUID preferenceSnapshotId;

	/** 제약 스냅샷 ID. FK 는 아직 없다. */
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

	/** 편집 기준이 된 일정 버전. 완료 시 최신과 같을 때만 새 버전을 게시한다. */
	@Column(name = "base_version")
	private Integer baseVersion;

	@Column(name = "retryable", nullable = false)
	private boolean retryable;

	/**
	 * 아래 둘은 비동기 Job 러너가 채운다. 기본값을 넣지 않는다 — 클라이언트가 지켜지지도 않을
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
	 * 온톨로지 판정에 쓰인 정책 버전. {@code ontologyVersion} 과 다르다 — 어휘·규칙이 그대로여도
	 * 정책(무엇을 REQUIRED 로 볼 것인가)이 바뀌면 같은 장소의 판정이 바뀐다.
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

	/**
	 * 이 요청이 개인화 추천으로 끝났는가 Editor's Pick 으로 끝났는가. 기본값이
	 * {@link SourceMode#PERSONALIZED} 이고 {@link #fallBackToEditorialPick} 만 이것을 바꾼다 —
	 * 그러지 않으면 Pick 이 개인화로 집계된다. {@link #fallbackMode} 와는 다른 질문에 답한다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "source_mode", nullable = false, length = 20)
	private SourceMode sourceMode = SourceMode.PERSONALIZED;

	/** 어느 Pick 의 어느 판을 보여줬나. 개인화 추천이면 {@code null} 이다. */
	@Column(name = "editorial_pick_id")
	private UUID editorialPickId;

	/**
	 * 출발지를 대략 1km 칸으로 뭉갠 번호. 정밀 좌표를 담는 칸은 이 표에 없다 — 요청이 받은
	 * 좌표는 거리 계산에만 쓰이고 요청이 끝나면 사라진다.
	 */
	@Column(name = "origin_area_code", length = 32)
	private String originAreaCode;

	/** 그 좌표가 어디서 왔나 — GPS · 수기 입력 · 여행 출발지. */
	@Enumerated(EnumType.STRING)
	@Column(name = "origin_source", length = 20)
	private RequestLocation.LocationSource originSource;

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
		this.jobStatus = JobStatus.PENDING;
		this.jobStage = JobStage.CREATED;
	}

	/**
	 * {@code jobId} 와 {@code requestId} 는 서버가 만든다. 클라이언트가 정하게 두면 같은
	 * request_id 로 남의 요청을 덮어쓰거나 분석 키를 조작할 수 있다.
	 */
	public static RecommendationJob start(UUID jobId, UUID requestId, UUID userId, JobType jobType,
			OffsetDateTime createdAt) {
		if (jobType == null) {
			throw new IllegalArgumentException("jobType 은 필수다 (GB-API-001 4.2 JobDto.type)");
		}
		return new RecommendationJob(jobId, requestId, userId, jobType, createdAt);
	}

	/**
	 * 대기(PENDING)에서 실행(RUNNING)으로. 비동기 러너가 실제 계산을 시작하기 직전에 부른다.
	 *
	 * <p>부르는 쪽이 이어서 저장해야 폴링하는 쪽이 "접수는 됐고 지금 도는 중" 을 볼 수 있다.
	 * 여기서 저장까지 하지 않는 것은 이 엔티티가 트랜잭션·저장소를 몰라야 하기 때문이다.
	 */
	public void markRunning(JobStage stage) {
		if (this.jobStatus != JobStatus.PENDING) {
			throw new IllegalStateException("PENDING 상태에서만 RUNNING 으로 갈 수 있다: " + this.jobStatus);
		}
		this.jobStatus = JobStatus.RUNNING;
		this.jobStage = stage;
		this.progressPercent = stage.percent();
	}

	/**
	 * 도는 중에 단계가 넘어갔다. 진행률은 단계가 들고 있는 값을 그대로 쓴다
	 * ({@link JobStage#percent()}) — 부르는 쪽이 숫자를 정하면 같은 단계가 자리마다 다른
	 * 퍼센트로 나간다.
	 *
	 * <p>진행률은 뒤로 가지 않는다. 단계 이름은 바꾸되 퍼센트는 지금까지의 최대값을 지킨다.
	 * 예외를 던지지 않는 것은 진행률 표시 하나 때문에 추천 계산 전체를 실패시키지 않기 위해서다.
	 *
	 * <p>끝난 작업(성공·실패)에는 아무것도 하지 않는다 — 늦게 도착한 단계 보고가 100%를
	 * 되돌리면 화면이 끝난 일을 다시 도는 것으로 그린다.
	 */
	public void markStage(JobStage stage) {
		if (this.jobStatus != JobStatus.RUNNING) {
			return;
		}
		this.jobStage = stage;
		this.progressPercent = Math.max(this.progressPercent, stage.percent());
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
	 * 일정 조립까지 끝난 뒤의 시간을 적는다 — S15P21E201-1622.
	 *
	 * <p>🔴 왜 따로 있나. {@link #applyLatencies} 는 조립 <b>전에</b> 불린다 — 조립이 {@link #markCompleted} 뒤에
	 * 일어나기 때문이다. 그래서 조립 시간 칸은 늘 비어 있었고 전체 시간에서도 조립이 빠졌다. 운영에서 조립이
	 * 8.3초 걸린 사례(S15P21E201-1621)가 기록에서는 안 보였다.
	 *
	 * @param optimizationMs 조립(편집이면 하루 다시 채우기)에만 든 시간
	 * @param totalMs 요청을 받은 때부터 조립이 끝난 때까지 — 조립 전에 적어 둔 전체 시간을 덮는다
	 */
	public void applyOptimizationLatency(long optimizationMs, long totalMs) {
		this.optimizationLatencyMs = optimizationMs;
		this.totalLatencyMs = totalMs;
	}

	/**
	 * 결과를 만들고 끝났다. 항상 {@link JobStatus#SUCCEEDED} 다.
	 *
	 * <p>대체 경로로 만들었어도 성공이다. 그 사실은 상태가 아니라 {@code fallbackMode} 와
	 * {@code fallbackReason} 이 나타낸다 — JobStatus 에는 FALLBACK 이 없다.
	 */
	public void markCompleted(OffsetDateTime generatedAt, OffsetDateTime completedAt, FallbackMode fallbackMode,
			String fallbackReason) {
		if (this.sourceMode == null) {
			// 옛 행에는 이 칸이 비어 있을 수 있다. Pick 은 이 칸이 생긴 뒤에야 존재하므로
			// 비어 있으면 개인화였던 것이 확실하다.
			this.sourceMode = SourceMode.PERSONALIZED;
		}
		this.generatedAt = generatedAt;
		this.completedAt = completedAt;
		this.fallbackMode = fallbackMode;
		this.fallbackReason = fallbackReason;
		this.jobStatus = JobStatus.SUCCEEDED;
		this.jobStage = JobStage.COMPLETED;
		this.progressPercent = 100;
	}

	/**
	 * 개인화 추천을 못 만들어 Editor's Pick 을 대신 내보낸다.
	 *
	 * <p>이 호출만으로는 아직 성공이 아니다 — 부르는 쪽이 이어서 {@link #markCompleted} 를
	 * {@link FallbackMode#EDITORIAL_PICK} 과 함께 불러야 한다. {@code markCompleted} 안에
	 * 합치지 않은 것은 Pick 을 골랐지만 그 장소가 전부 하드 제약에 걸려 실패로 끝나는 경로가
	 * 있기 때문이다. 그때도 Pick 을 시도했다는 사실은 남아야 한다.
	 *
	 * @param editorialPickId 어느 Pick 의 어느 판이었나. {@code editorial_pick} 은 판마다
	 *     다른 행이라 이 하나로 이름과 판이 함께 따라온다
	 */
	public void fallBackToEditorialPick(UUID editorialPickId) {
		if (editorialPickId == null) {
			throw new IllegalArgumentException(
					"editorialPickId 는 필수다 — 어느 Pick 이었는지 없으면 결과를 되짚을 수 없다");
		}
		this.sourceMode = SourceMode.EDITORIAL_PICK;
		this.editorialPickId = editorialPickId;
	}

	/**
	 * 이번 요청이 어느 칸에서 왔는지 남긴다. {@link RequestLocation} 을 그대로 받아서 여기서
	 * 뭉갠다 — 부르는 쪽이 칸 번호를 만들어 넘기게 하면 뭉개는 규칙이 호출부마다 생기고
	 * 그중 하나가 정밀 좌표를 그대로 넣는 날이 온다.
	 */
	public void applyOrigin(RequestLocation location) {
		if (location == null) {
			return;
		}
		this.originAreaCode = location.areaCode();
		this.originSource = location.source();
	}

	public String getOriginAreaCode() {
		return this.originAreaCode;
	}

	public RequestLocation.LocationSource getOriginSource() {
		return this.originSource;
	}

	public SourceMode getSourceMode() {
		return (this.sourceMode == null) ? SourceMode.PERSONALIZED : this.sourceMode;
	}

	public UUID getEditorialPickId() {
		return this.editorialPickId;
	}

	/**
	 * 실패 종료. {@code errorCode} 없이 FAILED 로 남길 수 없다 — DB CHECK 도 같은 것을 요구한다.
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

	/**
	 * 추천이 실제로 만든 일정을 이 Job 에 붙인다.
	 *
	 * <p>{@code resourceType}·{@code resourceId} 는 건드리지 않는다. 그 둘은 이 요청이 무엇에
	 * 대한 것이었나라는 입력이지 결과 포인터가 아니다 — 덮어쓰면 이 Job 이 원래 여행(TRIP)
	 * 대상이었다는 사실이 지워진다.
	 *
	 * <p>{@code itineraryId}·{@code itineraryVersion} 은 {@link #applyRequestContext} 도 쓰는
	 * 같은 칸이지만 뜻이 다르다. {@code ITINERARY_GENERATION} 이면 그 시점에 일정이 없어
	 * 여기서 적는 값이 새로 만든 일정이고, 편집 Job 이면 그쪽 값이 입력(무엇을 보고
	 * 편집했나)이고 여기 {@code itineraryVersion} 이 출력(실제로 게시된 새 판)이다.
	 */
	public void attachItinerary(String itineraryId, int version) {
		this.itineraryId = UUID.fromString(itineraryId);
		this.itineraryVersion = version;
	}

	/**
	 * {@code SUCCEEDED} 인 {@code ITINERARY_GENERATION}·{@code ITEM_REMOVE}·
	 * {@code ITINERARY_RECALCULATE} Job 은 itineraryId·itineraryVersion 이 있어야 한다 —
	 * {@code ck_recommendation_job_result_present}(DB CHECK)의 자바 쪽 쌍둥이다. 애플리케이션
	 * 에서도 보는 이유는 DB 제약 위반의 스택이 JDBC 안쪽에서 끊겨 어느 코드가 그랬는지
	 * 못 가리키기 때문이다.
	 *
	 * <p>이 검사는 {@link #markCompleted} 안에 둘 수 없다. 실제 호출 순서가 markCompleted →
	 * 일정 조립 → attachItinerary → 저장이라 markCompleted 시점에는 itineraryId 가 정상적으로
	 * 비어 있다. 저장 직전({@code RecommendationRecorder.recordWithItinerary})에서만 부른다.
	 */
	public void assertItineraryAttachedIfRequired() {
		boolean requiresItinerary = this.jobType == JobType.ITINERARY_GENERATION
				|| this.jobType == JobType.ITEM_REMOVE
				|| this.jobType == JobType.ITINERARY_RECALCULATE;
		if (requiresItinerary && this.jobStatus == JobStatus.SUCCEEDED
				&& (this.itineraryId == null || this.itineraryVersion == null)) {
			throw new IllegalStateException(
					"SUCCEEDED 인 " + this.jobType + " Job 은 itineraryId·itineraryVersion 이 있어야 한다: jobId="
							+ this.jobId);
		}
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
