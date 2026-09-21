package com.gabolle.backend.story.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 저장소에서 못 지운 파일 하나 — {@code storage_cleanup_queue} 한 줄.
 *
 * <p>파일 하나를 못 지웠다고 기록 삭제나 탈퇴를 되돌리면 사용자는 몇 번을 눌러도 안 되는 상태에
 * 갇힌다. 그렇다고 조용히 잊으면 지웠다고 말한 사진이 저장소에 남는다. 그래서 실패를 여기 적고
 * 뒷정리 작업이 다시 지운다. 다 지워지면 행이 빠진다.
 */
@Entity
@Table(name = "storage_cleanup_queue")
public class StorageCleanupEntry {

	public static final String REASON_STORY_DELETED = "STORY_DELETED";
	public static final String REASON_ACCOUNT_DELETED = "ACCOUNT_DELETED";
	public static final String REASON_ORPHAN_UPLOAD = "ORPHAN_UPLOAD";

	@Id
	@Column(name = "storage_key", nullable = false, updatable = false, length = 300)
	private String storageKey;

	@Column(name = "reason", nullable = false, length = 50)
	private String reason;

	@Column(name = "first_failed_at", nullable = false, updatable = false)
	private Instant firstFailedAt;

	@Column(name = "last_attempt_at", nullable = false)
	private Instant lastAttemptAt;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	protected StorageCleanupEntry() {
	}

	public StorageCleanupEntry(String storageKey, String reason, Instant now, String error) {
		this.storageKey = storageKey;
		this.reason = reason;
		this.firstFailedAt = now;
		this.lastAttemptAt = now;
		this.attempts = 1;
		this.lastError = truncate(error);
	}

	public void recordFailure(Instant now, String error) {
		this.lastAttemptAt = now;
		this.attempts++;
		this.lastError = truncate(error);
	}

	private static String truncate(String error) {
		if (error == null) {
			return null;
		}
		return error.length() > 500 ? error.substring(0, 500) : error;
	}

	public String getStorageKey()     { return storageKey; }
	public String getReason()         { return reason; }
	public Instant getFirstFailedAt() { return firstFailedAt; }
	public Instant getLastAttemptAt() { return lastAttemptAt; }
	public int getAttempts()          { return attempts; }
	public String getLastError()      { return lastError; }
}
