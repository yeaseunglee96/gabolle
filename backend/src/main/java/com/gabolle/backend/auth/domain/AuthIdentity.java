package com.gabolle.backend.auth.domain;

import com.gabolle.backend.user.domain.AppUser;
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
@Table(name = "auth_identity", uniqueConstraints = @UniqueConstraint(
		name = "uk_auth_identity_provider_subject", columnNames = {"provider", "provider_subject"}))
public class AuthIdentity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID identityId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_subject", nullable = false, length = 255)
	private String providerSubject;

	@Column(name = "provider_email", length = 254)
	private String providerEmail;

	@Column(name = "linked_at", nullable = false)
	private Instant linkedAt;

	@Column(name = "unlinked_at")
	private Instant unlinkedAt;

	protected AuthIdentity() {
	}

	private AuthIdentity(AppUser user, AuthProvider provider, String providerSubject, String providerEmail) {
		this.user = user;
		this.provider = provider;
		this.providerSubject = providerSubject;
		this.providerEmail = providerEmail;
	}

	public static AuthIdentity link(AppUser user, AuthProvider provider, String providerSubject, String providerEmail) {
		return new AuthIdentity(user, provider, providerSubject, providerEmail);
	}

	@PrePersist
	void initializeLinkedAt() {
		linkedAt = Instant.now();
	}

	public UUID getIdentityId() {
		return identityId;
	}

	public AppUser getUser() {
		return user;
	}

	public AuthProvider getProvider() {
		return provider;
	}

	public String getProviderSubject() {
		return providerSubject;
	}

	public String getProviderEmail() {
		return providerEmail;
	}

	public Instant getLinkedAt() {
		return linkedAt;
	}

	public Instant getUnlinkedAt() {
		return unlinkedAt;
	}

	public boolean isActive() {
		return unlinkedAt == null;
	}
}
