package com.gabolle.backend.preference.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자 취향을 온톨로지 개념별 숫자로 접어 둔 판 하나. 온톨로지를 매번 다시 안 훑으려고 있고,
 * 원본인 {@code preference_snapshot} 을 대신하지 않는다 — 접는 규칙이 바뀌면 원본에서 다시
 * 접어야 한다.
 *
 * <p>고치지 않고 판을 새로 만든다. 같은 행을 덮어쓰면 어제의 추천을 오늘의 값으로 설명하게
 * 되므로, 새 판을 만들고 옛 판에는 {@code supersededAt} 만 찍는다.
 *
 * <p>한 사람에게 {@code supersededAt} 이 비어 있는 행은 최대 하나다. 애플리케이션의 조심성이
 * 아니라 DB 의 조건부 UNIQUE 색인이 그것을 막는다.
 */
@Entity
@Table(name = "user_taste_vector")
public class UserTasteVector {

	@Id
	@Column(name = "taste_vector_id", nullable = false, updatable = false)
	private UUID tasteVectorId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/**
	 * 판 번호. UUID 에는 순서가 없어 "이게 최신인가" 를 판정할 수 없으므로, 겹치지 않는
	 * 키(UUID)와 순서를 세는 값(정수)을 둘 다 둔다 — {@code trip.version} 과 같은 모양.
	 */
	@Column(name = "version", nullable = false, updatable = false)
	private int version;

	/** 어느 설문 답에서 출발했는가. 행동만으로 만든 판은 비어 있다. */
	@Column(name = "source_preference_snapshot_id", updatable = false)
	private UUID sourcePreferenceSnapshotId;

	@Column(name = "observed_event_count", nullable = false)
	private int observedEventCount;

	/**
	 * 어느 시점까지의 행동을 반영했는가. 다음 판을 만들 때 여기서부터 이어 붙이므로, 없으면
	 * 같은 행동을 두 번 세거나 빠뜨린다. 행동을 반영했다면 반드시 있다 — DB 가 막는다.
	 */
	@Column(name = "observed_until")
	private OffsetDateTime observedUntil;

	/**
	 * 어떤 방식으로 접었는가. 규칙이 바뀌면 같은 답에서 다른 숫자가 나오므로, 이 값이 없으면
	 * 두 벡터가 같은 잣대로 만들어진 것인지 알 수 없어 비교가 무의미해진다.
	 */
	@Column(name = "vector_version", nullable = false, length = 100, updatable = false)
	private String vectorVersion;

	@Column(name = "ontology_version", nullable = false, length = 100, updatable = false)
	private String ontologyVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** 비어 있으면 현재 판이다. 다음 판이 나오면 찍힌다 — 행을 지우지 않는다. */
	@Column(name = "superseded_at")
	private OffsetDateTime supersededAt;

	protected UserTasteVector() {
	}

	private UserTasteVector(UUID tasteVectorId, UUID userId, int version, UUID sourcePreferenceSnapshotId,
			int observedEventCount, OffsetDateTime observedUntil, String vectorVersion, String ontologyVersion,
			OffsetDateTime createdAt) {
		this.tasteVectorId = tasteVectorId;
		this.userId = userId;
		this.version = version;
		this.sourcePreferenceSnapshotId = sourcePreferenceSnapshotId;
		this.observedEventCount = observedEventCount;
		this.observedUntil = observedUntil;
		this.vectorVersion = vectorVersion;
		this.ontologyVersion = ontologyVersion;
		this.createdAt = createdAt;
	}

	/**
	 * 새 판을 연다. 이 판을 현재로 만드는 것은 이 메서드가 아니다 — 앞선 판에
	 * {@link #supersede(OffsetDateTime)} 를 찍는 것과 같은 트랜잭션이어야 하고, 안 그러면
	 * DB 가 거부한다.
	 */
	public static UserTasteVector open(UUID userId, int version, UUID sourcePreferenceSnapshotId,
			int observedEventCount, OffsetDateTime observedUntil, String vectorVersion, String ontologyVersion,
			OffsetDateTime createdAt) {
		return new UserTasteVector(UUID.randomUUID(), userId, version, sourcePreferenceSnapshotId, observedEventCount,
				observedUntil, vectorVersion, ontologyVersion, createdAt);
	}

	/**
	 * 성분은 그대로 두고 "어디까지 봤는가" 만 앞으로 옮긴다. 설문도 안 바뀌고 새 행동도 없으면
	 * 판을 새로 만들지 않는다 — 성분이 같은 판을 날마다 쌓으면 {@code version} 이 아무것도
	 * 뜻하지 않게 된다. 그래도 본 구간은 적어야 다음 배치가 같은 구간을 다시 안 훑는다.
	 *
	 * <p>이 값은 "무엇을 좋아하는가" 가 아니라 "어디까지 확인했는가" 라서 과거를 고치는 것이
	 * 아니다. 성분({@code user_taste_weight})은 한 줄도 안 바뀐다.
	 */
	public void advanceWatermark(OffsetDateTime observedUntil, int additionalEvents) {
		this.observedUntil = observedUntil;
		this.observedEventCount += additionalEvents;
	}

	/** 다음 판에 자리를 내준다. 행은 남는다 — 과거 추천을 설명하려면 필요하다. */
	public void supersede(OffsetDateTime at) {
		this.supersededAt = at;
	}

	public boolean isCurrent() {
		return this.supersededAt == null;
	}

	public UUID getTasteVectorId() {
		return this.tasteVectorId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public int getVersion() {
		return this.version;
	}

	public UUID getSourcePreferenceSnapshotId() {
		return this.sourcePreferenceSnapshotId;
	}

	public int getObservedEventCount() {
		return this.observedEventCount;
	}

	public OffsetDateTime getObservedUntil() {
		return this.observedUntil;
	}

	public String getVectorVersion() {
		return this.vectorVersion;
	}

	public String getOntologyVersion() {
		return this.ontologyVersion;
	}

	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	public OffsetDateTime getSupersededAt() {
		return this.supersededAt;
	}
}
