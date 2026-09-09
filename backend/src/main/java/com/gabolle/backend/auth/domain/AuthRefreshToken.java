package com.gabolle.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_token")
public class AuthRefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID refreshTokenId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private AuthSession session;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected AuthRefreshToken() {
	}

	private AuthRefreshToken(AuthSession session, String tokenHash, Instant expiresAt) {
		this.session = session;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public static AuthRefreshToken issue(AuthSession session, String tokenHash, Instant expiresAt) {
		return new AuthRefreshToken(session, tokenHash, expiresAt);
	}

	@PrePersist
	void initializeCreatedAt() {
		createdAt = Instant.now();
	}

	public boolean isUsableAt(Instant now) {
		return usedAt == null && revokedAt == null && expiresAt.isAfter(now) && session.isUsableAt(now);
	}

	public boolean wasUsed() {
		return usedAt != null;
	}

	public void consume(Instant usedAt) {
		this.usedAt = usedAt;
	}

	public void revoke(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}

	public AuthSession getSession() {
		return session;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}
}
