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

	/**
	 * 표시 이름을 바꾼다 (S15P21E201-423).
	 *
	 * <p>🔴 빈 이름을 허용하지 않는다. "지운다" 와 "안 바꾼다" 를 구분해야 하는데, 지우는 쪽은
	 * 이름 없는 계정을 만들기 때문에 제품 결정 없이 열지 않는다. 안 바꾸는 것은 이 메서드를
	 * 부르지 않는 것으로 표현한다.
	 */
	public void rename(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			throw new IllegalArgumentException("표시 이름은 비울 수 없다");
		}
		this.displayName = displayName;
	}

	/** 표시 언어를 바꾼다. 값 정규화는 부르는 쪽이 끝낸 뒤 넘긴다. */
	public void changeLanguage(String language) {
		if (language == null || language.isBlank()) {
			throw new IllegalArgumentException("언어는 비울 수 없다");
		}
		this.language = language;
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
