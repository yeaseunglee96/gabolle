package com.gabolle.backend.auth.domain;

import com.gabolle.backend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "local_credential")
public class LocalCredential {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID localCredentialId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private AppUser user;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "password_changed_at", nullable = false)
	private Instant passwordChangedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected LocalCredential() {
	}

	private LocalCredential(AppUser user, String email, String passwordHash) {
		this.user = user;
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public static LocalCredential create(AppUser user, String email, String passwordHash) {
		return new LocalCredential(user, email, passwordHash);
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
		passwordChangedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public void markEmailVerified(Instant verifiedAt) {
		emailVerifiedAt = verifiedAt;
	}

	public void changePassword(String passwordHash, Instant changedAt) {
		this.passwordHash = passwordHash;
		this.passwordChangedAt = changedAt;
	}

	public UUID getLocalCredentialId() {
		return localCredentialId;
	}

	public AppUser getUser() {
		return user;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public Instant getPasswordChangedAt() {
		return passwordChangedAt;
	}
}
