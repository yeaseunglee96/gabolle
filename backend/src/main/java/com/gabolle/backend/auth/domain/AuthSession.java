package com.gabolle.backend.auth.domain;

import com.gabolle.backend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_session", indexes = @Index(name = "idx_auth_session_user_expires", columnList = "user_id, expires_at"))
public class AuthSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID sessionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "token_family_id", nullable = false)
	private UUID tokenFamilyId;

	@Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
	private String refreshTokenHash;

	@Column(name = "device_id", length = 255)
	private String deviceId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected AuthSession() {
	}

	private AuthSession(AppUser user, UUID tokenFamilyId, String refreshTokenHash, String deviceId, Instant expiresAt) {
		this.user = user;
		this.tokenFamilyId = tokenFamilyId;
		this.refreshTokenHash = refreshTokenHash;
		this.deviceId = deviceId;
		this.expiresAt = expiresAt;
	}

	public static AuthSession issue(AppUser user, UUID tokenFamilyId, String refreshTokenHash, String deviceId,
			Instant expiresAt) {
		return new AuthSession(user, tokenFamilyId, refreshTokenHash, deviceId, expiresAt);
	}

	@PrePersist
	void initializeCreatedAt() {
		createdAt = Instant.now();
	}

	public void revoke(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}

	public void rotate(String refreshTokenHash, Instant expiresAt) {
		this.refreshTokenHash = refreshTokenHash;
		this.expiresAt = expiresAt;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public AppUser getUser() {
		return user;
	}

	public UUID getTokenFamilyId() {
		return tokenFamilyId;
	}

	public String getRefreshTokenHash() {
		return refreshTokenHash;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isUsableAt(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}
}
