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

import com.gabolle.backend.trip.domain.TripConstraint;

/**
 * {@code constraint_answer} 표 매핑.
 *
 * <p>알레르기 자유 입력 전용 칸({@code other_allergy_ciphertext}·{@code encryption_key_version}·
 * {@code encryption_nonce}·{@code cross_contact_policy}·{@code verification_policy})은 매핑하지
 * 않는다 — 자유 입력은 {@code constraintKey == "OTHER"} 일 때 생성자 단계에서 이미 거부되므로
 * 여기 도달하는 것은 전부 코드로 된 값이다.
 */
@Entity
@Table(name = "constraint_answer")
public class ConstraintAnswerJpaEntity {

	@Id
	@Column(name = "constraint_answer_id")
	private UUID constraintAnswerId;

	@Column(name = "constraint_snapshot_id", nullable = false, updatable = false)
	private UUID constraintSnapshotId;

	@Column(name = "constraint_type", nullable = false, updatable = false, length = 30)
	private String constraintType;

	@Column(name = "constraint_key", nullable = false, updatable = false, length = 40)
	private String constraintKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "value", updatable = false)
	private String valueJson;

	@Column(name = "hard", nullable = false, updatable = false)
	private boolean hard;

	@Enumerated(EnumType.STRING)
	@Column(name = "answer_status", nullable = false, length = 10, updatable = false)
	private TripConstraint.AnswerStatus answerStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "diet_requirement", length = 10, updatable = false)
	private TripConstraint.DietRequirement dietRequirement;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ConstraintAnswerJpaEntity() {
		// JPA 전용
	}

	ConstraintAnswerJpaEntity(UUID constraintAnswerId, UUID constraintSnapshotId, String constraintType,
			String constraintKey, String valueJson, boolean hard, TripConstraint.AnswerStatus answerStatus,
			TripConstraint.DietRequirement dietRequirement, OffsetDateTime createdAt) {
		this.constraintAnswerId = constraintAnswerId;
		this.constraintSnapshotId = constraintSnapshotId;
		this.constraintType = constraintType;
		this.constraintKey = constraintKey;
		this.valueJson = valueJson;
		this.hard = hard;
		this.answerStatus = answerStatus;
		this.dietRequirement = dietRequirement;
		this.createdAt = createdAt;
	}

	UUID constraintAnswerId() { return constraintAnswerId; }
	UUID constraintSnapshotId() { return constraintSnapshotId; }
	String constraintType() { return constraintType; }
	String constraintKey() { return constraintKey; }
	String valueJson() { return valueJson; }
	boolean hard() { return hard; }
	TripConstraint.AnswerStatus answerStatus() { return answerStatus; }
	TripConstraint.DietRequirement dietRequirement() { return dietRequirement; }
}
