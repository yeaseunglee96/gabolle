package com.gabolle.backend.trip.infra;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.gabolle.backend.trip.domain.PreferenceSnapshot;

/**
 * {@code preference_answer} 표 매핑. 도메인 {@link PreferenceSnapshot.PreferenceAnswer} 에는 id 가
 * 없어 저장할 때마다 새 UUID 를 만든다 — 답은 불변 스냅샷의 일부라 이 id 로 다시 찾을 일이 없다.
 */
@Entity
@Table(name = "preference_answer")
public class PreferenceAnswerJpaEntity {

	@Id
	@Column(name = "preference_answer_id")
	private UUID preferenceAnswerId;

	@Column(name = "preference_snapshot_id", nullable = false, updatable = false)
	private UUID preferenceSnapshotId;

	@Column(name = "dimension", nullable = false, updatable = false, length = 50)
	private String dimension;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "value", updatable = false)
	private String valueJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "answer_status", nullable = false, length = 10, updatable = false)
	private PreferenceSnapshot.AnswerStatus answerStatus;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected PreferenceAnswerJpaEntity() {
		// JPA 전용
	}

	PreferenceAnswerJpaEntity(UUID preferenceAnswerId, UUID preferenceSnapshotId, String dimension,
			String valueJson, PreferenceSnapshot.AnswerStatus answerStatus, OffsetDateTime createdAt) {
		this.preferenceAnswerId = preferenceAnswerId;
		this.preferenceSnapshotId = preferenceSnapshotId;
		this.dimension = dimension;
		this.valueJson = valueJson;
		this.answerStatus = answerStatus;
		this.createdAt = createdAt;
	}

	UUID preferenceSnapshotId() { return preferenceSnapshotId; }
	String dimension() { return dimension; }
	String valueJson() { return valueJson; }
	PreferenceSnapshot.AnswerStatus answerStatus() { return answerStatus; }
}
