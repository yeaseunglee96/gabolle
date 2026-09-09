package com.gabolle.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "auth_one_time_token")
public class AuthOneTimeToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID authTokenId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "local_credential_id", nullable = false)
	private LocalCredential localCredential;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AuthTokenPurpose purpose;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected AuthOneTimeToken() {
	}

	private AuthOneTimeToken(LocalCredential localCredential, AuthTokenPurpose purpose, String tokenHash, Instant expiresAt) {
		this.localCredential = localCredential;
		this.purpose = purpose;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public static AuthOneTimeToken issue(LocalCredential localCredential, AuthTokenPurpose purpose, String tokenHash,
			Instant expiresAt) {
		return new AuthOneTimeToken(localCredential, purpose, tokenHash, expiresAt);
	}

	@PrePersist
	void initializeCreatedAt() {
		createdAt = Instant.now();
	}

	public boolean isUsableAt(Instant now) {
		return consumedAt == null && expiresAt.isAfter(now);
	}

	public void consume(Instant consumedAt) {
		this.consumedAt = consumedAt;
	}

	public UUID getAuthTokenId() {
		return authTokenId;
	}

	public LocalCredential getLocalCredential() {
		return localCredential;
	}

	public AuthTokenPurpose getPurpose() {
		return purpose;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
