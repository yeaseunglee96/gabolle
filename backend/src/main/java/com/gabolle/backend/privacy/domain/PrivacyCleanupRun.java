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
 * 개인정보 자동 정리 배치 한 번의 실행 기록.
 *
 * <p>언제 돌았고 무엇을 몇 건 지웠고 실패했는지만 남긴다. {@link PrivacyCleanupStatus#FAILED}
 * 로 끝난 것을 아무도 모르고 넘어가지 않게 하는 것이 목적이다.
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

	/**
	 * 지운 조회 낱개 행 수다. 글의 누적 조회수는 안 내려간다.
	 */
	@Column(name = "story_views_deleted", nullable = false)
	private int storyViewsDeleted;

	/** 지운 링크 복사 낱개 행 수. 누적 인용수는 안 내려간다. */
	@Column(name = "story_link_copies_deleted", nullable = false)
	private int storyLinkCopiesDeleted;

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

	/**
	 * 건수를 낱낱의 int 로 받지 않고 {@link PrivacyCleanupResult} 하나로 받는다. 같은 타입의
	 * 인자가 다섯이면 순서를 한 번만 바꿔 넣어도 컴파일이 통과하고 기록 표의 숫자가 조용히
	 * 뒤바뀐다.
	 */
	public void succeed(Instant finishedAt, PrivacyCleanupResult result) {
		this.finishedAt = finishedAt;
		this.status = PrivacyCleanupStatus.SUCCEEDED;
		this.expiredSessionsDeleted = result.sessionsDeleted();
		this.expiredRefreshTokensDeleted = result.refreshTokensDeleted();
		this.expiredEventsDeleted = result.eventsDeleted();
		this.storyViewsDeleted = result.storyViewsDeleted();
		this.storyLinkCopiesDeleted = result.storyLinkCopiesDeleted();
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

	public int getStoryViewsDeleted() {
		return storyViewsDeleted;
	}

	public int getStoryLinkCopiesDeleted() {
		return storyLinkCopiesDeleted;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
