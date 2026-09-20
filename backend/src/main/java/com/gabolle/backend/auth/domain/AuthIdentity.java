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

	// Boolean 이어야 한다 — NULL 은 "모른다" 다. 네이버는 이 정보를 안 주고 카카오도 필드가
	// 없을 수 있는데, 그런 경우를 false 로 채우면 "확인했는데 아니다" 로 오독된다.
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

	// 로그인할 때마다 공급자가 새로 준 이메일과 그 신뢰 근거로 갱신하는 자리.
	// link() 는 처음 연결할 때 한 번만 쓰고 emailVerified·emailValid 는 null(모름)로 시작한다.
	public void recordProviderEmail(String providerEmail, Boolean emailVerified, Boolean emailValid) {
		this.providerEmail = providerEmail;
		this.emailVerified = emailVerified;
		this.emailValid = emailValid;
	}

	/**
	 * 이 연결을 뗀다. 행을 지우지 않고 뗀 시각을 적는다.
	 *
	 * <p>{@code (provider, provider_subject)} 가 유일해야 하고, 뗀 소셜로 로그인하려는 시도를
	 * 그 자리에서 알아봐야 하기 때문이다. 행을 지우면 그 시도가 처음 보는 신원이 되어 가입이나
	 * 자동 연결 흐름으로 들어가고, 방금 뗀 연결이 조용히 되살아난다.
	 */
	public void unlink(Instant unlinkedAt) {
		this.unlinkedAt = unlinkedAt;
	}

	/**
	 * 끊겼던 연결을 다시 잇는다. 주인이 바뀔 수 있다 — 끊긴 연결은 누구의 것도 아니다.
	 *
	 * <p>주인을 안 바꾸면 그 소셜 계정은 처음 연결했던 계정 말고는 아무도 쓸 수 없다.
	 * 다시 잇는 쪽은 언제나 그 소셜 계정으로 방금 인증을 마친 사람이다.
	 */
	public void relink(AppUser user, Instant linkedAt) {
		this.user = user;
		this.linkedAt = linkedAt;
		this.unlinkedAt = null;
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
