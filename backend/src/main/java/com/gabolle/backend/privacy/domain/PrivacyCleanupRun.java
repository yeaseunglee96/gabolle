package com.gabolle.backend.privacy.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 개인정보 자동 정리 배치(S15P21E201-357 · -166) 한 번의 실행 기록.
 *
 * <p>배치가 지운 행 자체는 여기 없다 — 지운 시점에 이미 사라진다. 이 표는 "언제 돌았고 무엇을
 * 몇 건 지웠고 실패했는가" 만 남긴다. {@link PrivacyCleanupStatus#FAILED} 로 끝났는데 아무도
 * 모르고 넘어가는 것을 막는 것이 이 표의 유일한 목적이다.
 */
@Entity
@Table(name = "privacy_cleanup_run")
public class PrivacyCleanupRun {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID runId;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private PrivacyCleanupStatus status;

	@Column(name = "expired_sessions_deleted", nullable = false)
	private int expiredSessionsDeleted;

	@Column(name = "expired_refresh_tokens_deleted", nullable = false)
	private int expiredRefreshTokensDeleted;

	@Column(name = "expired_events_deleted", nullable = false)
	private int expiredEventsDeleted;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	protected PrivacyCleanupRun() {
	}

	private PrivacyCleanupRun(Instant startedAt) {
		this.startedAt = startedAt;
		this.status = PrivacyCleanupStatus.RUNNING;
	}

	public static PrivacyCleanupRun start(Instant startedAt) {
		return new PrivacyCleanupRun(startedAt);
	}

	public void succeed(Instant finishedAt, int expiredSessionsDeleted, int expiredRefreshTokensDeleted,
			int expiredEventsDeleted) {
		this.finishedAt = finishedAt;
		this.status = PrivacyCleanupStatus.SUCCEEDED;
		this.expiredSessionsDeleted = expiredSessionsDeleted;
		this.expiredRefreshTokensDeleted = expiredRefreshTokensDeleted;
		this.expiredEventsDeleted = expiredEventsDeleted;
	}

	public void fail(Instant finishedAt, String errorMessage) {
		this.finishedAt = finishedAt;
		this.status = PrivacyCleanupStatus.FAILED;
		this.errorMessage = errorMessage;
	}

	public UUID getRunId() {
		return runId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public PrivacyCleanupStatus getStatus() {
		return status;
	}

	public int getExpiredSessionsDeleted() {
		return expiredSessionsDeleted;
	}

	public int getExpiredRefreshTokensDeleted() {
		return expiredRefreshTokensDeleted;
	}

	public int getExpiredEventsDeleted() {
		return expiredEventsDeleted;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
