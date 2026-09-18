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

	/**
	 * 🔴 S15P21E201-1216 — 지운 <b>조회 낱개</b> 행 수다. 글의 누적 조회수는 안 내려간다.
	 * 자세한 이유는 {@link PrivacyCleanupResult} 주석에 있다.
	 */
	@Column(name = "story_views_deleted", nullable = false)
	private int storyViewsDeleted;

	/** 🔴 S15P21E201-1216 — 지운 <b>링크 복사 낱개</b> 행 수. 누적 인용수는 안 내려간다. */
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
	 * 🔴 S15P21E201-1216 — 건수를 <b>낱낱의 int 로 받지 않고 {@link PrivacyCleanupResult} 하나로</b>
	 * 받는다. 카테고리가 셋에서 다섯이 되면서 같은 자리에 같은 타입의 인자가 다섯 개 늘어서는데,
	 * 그 상태에서 <b>순서를 한 번만 바꿔 넣어도 컴파일이 통과하고 검사도 대개 통과한다</b> —
	 * 기록 표의 숫자가 조용히 서로 바뀐다. 이름 있는 칸으로 받으면 그 실수가 아예 불가능하다.
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
