package com.gabolle.backend.feed.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 미리 만들어 둔 피드 한 세대.
 *
 * <p>지우고 다시 쓰지 않는다. 새 세대를 옆에 통째로 만들고 다 만든 뒤에 "현재는 이것"
 * 이라는 표시만 옮긴다. 순서는 연다 → 줄을 다 넣는다 → 한 트랜잭션에서 옛 세대
 * {@link #supersede()} 와 새 세대 {@link #markReady} → 옛 세대는 나중에 지운다.
 * 줄을 넣는 동안 읽기는 옛 세대를 보므로 화면이 안 빈다.
 *
 * <p>둘이 동시에 READY 인 순간이 없어야 하고, 그것은 DB 의 조건부 UNIQUE 색인
 * {@code uq_feed_build_ready} 가 막는다.
 *
 * <p>만든 재료와 판 번호를 다 적어 두는 이유는, 미리 만든 것은 반드시 낡는데 무엇으로
 * 만들었는지 없으면 낡았다는 사실 자체를 알 수 없기 때문이다.
 */
@Entity
@Table(name = "feed_build")
public class FeedBuild {

	@Id
	@Column(name = "build_id", nullable = false, updatable = false)
	private UUID buildId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "surface", nullable = false, length = 20, updatable = false)
	private FeedSurface surface;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private FeedBuildStatus status;

	@Column(name = "taste_vector_id")
	private UUID tasteVectorId;

	@Column(name = "constraint_snapshot_id")
	private UUID constraintSnapshotId;

	/**
	 * 추천 엔진을 거쳐 만들었다면 그 요청 번호. 이 연결이 있어야 {@code recommendation_candidate}
	 * 까지 따라가 탈락한 후보까지 볼 수 있다.
	 */
	@Column(name = "source_request_id")
	private UUID sourceRequestId;

	@Column(name = "model_version", length = 100)
	private String modelVersion;

	@Column(name = "feature_version", length = 100)
	private String featureVersion;

	@Column(name = "ontology_version", length = 100)
	private String ontologyVersion;

	@Column(name = "policy_version", length = 100)
	private String policyVersion;

	@Column(name = "dataset_version", length = 100)
	private String datasetVersion;

	@Column(name = "service_version", length = 100)
	private String serviceVersion;

	@Column(name = "entry_count", nullable = false)
	private int entryCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "ready_at")
	private OffsetDateTime readyAt;

	/**
	 * 이 세대를 언제까지 믿을 것인가. {@code null} 은 "만료 없음" 이 아니라 "아직 안 정했다"
	 * 다. READY 세대에는 반드시 값이 있어야 하고 DB 가 요구한다
	 * ({@code ck_feed_build_ready_shape}).
	 */
	@Column(name = "expires_at")
	private OffsetDateTime expiresAt;

	@Column(name = "failure_reason", length = 64)
	private String failureReason;

	protected FeedBuild() {
	}

	private FeedBuild(UUID buildId, UUID userId, FeedSurface surface, OffsetDateTime createdAt) {
		this.buildId = buildId;
		this.userId = userId;
		this.surface = surface;
		this.status = FeedBuildStatus.BUILDING;
		this.entryCount = 0;
		this.createdAt = createdAt;
	}

	/** 새 세대를 연다. 이 시점에는 아무도 읽지 않는다. */
	public static FeedBuild open(UUID userId, FeedSurface surface, OffsetDateTime createdAt) {
		return new FeedBuild(UUID.randomUUID(), userId, surface, createdAt);
	}

	/**
	 * 이 세대를 현재로 만든다. 같은 트랜잭션에서 앞선 READY 세대에 {@link #supersede()} 를
	 * 먼저 찍어야 한다 — 안 하면 DB 가 거부한다({@code uq_feed_build_ready}).
	 * 이 메서드는 그 순서를 대신 지켜 주지 않는다.
	 */
	public void markReady(OffsetDateTime readyAt, OffsetDateTime expiresAt, int entryCount) {
		this.status = FeedBuildStatus.READY;
		this.readyAt = readyAt;
		this.expiresAt = expiresAt;
		this.entryCount = entryCount;
	}

	/** 다음 세대에 자리를 내준다. */
	public void supersede() {
		this.status = FeedBuildStatus.SUPERSEDED;
	}

	/** 만들다 실패했다. 옛 READY 세대는 그대로 살아 있으므로 화면은 안 빈다. */
	public void fail(String reason) {
		this.status = FeedBuildStatus.FAILED;
		this.failureReason = reason;
	}

	/** 무엇으로 만들었는지 박는다. READY 로 올리기 전에 채워야 한다. */
	public void recordInputs(UUID tasteVectorId, UUID constraintSnapshotId, UUID sourceRequestId) {
		this.tasteVectorId = tasteVectorId;
		this.constraintSnapshotId = constraintSnapshotId;
		this.sourceRequestId = sourceRequestId;
	}

	/** 어느 판으로 계산했는지 박는다. READY 로 올리기 전에 채워야 한다. */
	public void recordVersions(String modelVersion, String featureVersion, String ontologyVersion,
			String policyVersion, String datasetVersion, String serviceVersion) {
		this.modelVersion = modelVersion;
		this.featureVersion = featureVersion;
		this.ontologyVersion = ontologyVersion;
		this.policyVersion = policyVersion;
		this.datasetVersion = datasetVersion;
		this.serviceVersion = serviceVersion;
	}

	/**
	 * 만료 시각이 지났는가. 지났다고 안 보여주는 것이 아니라, 이 값이 응답에 실려 나가
	 * 앱이 새로고침을 띄울지 정하는 데 쓰인다.
	 *
	 * <p>취향이 바뀐 것은 여기서 판정하지 않는다. 읽을 때 취향 판을 다시 조회하면 조회가
	 * 하나 늘기 때문이고, 취향이 바뀌는 그 순간에 만료를 앞당기는 것으로 처리해야 한다.
	 * TODO: 그 무효화 코드가 아직 없다.
	 */
	public boolean isExpiredAt(OffsetDateTime now) {
		return this.expiresAt != null && !this.expiresAt.isAfter(now);
	}

	public UUID getBuildId() {
		return this.buildId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public FeedSurface getSurface() {
		return this.surface;
	}

	public FeedBuildStatus getStatus() {
		return this.status;
	}

	public UUID getTasteVectorId() {
		return this.tasteVectorId;
	}

	public UUID getConstraintSnapshotId() {
		return this.constraintSnapshotId;
	}

	public UUID getSourceRequestId() {
		return this.sourceRequestId;
	}

	public String getOntologyVersion() {
		return this.ontologyVersion;
	}

	public String getPolicyVersion() {
		return this.policyVersion;
	}

	public String getDatasetVersion() {
		return this.datasetVersion;
	}

	public String getServiceVersion() {
		return this.serviceVersion;
	}

	public String getModelVersion() {
		return this.modelVersion;
	}

	public String getFeatureVersion() {
		return this.featureVersion;
	}

	public int getEntryCount() {
		return this.entryCount;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getReadyAt() {
		return this.readyAt;
	}

	public OffsetDateTime getExpiresAt() {
		return this.expiresAt;
	}

	public String getFailureReason() {
		return this.failureReason;
	}
}
