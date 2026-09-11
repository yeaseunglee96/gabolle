package com.gabolle.backend.preference.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자 취향을 온톨로지 개념별 숫자로 접어 둔 판 하나 (S15P21E201-632).
 *
 * <h2>이미 있는 {@code preference_snapshot} 과 무엇이 다른가</h2>
 *
 * <table border="1">
 * <tr><th></th><th>preference_snapshot (S15P21E201-554)</th><th>이 표</th></tr>
 * <tr><td>담는 것</td><td>사람이 화면에서 고른 답 그대로</td><td>그 답과 행동을 숫자로 접은 것</td></tr>
 * <tr><td>있는 이유</td><td><b>재현</b> — 그때 무엇을 보고 추천했나</td><td><b>속도</b> — 온톨로지를 다시 안 훑으려고</td></tr>
 * <tr><td>바뀌는 때</td><td>사람이 설문을 다시 낼 때</td><td>설문이 바뀌거나 행동이 쌓였을 때</td></tr>
 * </table>
 *
 * <p>🔴 <b>원본을 대신하는 것이 아니다. 둘 다 있어야 한다.</b> 접는 규칙은 앞으로 여러 번
 * 바뀔 텐데, 그때마다 원본에서 다시 접어야 한다. 원본이 없으면 다시 접을 수가 없다.
 *
 * <h2>왜 고치지 않고 판을 새로 만드는가</h2>
 *
 * 과거 추천이 <b>어느 벡터로</b> 나왔는지 되짚을 수 있어야 하기 때문이다. 같은 행을
 * 덮어쓰면 어제의 추천을 오늘의 값으로 설명하게 되고, 그건 설명이 아니라 지어내기다.
 * 그래서 새 판을 만들고 옛 판에는 {@code supersededAt} 만 찍는다 —
 * {@code ItineraryVersion} 이 일정에 대해 하는 것과 같다.
 *
 * <p>한 사람에게 {@code supersededAt} 이 비어 있는 행은 <b>최대 하나</b>다.
 * 그것을 DB 의 조건부 UNIQUE 색인({@code uq_user_taste_vector_current})이 막는다 —
 * 애플리케이션의 조심성에 맡기지 않는다.
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

	/**
	 * 어느 설문 답에서 출발했는가. 설문을 아직 안 받은 사용자(행동만으로 만든 판)는 비어 있다.
	 *
	 * <p>🔴 이 연결이 없으면 접은 값이 어디서 왔는지 영영 못 되짚는다.
	 */
	@Column(name = "source_preference_snapshot_id", updatable = false)
	private UUID sourcePreferenceSnapshotId;

	@Column(name = "observed_event_count", nullable = false)
	private int observedEventCount;

	/**
	 * 어느 시점까지의 행동을 반영했는가.
	 *
	 * <p>🔴 다음 판을 만들 때 여기서부터 이어 붙인다. 이 값이 없으면 같은 행동을 두 번
	 * 세거나 빠뜨리는데, 둘 다 조용히 일어난다. DB 가
	 * {@code ck_user_taste_vector_observed_until} 로 막는다 — 행동을 반영했다면 반드시 있다.
	 */
	@Column(name = "observed_until")
	private OffsetDateTime observedUntil;

	/**
	 * 어떤 방식으로 접었는가.
	 *
	 * <p>🔴 접는 규칙이 바뀌면 같은 답에서 다른 숫자가 나온다. 이 값이 없으면 두 사람의
	 * 벡터가 같은 잣대로 만들어진 것인지 알 수 없어서, 비교 자체가 무의미해진다.
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
	 * 새 판을 연다.
	 *
	 * <p>🔴 이 판을 <b>현재</b> 로 만드는 것은 이 메서드가 아니다. 앞선 판에
	 * {@link #supersede(OffsetDateTime)} 를 찍는 것과 같은 트랜잭션이어야 하고, 안 그러면
	 * DB 가 거부한다({@code uq_user_taste_vector_current}). 그 순서를 지키는 코드는
	 * 벡터를 실제로 계산하는 쪽의 몫이고 <b>이 티켓 범위가 아니다.</b>
	 */
	public static UserTasteVector open(UUID userId, int version, UUID sourcePreferenceSnapshotId,
			int observedEventCount, OffsetDateTime observedUntil, String vectorVersion, String ontologyVersion,
			OffsetDateTime createdAt) {
		return new UserTasteVector(UUID.randomUUID(), userId, version, sourcePreferenceSnapshotId, observedEventCount,
				observedUntil, vectorVersion, ontologyVersion, createdAt);
	}

	/**
	 * 성분은 그대로 두고 "어디까지 봤는가" 만 앞으로 옮긴다 — MLOps Phase 1.
	 *
	 * <p>🔴 <b>판을 새로 만들지 않는 경우가 있다.</b> 설문도 안 바뀌고 새 행동도 없었다면
	 * 새 판은 앞 판과 성분이 글자 하나 다르지 않다. 그런 판을 날마다 쌓으면
	 * {@code version} 이 아무것도 뜻하지 않게 되고, "이 추천은 3판으로 나왔다" 가 정보가
	 * 아니게 된다.
	 *
	 * <p>그래도 <b>본 구간은 넓어졌다.</b> 그 사실을 안 적으면 다음 배치가 같은 구간을 다시
	 * 훑는다 — 결과는 같은데 일은 두 번 한다.
	 *
	 * <p>이것이 과거를 고치는 것이 아닌 이유: 이 값은 "무엇을 좋아하는가" 가 아니라
	 * "어디까지 확인했는가" 다. 확인한 구간이 넓어진 것은 사실이고, 사실을 적는 것은 재현을
	 * 깨지 않는다. 성분({@code user_taste_weight})은 한 줄도 안 바뀐다.
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
