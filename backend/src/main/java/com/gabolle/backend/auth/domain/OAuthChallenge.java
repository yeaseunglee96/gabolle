package com.gabolle.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_challenge")
public class OAuthChallenge {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID challengeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "state_hash", nullable = false, unique = true, length = 64)
	private String stateHash;

	@Column(name = "nonce_hash", nullable = false, length = 64)
	private String nonceHash;

	@Column(name = "code_challenge_hash", nullable = false, length = 64)
	private String codeChallengeHash;

	@Column(name = "code_challenge_method", nullable = false, length = 10)
	private String codeChallengeMethod;

	@Column(name = "redirect_uri", nullable = false, length = 500)
	private String redirectUri;

	@Column(name = "device_id", length = 255)
	private String deviceId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected OAuthChallenge() {
	}

	private OAuthChallenge(AuthProvider provider, String stateHash, String nonceHash, String codeChallengeHash,
			String codeChallengeMethod, String redirectUri, String deviceId, Instant expiresAt) {
		this.provider = provider;
		this.stateHash = stateHash;
		this.nonceHash = nonceHash;
		this.codeChallengeHash = codeChallengeHash;
		this.codeChallengeMethod = codeChallengeMethod;
		this.redirectUri = redirectUri;
		this.deviceId = deviceId;
		this.expiresAt = expiresAt;
	}

	public static OAuthChallenge issue(AuthProvider provider, String stateHash, String nonceHash, String codeChallengeHash,
			String codeChallengeMethod, String redirectUri, String deviceId, Instant expiresAt) {
		return new OAuthChallenge(provider, stateHash, nonceHash, codeChallengeHash, codeChallengeMethod, redirectUri,
				deviceId, expiresAt);
	}

	@PrePersist
	void initializeCreatedAt() {
		createdAt = Instant.now();
	}

	public boolean isUsableAt(Instant now) {
		return consumedAt == null && expiresAt.isAfter(now);
	}

	public boolean matches(String nonceHash, String codeChallengeHash, String codeChallengeMethod, String redirectUri,
			String deviceId) {
		return this.nonceHash.equals(nonceHash)
				&& this.codeChallengeHash.equals(codeChallengeHash)
				&& this.codeChallengeMethod.equals(codeChallengeMethod)
				&& this.redirectUri.equals(redirectUri)
				&& (this.deviceId == null || this.deviceId.equals(deviceId));
	}

	public void consume(Instant consumedAt) {
		this.consumedAt = consumedAt;
	}
}
