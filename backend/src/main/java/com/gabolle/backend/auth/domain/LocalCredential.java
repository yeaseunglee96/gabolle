package com.gabolle.backend.auth.domain;

import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.UserStatus;
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
import java.time.Duration;
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

	/** 마지막 성공 이후 연속으로 틀린 횟수. 성공하면 0 으로 돌아간다. */
	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	/**
	 * 이 시각까지는 비밀번호가 맞아도 거부한다. 잠겨 있지 않으면 {@code null} 이다.
	 *
	 * <p>영구 잠금은 만들지 않는다 — 남의 이메일로 몇 번 틀리는 것만으로 그 사람 계정을 영영 못
	 * 쓰게 만들 수 있다. 잠금은 언제나 시간이 지나면 저절로 풀린다.
	 */
	@Column(name = "login_locked_until")
	private Instant loginLockedUntil;

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
		// 비밀번호를 바꿨으면 잠금도 푼다 — 재설정한 사람이 새 비밀번호를 알면서도 잠금이
		// 풀릴 때까지 기다리게 되지 않도록.
		this.failedLoginAttempts = 0;
		this.loginLockedUntil = null;
	}

	/**
	 * 이 비밀번호로 지금 로그인할 수 있나. 메일 인증을 안 끝냈으면 못 한다.
	 *
	 * <p>소셜 연결을 뗄 수 있는지도 이 판정이 가른다. 비밀번호 행이 있다는 것만으로 다른 로그인
	 * 수단이 있다고 보면, 메일 인증을 안 끝낸 사람이 마지막 소셜 연결을 떼고 다시 못 들어온다.
	 * 로그인과 이 판정은 같은 한 곳을 본다.
	 */
	public boolean canSignIn() {
		return this.emailVerifiedAt != null && this.user.getStatus() != UserStatus.PENDING_EMAIL_VERIFICATION;
	}

	/** 지금 잠겨 있는가. */
	public boolean isLoginLocked(Instant now) {
		return this.loginLockedUntil != null && this.loginLockedUntil.isAfter(now);
	}

	/**
	 * 로그인 실패를 한 번 센다. 정해진 횟수에 닿으면 잠근다.
	 *
	 * <p>잠글 때 횟수를 0 으로 되돌린다. 안 그러면 잠금이 풀린 직후 한 번만 더 틀려도 다시 잠겨
	 * 사실상 영구 잠금이 된다. 되돌려도 시도 횟수는 잠금 시간당 {@code threshold} 번으로 묶인다.
	 */
	public void recordFailedLogin(Instant now, int threshold, Duration lockoutDuration) {
		this.failedLoginAttempts++;
		if (this.failedLoginAttempts >= threshold) {
			this.failedLoginAttempts = 0;
			this.loginLockedUntil = now.plus(lockoutDuration);
		}
	}

	/** 로그인에 성공했다. 세던 것을 지운다. */
	public void recordSuccessfulLogin() {
		this.failedLoginAttempts = 0;
		this.loginLockedUntil = null;
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

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Instant getLoginLockedUntil() {
		return loginLockedUntil;
	}
}
