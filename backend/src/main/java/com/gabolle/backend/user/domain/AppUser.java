package com.gabolle.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID userId;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(nullable = false, length = 2)
	private String language;

	@Column(name = "age_verified_at")
	private Instant ageVerifiedAt;

	@Column(name = "age_gate_policy_version", length = 50)
	private String ageGatePolicyVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "personalization_mode", nullable = false, length = 30)
	private PersonalizationMode personalizationMode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected AppUser() {
	}

	private AppUser(String displayName, String language, Instant ageVerifiedAt, String ageGatePolicyVersion,
			PersonalizationMode personalizationMode, UserStatus status) {
		this.displayName = displayName;
		this.language = language;
		this.ageVerifiedAt = ageVerifiedAt;
		this.ageGatePolicyVersion = ageGatePolicyVersion;
		this.personalizationMode = personalizationMode;
		this.status = status;
	}

	public static AppUser register(String displayName, String language, Instant ageVerifiedAt,
			String ageGatePolicyVersion, PersonalizationMode personalizationMode, UserStatus status) {
		return new AppUser(displayName, language, ageVerifiedAt, ageGatePolicyVersion, personalizationMode, status);
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public UUID getUserId() {
		return userId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getLanguage() {
		return language;
	}

	public Instant getAgeVerifiedAt() {
		return ageVerifiedAt;
	}

	public String getAgeGatePolicyVersion() {
		return ageGatePolicyVersion;
	}

	public PersonalizationMode getPersonalizationMode() {
		return personalizationMode;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void activate() {
		status = UserStatus.ACTIVE;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
