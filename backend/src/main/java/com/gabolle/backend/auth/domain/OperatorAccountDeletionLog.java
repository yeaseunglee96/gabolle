package com.gabolle.backend.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 운영자 대리 탈퇴 처리 기록 한 줄 (S15P21E201-1647). 이메일 원문을 담지 않는다 — 해시만.
 *
 * <p>{@code app_user} 를 가리키는 외래키가 없다. 탈퇴의 결과 기록이라 계정 행에 매이면 안 된다(마이그레이션 머리말).
 */
@Entity
@Table(name = "operator_account_deletion_log")
public class OperatorAccountDeletionLog {

	/** 계정 하나를 찾아 지웠다. */
	public static final String DELETED = "DELETED";

	/** 쓸 수 있는 계정이 없었다. */
	public static final String NOT_FOUND = "NOT_FOUND";

	/** 이메일이 서로 다른 계정 둘 이상을 가리켜 아무것도 지우지 않았다. */
	public static final String AMBIGUOUS = "AMBIGUOUS";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id")
	private Long logId;

	@Column(name = "request_ref", nullable = false, length = 120)
	private String requestRef;

	@Column(name = "operator_name", nullable = false, length = 60)
	private String operatorName;

	@Column(name = "email_sha256", nullable = false, length = 64)
	private String emailSha256;

	@Column(name = "deleted_user_id")
	private UUID deletedUserId;

	@Column(name = "outcome", nullable = false, length = 20)
	private String outcome;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected OperatorAccountDeletionLog() {
	}

	public OperatorAccountDeletionLog(String requestRef, String operatorName, String emailSha256,
			UUID deletedUserId, String outcome, Instant createdAt) {
		this.requestRef = requestRef;
		this.operatorName = operatorName;
		this.emailSha256 = emailSha256;
		this.deletedUserId = deletedUserId;
		this.outcome = outcome;
		this.createdAt = createdAt;
	}

	public Long getLogId() {
		return this.logId;
	}

	public String getRequestRef() {
		return this.requestRef;
	}

	public String getOperatorName() {
		return this.operatorName;
	}

	public String getEmailSha256() {
		return this.emailSha256;
	}

	public UUID getDeletedUserId() {
		return this.deletedUserId;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
