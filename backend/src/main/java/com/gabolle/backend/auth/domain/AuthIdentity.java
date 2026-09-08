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

	// S15P21E201-741 — Boolean(원시형 boolean 이 아니다). NULL 은 "모른다" 다.
	// 네이버는 이 정보를 아예 안 주고, 카카오도 필드가 없을 수 있다 — 그런 경우를
	// false 로 채우면 "확인했는데 아니다" 로 오독된다. 자세한 근거는 이 칼럼을
	// 만든 마이그레이션(V20260908150000)의 주석에 있다.
	@Column(name = "email_verified")
	private Boolean emailVerified;

	@Column(name = "email_valid")
	private Boolean emailValid;

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

	// S15P21E201-741 — 로그인할 때마다 공급자가 새로 준 이메일과 그 신뢰 근거로
	// 갱신하는 자리. link() 는 처음 연결할 때 한 번만 쓰고 emailVerified·emailValid 는
	// null(모름)로 시작한다 — 지금은 이 메서드를 부르는 곳이 없고, 로그인마다 다시
	// 확인하려는 곳(OAuthAccountService)에서 이 메서드로 갱신하면 된다.
	public void recordProviderEmail(String providerEmail, Boolean emailVerified, Boolean emailValid) {
		this.providerEmail = providerEmail;
		this.emailVerified = emailVerified;
		this.emailValid = emailValid;
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

	public Boolean getEmailVerified() {
		return emailVerified;
	}

	public Boolean getEmailValid() {
		return emailValid;
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
