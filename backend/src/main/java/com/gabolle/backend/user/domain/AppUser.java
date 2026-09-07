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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

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
		this.role = UserRole.USER;
	}

	/**
	 * 가입 경로 전부(로컬·OAuth)가 이 팩토리를 거친다 — S15P21E201-686. {@code role} 을 인자로
	 * 받지 않는 이유: ADMIN 은 가입으로 얻는 값이 아니라 운영자가 DB에서 직접 올리는 값이다.
	 */
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

	/**
	 * 탈퇴한 계정으로 만든다 (S15P21E201-425).
	 *
	 * <p>🔴 행을 지우지 않고 비우는 이유가 있다. {@code itinerary_versions.created_by} 가 필수 값이면서
	 * 이 표를 가리키는데, 이 사람이 동행자의 일정을 편집한 적이 있으면 행을 지울 때 <b>남의 일정
	 * 편집 이력까지 함께 지워야</b> 한다. 그건 탈퇴한 사람의 권한 밖이다.
	 *
	 * <p>대신 로그인에 필요한 것(비밀번호·소셜 연결·세션)과 본인 데이터는 전부 지운다. 이메일이
	 * 풀려서 같은 주소로 다시 가입할 수 있고, 남는 행에는 개인을 알아볼 값이 없다.
	 *
	 * <p>{@code status} 와 {@code deletedAt} 은 원래 이 용도로 만들어져 있던 칸이다.
	 */
	/**
	 * 행동 기반 개인화를 켜고 끈다 — 2026-09-07 추가 (S15P21E201-735).
	 *
	 * <p>이 값은 가입할 때 한 번 정해지고 그 뒤로 <b>바꿀 방법이 아예 없었다.</b> 앱은
	 * 마이페이지에 토글을 뒀는데 그 선택이 기기 안에만 남았고, 서버는 가입 때의 값을
	 * 계속 믿었다 — 껐다고 생각한 사람의 행동이 계속 개인화에 들어가는 상태다.
	 *
	 * <p>🔴 동의 표({@code user_consent})와 <b>따로 두지 않는다.</b> 이 칸은 판정에 쓰는
	 * 현재 값이고 동의 표는 "언제 무엇에 동의했나" 의 기록이다. 둘 중 하나만 바뀌면
	 * 개인화는 켜져 있는데 동의는 없는(또는 그 반대) 상태가 되고, 그건 코드가 아니라
	 * 방침을 어기는 것이다. 그래서 바꾸는 자리를 하나로 둔다 —
	 * {@code ConsentUpdateService} 가 둘을 같은 트랜잭션에서 고친다.
	 */
	public void changePersonalizationMode(PersonalizationMode personalizationMode) {
		if (personalizationMode == null) {
			throw new IllegalArgumentException("개인화 모드는 비울 수 없다");
		}
		this.personalizationMode = personalizationMode;
	}

	public void anonymizeForDeletion(Instant deletedAt) {
		this.displayName = "탈퇴한 사용자";
		this.ageVerifiedAt = null;
		this.personalizationMode = PersonalizationMode.EXPLICIT_ONLY;
		this.status = UserStatus.DELETED;
		this.deletedAt = deletedAt;
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

	public UserRole getRole() {
		return role;
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
