package com.gabolle.backend.notification.infra;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code push_token} 표 매핑 — 기기 하나가 한 줄이다 (S15P21E201-1391).
 *
 * <p>🔴 열쇠는 사람이 아니라 <b>토큰</b>이다. 한 사람이 여러 기기를 쓰므로 계정당 여러 줄이고,
 * 같은 기기를 다른 사람이 쓰면(로그아웃 뒤 다른 계정 로그인) 토큰은 그대로인데 주인만 바뀐다.
 * 그때 옛 주인 줄을 남겨 두면 <b>앞사람 계정의 알림이 뒷사람 폰에 뜬다.</b>
 */
@Entity
@Table(name = "push_token")
public class PushTokenJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "push_token_id")
	private UUID pushTokenId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token", nullable = false, columnDefinition = "text", updatable = false)
	private String token;

	@Column(name = "platform", nullable = false, length = 16)
	private String platform;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PushTokenJpaEntity() {
		// JPA 전용
	}

	public static PushTokenJpaEntity register(UUID userId, String token, String platform, Instant now) {
		PushTokenJpaEntity entity = new PushTokenJpaEntity();
		entity.userId = userId;
		entity.token = token;
		entity.platform = platform;
		entity.createdAt = now;
		entity.updatedAt = now;
		return entity;
	}

	/**
	 * 같은 기기가 다시 올라왔다 — 주인과 갈래를 지금 값으로 덮는다.
	 *
	 * <p>주인을 안 덮으면 기기를 넘겨받은 사람의 폰에 앞사람 알림이 간다. 새로 만들지 않고 덮는 것은
	 * 토큰이 UNIQUE 라서이기도 하다.
	 */
	public void reassign(UUID userId, String platform, Instant now) {
		this.userId = userId;
		this.platform = platform;
		this.updatedAt = now;
	}

	public UUID userId() {
		return this.userId;
	}

	public String token() {
		return this.token;
	}

	public String platform() {
		return this.platform;
	}
}
