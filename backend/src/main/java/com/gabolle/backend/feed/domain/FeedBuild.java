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
 * 미리 만들어 둔 피드 한 세대 (S15P21E201-632).
 *
 * <h2>이 클래스가 있는 이유 — 다시 만드는 동안의 빈 화면</h2>
 *
 * 미리 계산해 두는 방식의 진짜 어려움은 "언제 계산하나" 가 아니라
 * <b>"다시 계산하는 동안 어떻게 하나"</b> 다.
 *
 * <p>순진한 방법은 옛 줄을 지우고 새 줄을 넣는 것인데, 그 사이에 사람이 앱을 켜면
 * 빈 화면이나 반쪽 화면을 본다. 밤에 도는 배치라도 시차가 있는 사용자에게는 한낮이다.
 *
 * <p>그래서 <b>지우고 다시 쓰지 않는다.</b> 새 세대를 옆에 통째로 만들고, 다 만든 뒤에
 * "현재는 이것" 이라는 표시만 옮긴다. 배포에서 쓰는 blue-green(<b>새 판을 옆에 다 띄운 뒤
 * 접속만 옮기는 방식</b>)과 같은 생각이다.
 *
 * <pre>
 *   1) open()          세대를 BUILDING 으로 연다
 *   2) 줄을 다 넣는다    ← 이 동안 읽기는 옛 세대를 본다. 영향 0
 *   3) 한 트랜잭션에서   옛 READY.supersede()  +  새 세대.markReady()
 *   4) 옛 세대는 나중에 지운다 (줄은 ON DELETE CASCADE 로 같이 사라진다)
 * </pre>
 *
 * <p>🔴 3)에서 둘이 동시에 READY 인 순간이 없어야 한다. 그것을 DB 의 조건부 UNIQUE 색인
 * {@code uq_feed_build_ready} 가 막는다. 순서를 어기면 애플리케이션이 아니라
 * <b>DB 가</b> 거절한다 — 조심성에 맡기지 않는다.
 *
 * <h2>왜 만든 재료의 판을 다 적어 두는가</h2>
 *
 * 미리 만든 것은 <b>반드시 낡는다.</b> 무엇으로 만들었는지 안 적어 두면 낡았다는 사실
 * 자체를 알 수 없다. {@code recommendation_job} 이 같은 이유로 같은 버전 칸들을 가지고
 * 있고(S15P21E201-543), 값을 못 구하면 'unknown' 을 넣는 대신 성공으로 남기지 못하게
 * DB 가 막는 것도 같다.
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
	 * 추천 엔진을 거쳐 만들었다면 그 요청 번호.
	 *
	 * <p>사람이 "왜 이게 떴지" 를 물었을 때 {@code recommendation_candidate} 까지 따라가
	 * <b>탈락한 후보들까지</b> 볼 수 있다. 그 연결이 없으면 피드는 설명할 수 없는 결과가 된다.
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
	 * 이 세대를 언제까지 믿을 것인가.
	 *
	 * <p>🔴 {@code null} 은 "만료 없음" 이 아니라 <b>"아직 안 정했다"</b> 다. READY 세대에는
	 * 반드시 값이 있어야 하고 DB 가 그것을 요구한다({@code ck_feed_build_ready_shape}).
	 * 만료 없는 피드를 허용하면 한 번 만든 뒤 영영 안 고쳐지는 사용자가 생기는데,
	 * 그 사람에게는 앱이 죽은 것처럼 보인다.
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

	/** 새 세대를 연다. 이 시점에는 아무도 이 세대를 읽지 않는다. */
	public static FeedBuild open(UUID userId, FeedSurface surface, OffsetDateTime createdAt) {
		return new FeedBuild(UUID.randomUUID(), userId, surface, createdAt);
	}

	/**
	 * 이 세대를 현재로 만든다.
	 *
	 * <p>🔴 <b>같은 트랜잭션에서 앞선 READY 세대에 {@link #supersede()} 를 먼저 찍어야 한다.</b>
	 * 안 하면 DB 가 거부한다({@code uq_feed_build_ready}). 이 메서드는 그 순서를 대신
	 * 지켜 주지 않는다 — 앞선 세대를 알아서 찾아 내리게 만들면, 부르는 쪽이 그 사실을
	 * 모른 채 트랜잭션 밖에서 부르는 날이 온다.
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

	/** 만들다 실패했다. 옛 READY 세대는 그대로 살아 있으므로 화면은 안 비는다. */
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
	 * 만료 시각이 지났는가.
	 *
	 * <p>🔴 <b>낡았다고 안 보여주는 것이 아니다.</b> 낡은 화면이 빈 화면보다 낫다.
	 * 이 값은 응답에 실려 나가고, 앱이 "새로고침" 을 띄울지 정하는 데 쓴다.
	 *
	 * <p>🔴 <b>취향이 바뀐 것은 여기서 판정하지 않는다.</b> 그것은 취향이 바뀌는 <b>그 순간에</b>
	 * 이 세대의 만료를 앞당기는 것으로 처리해야 한다 — 읽을 때 취향 판을 다시 조회하면
	 * 조회가 하나 늘고, 그게 이 티켓이 없애려던 바로 그 비용이다.
	 * <b>그 무효화 코드는 아직 없다</b> (MR 설명의 "안 한 것" 참고).
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
