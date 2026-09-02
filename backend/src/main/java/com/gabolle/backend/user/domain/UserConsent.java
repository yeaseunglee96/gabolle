package com.gabolle.backend.user.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_consent", uniqueConstraints = @UniqueConstraint(
		name = "uk_user_consent_policy", columnNames = {"user_id", "consent_type", "policy_version"}))
public class UserConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID consentId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(name = "consent_type", nullable = false, length = 50)
	private ConsentType consentType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConsentStatus status;

	@Column(name = "policy_version", nullable = false, length = 50)
	private String policyVersion;

	@Column(name = "decided_at", nullable = false)
	private Instant decidedAt;

	protected UserConsent() {
	}

	private UserConsent(AppUser user, ConsentType consentType, ConsentStatus status, String policyVersion) {
		this.user = user;
		this.consentType = consentType;
		this.status = status;
		this.policyVersion = policyVersion;
	}

	public static UserConsent decide(AppUser user, ConsentType consentType, ConsentStatus status, String policyVersion) {
		return new UserConsent(user, consentType, status, policyVersion);
	}

	@PrePersist
	void initializeDecisionTime() {
		decidedAt = Instant.now();
	}

	public UUID getConsentId() {
		return consentId;
	}

	public ConsentType getConsentType() {
		return consentType;
	}

	public ConsentStatus getStatus() {
		return status;
	}

	public String getPolicyVersion() {
		return policyVersion;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}
}
