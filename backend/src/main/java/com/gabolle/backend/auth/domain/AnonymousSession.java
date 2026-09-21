package com.gabolle.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 가입 안 한 사람의 출입증. 원본 문자열은 여기 없다 — {@code tokenHash} 는 되돌릴 수 없는 값이고,
 * 발급 때 응답으로만 나간 원본과 이 표를 잇는 유일한 끈이다.
 */
@Entity
@Table(name = "anonymous_session",
		indexes = @Index(name = "idx_anonymous_session_last_seen", columnList = "last_seen_at"))
public class AnonymousSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	protected AnonymousSession() {
	}

	private AnonymousSession(String tokenHash, Instant now) {
		this.tokenHash = tokenHash;
		this.createdAt = now;
		this.lastSeenAt = now;
	}

	public static AnonymousSession issue(String tokenHash, Instant now) {
		return new AnonymousSession(tokenHash, now);
	}

	/** 이 출입증으로 요청이 다시 왔다는 뜻이다 — 마지막 접속 시각만 갱신한다. */
	public void touch(Instant now) {
		this.lastSeenAt = now;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}
}
