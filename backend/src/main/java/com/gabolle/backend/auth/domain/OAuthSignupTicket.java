package com.gabolle.backend.auth.domain;

import java.time.Instant;
import java.util.UUID;

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

/**
 * 소셜 인증과 회원가입(또는 기존 계정 연결) 사이를 잇는 10분짜리 1회용 티켓.
 *
 * <p>클라이언트가 받는 것은 원문 티켓(43글자 난수)이고 여기 남는 것은 SHA-256 해시다.
 * provider 의 access token 은 저장하지 않는다 — 필요한 것은 누구인지와 미리 채울 값뿐이다.
 *
 * <p>{@link AuthOneTimeToken} 을 재사용하지 않은 것은 그 표가 {@code local_credential} 에
 * NOT NULL 로 매달려 있기 때문이다. 소셜로 처음 온 사람에게는 아직 자격증명이 없다.
 */
@Entity
@Table(name = "oauth_signup_ticket")
public class OAuthSignupTicket {

	public enum Kind {
		/** 처음 보는 소셜 신원. 가입 화면으로 이어진다. */
		SIGNUP,
		/** 같은 이메일의 로컬 계정이 있다. 비밀번호를 확인하고 그 계정에 붙인다. */
		LINK
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "oauth_ticket_id")
	private UUID oauthTicketId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Kind kind;

	@Column(name = "ticket_hash", nullable = false, unique = true, length = 64)
	private String ticketHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_subject", nullable = false, length = 255)
	private String providerSubject;

	/** provider 가 이메일을 안 주면(카카오 기본 동의) {@code null}. 그 계정은 이메일 없이 만들어진다. */
	@Column(name = "provider_email", length = 254)
	private String providerEmail;

	@Column(name = "display_name", length = 50)
	private String displayName;

	@Column(length = 2)
	private String language;

	/** {@link Kind#LINK} 일 때만. 붙일 기존 계정. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "existing_user_id")
	private AppUser existingUser;

	@Column(name = "device_id", length = 255)
	private String deviceId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected OAuthSignupTicket() {
	}

	private OAuthSignupTicket(Kind kind, String ticketHash, AuthProvider provider, String providerSubject,
			String providerEmail, String displayName, String language, AppUser existingUser, String deviceId,
			Instant createdAt, Instant expiresAt) {
		if (kind == Kind.LINK && existingUser == null) {
			throw new IllegalArgumentException("LINK 티켓은 붙일 계정이 있어야 한다");
		}
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("만료 시각은 발급 시각보다 뒤여야 한다");
		}
		this.kind = kind;
		this.ticketHash = ticketHash;
		this.provider = provider;
		this.providerSubject = providerSubject;
		this.providerEmail = providerEmail;
		this.displayName = displayName;
		this.language = language;
		this.existingUser = existingUser;
		this.deviceId = deviceId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public static OAuthSignupTicket forSignup(String ticketHash, AuthProvider provider, String providerSubject,
			String providerEmail, String displayName, String language, String deviceId, Instant now, Instant expiresAt) {
		return new OAuthSignupTicket(Kind.SIGNUP, ticketHash, provider, providerSubject, providerEmail, displayName,
				language, null, deviceId, now, expiresAt);
	}

	public static OAuthSignupTicket forLink(String ticketHash, AuthProvider provider, String providerSubject,
			String providerEmail, AppUser existingUser, String deviceId, Instant now, Instant expiresAt) {
		return new OAuthSignupTicket(Kind.LINK, ticketHash, provider, providerSubject, providerEmail, null, null,
				existingUser, deviceId, now, expiresAt);
	}

	@PrePersist
	void initializeCreatedAt() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isUsableAt(Instant now) {
		return consumedAt == null && expiresAt.isAfter(now);
	}

	public void consume(Instant consumedAt) {
		this.consumedAt = consumedAt;
	}

	public UUID getOauthTicketId() { return oauthTicketId; }
	public Kind getKind() { return kind; }
	public String getTicketHash() { return ticketHash; }
	public AuthProvider getProvider() { return provider; }
	public String getProviderSubject() { return providerSubject; }
	public String getProviderEmail() { return providerEmail; }
	public String getDisplayName() { return displayName; }
	public String getLanguage() { return language; }
	public AppUser getExistingUser() { return existingUser; }
	public String getDeviceId() { return deviceId; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getConsumedAt() { return consumedAt; }
}
